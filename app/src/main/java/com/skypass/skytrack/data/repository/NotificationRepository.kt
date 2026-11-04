package com.skypass.skytrack.data.repository

import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.remote.ApiErrorExtractor
import com.skypass.skytrack.data.remote.DeviceTokenRequest
import com.skypass.skytrack.data.remote.NotificationDto
import com.skypass.skytrack.data.remote.NotificationsRequest
import com.skypass.skytrack.data.remote.SkyTrackApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NotificationPage(
    val items: List<NotificationDto>,
    val unreadCount: Int,
    val hasMore: Boolean
)

/** The employee's notification inbox and push device registration. No local cache. */
@Singleton
class NotificationRepository @Inject constructor(
    private val api: SkyTrackApi
) {
    private val _unreadCount = MutableStateFlow(0)

    /** Unread count for the bell badge on Home */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    suspend fun list(token: String, before: String? = null): Result<NotificationPage> = call {
        val response = api.notificationsList(
            BuildConfig.SKYTRACK_API_URL,
            "Bearer $token",
            NotificationsRequest(action = "notifications_list", limit = PAGE_SIZE, before = before)
        )
        if (!response.success) throw failure(response.error, response.message)
        val unread = response.unreadCount ?: 0
        _unreadCount.value = unread
        NotificationPage(response.notifications.orEmpty(), unread, response.hasMore == true)
    }

    suspend fun refreshUnreadCount(token: String) {
        list(token)
    }

    suspend fun markAllRead(token: String): Result<Unit> = call {
        val response = api.notificationsMarkRead(
            BuildConfig.SKYTRACK_API_URL,
            "Bearer $token",
            NotificationsRequest(action = "notifications_mark_read", all = true)
        )
        if (!response.success) throw failure(response.error, response.message)
        _unreadCount.value = 0
    }

    /** A push arrived while the app is running: show it on the bell until the inbox is refreshed */
    fun onPushReceived() {
        _unreadCount.update { it + 1 }
    }

    fun clearUnreadCount() {
        _unreadCount.value = 0
    }

    suspend fun registerDevice(token: String, deviceToken: String): Result<Unit> = call {
        val response = api.deviceToken(
            BuildConfig.SKYTRACK_API_URL,
            "Bearer $token",
            DeviceTokenRequest(action = "device_register", token = deviceToken, appVersion = BuildConfig.VERSION_NAME)
        )
        if (!response.success) throw failure(response.error, response.message)
    }

    suspend fun unregisterDevice(token: String, deviceToken: String): Result<Unit> = call {
        val response = api.deviceToken(
            BuildConfig.SKYTRACK_API_URL,
            "Bearer $token",
            DeviceTokenRequest(action = "device_unregister", token = deviceToken)
        )
        if (!response.success) throw failure(response.error, response.message)
    }

    private fun failure(code: String?, message: String?) = AttendanceApiException(
        code = code,
        message = ApiErrorExtractor.cleanErrorString(message ?: "Unable to load notifications.", "Unable to load notifications. Please try again.")
    )

    private suspend fun <T> call(block: suspend () -> T): Result<T> = runCatching {
        try {
            block()
        } catch (httpException: retrofit2.HttpException) {
            val (code, message) = ApiErrorExtractor.extractCodeAndMessage(httpException, "Unable to load notifications. Please try again.")
            throw AttendanceApiException(code = code ?: "HTTP_${httpException.code()}", message = message)
        } catch (_: IOException) {
            throw AttendanceApiException(
                code = "NETWORK_REQUIRED",
                message = "Notifications need an internet connection. Please reconnect and try again."
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
