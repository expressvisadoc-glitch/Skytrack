package com.skypass.skytrack.data.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.skypass.skytrack.data.local.SessionDataStore
import com.skypass.skytrack.data.repository.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class SkyTrackMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushManager: PushManager

    @Inject
    lateinit var notificationPreferences: NotificationPreferences

    @Inject
    lateinit var sessionDataStore: SessionDataStore

    @Inject
    lateinit var notificationRepository: NotificationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { pushManager.onNewDeviceToken(token) }
    }

    // Runs on a background thread; the reads below are local and quick
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val id = data["id"] ?: return
        val title = data["title"] ?: return
        val body = data["body"].orEmpty()
        runBlocking {
            // Only for the employee signed in on this phone
            val session = sessionDataStore.session.first() ?: return@runBlocking
            val recipient = data["employee"]
            if (!recipient.isNullOrBlank() && !recipient.equals(session.employeeId, ignoreCase = true)) {
                return@runBlocking
            }
            notificationRepository.onPushReceived()
            if (notificationPreferences.isEnabled()) {
                PushNotifier.show(applicationContext, id, title, body, data["screen"] ?: "home")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
