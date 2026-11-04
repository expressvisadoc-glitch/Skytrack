package com.skypass.skytrack.data.notifications

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.local.SessionDataStore
import com.skypass.skytrack.data.repository.NotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Registers this phone for push notifications with the backend. Firebase is configured from
 * BuildConfig (gradle properties); without them push is off and the in-app inbox still works.
 */
@Singleton
class PushManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationRepository: NotificationRepository,
    private val sessionDataStore: SessionDataStore
) {
    private val isConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun registerCurrentDevice(sessionToken: String) {
        if (!isConfigured) return
        val deviceToken = currentDeviceToken() ?: return
        notificationRepository.registerDevice(sessionToken, deviceToken)
            .onFailure { Log.w(TAG, "Device registration failed: ${it.message}") }
    }

    /** Called before sign-out so the previous employee's notifications stop reaching this phone */
    suspend fun unregisterCurrentDevice(sessionToken: String) {
        if (!isConfigured) return
        withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
            val deviceToken = currentDeviceToken() ?: return@withTimeoutOrNull
            notificationRepository.unregisterDevice(sessionToken, deviceToken)
        }
        notificationRepository.clearUnreadCount()
    }

    suspend fun onNewDeviceToken(deviceToken: String) {
        val session = sessionDataStore.session.first() ?: return
        notificationRepository.registerDevice(session.token, deviceToken)
            .onFailure { Log.w(TAG, "Device registration failed: ${it.message}") }
    }

    private suspend fun currentDeviceToken(): String? =
        runCatching { FirebaseMessaging.getInstance().token.awaitResult() }
            .onFailure { Log.w(TAG, "Unable to read the push token: ${it.message}") }
            .getOrNull()

    companion object {
        private const val TAG = "PushManager"
        private const val UNREGISTER_TIMEOUT_MS = 4000L

        /** Initialises Firebase when the app was built with the Firebase settings. */
        fun initialize(context: Context) {
            if (BuildConfig.FIREBASE_APP_ID.isBlank() || BuildConfig.FIREBASE_API_KEY.isBlank() ||
                BuildConfig.FIREBASE_PROJECT_ID.isBlank() || BuildConfig.FIREBASE_SENDER_ID.isBlank()
            ) {
                return
            }
            if (FirebaseApp.getApps(context).isNotEmpty()) return
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            runCatching { FirebaseApp.initializeApp(context, options) }
                .onFailure { Log.w(TAG, "Firebase initialisation failed: ${it.message}") }
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val exception = task.exception
        if (exception != null) {
            continuation.resumeWith(Result.failure(exception))
        } else {
            continuation.resume(task.result)
        }
    }
}
