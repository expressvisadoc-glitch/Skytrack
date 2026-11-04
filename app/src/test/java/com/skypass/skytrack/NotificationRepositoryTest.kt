package com.skypass.skytrack

import com.google.gson.Gson
import com.skypass.skytrack.data.remote.*
import com.skypass.skytrack.data.repository.AttendanceApiException
import com.skypass.skytrack.data.repository.NotificationRepository
import com.skypass.skytrack.ui.notifications.formatNotificationTime
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {

    private class FakeApi(
        var list: (NotificationsRequest) -> NotificationsListResponse = { error("unused") },
        var markRead: (NotificationsRequest) -> BasicResponse = { BasicResponse(success = true) },
        var device: (DeviceTokenRequest) -> BasicResponse = { BasicResponse(success = true) }
    ) : SkyTrackApi {
        val listRequests = mutableListOf<NotificationsRequest>()
        val markRequests = mutableListOf<NotificationsRequest>()
        val deviceRequests = mutableListOf<DeviceTokenRequest>()
        override suspend fun notificationsList(endpoint: String, authorization: String, request: NotificationsRequest) = list(request).also { listRequests += request }
        override suspend fun notificationsMarkRead(endpoint: String, authorization: String, request: NotificationsRequest) = markRead(request).also { markRequests += request }
        override suspend fun deviceToken(endpoint: String, authorization: String, request: DeviceTokenRequest) = device(request).also { deviceRequests += request }

        override suspend fun payslipList(endpoint: String, authorization: String, request: PayslipRequest): PayslipListResponse = TODO()
        override suspend fun payslipGet(endpoint: String, authorization: String, request: PayslipRequest): PayslipResponse = TODO()
        override suspend fun salarySummary(endpoint: String, authorization: String, request: SalarySummaryRequest): SalarySummaryResponse = TODO()
        override suspend fun login(endpoint: String, request: LoginRequest): LoginResponse = TODO()
        override suspend fun startWork(endpoint: String, authorization: String, request: AttendanceRequest): AttendanceResponse = TODO()
        override suspend fun endWork(endpoint: String, authorization: String, request: AttendanceRequest): AttendanceResponse = TODO()
        override suspend fun startBreak(endpoint: String, authorization: String, request: AttendanceRequest): AttendanceResponse = TODO()
        override suspend fun resumeWork(endpoint: String, authorization: String, request: AttendanceRequest): AttendanceResponse = TODO()
        override suspend fun attendanceToday(endpoint: String, authorization: String, request: AttendanceRequest): AttendanceResponse = TODO()
        override suspend fun geofenceConfig(endpoint: String, authorization: String, request: AttendanceRequest): GeofenceConfigResponse = TODO()
        override suspend fun submitLeave(endpoint: String, authorization: String, request: LeaveSubmitRequest): LeaveResponse = TODO()
        override suspend fun leaveHistory(endpoint: String, authorization: String, request: LeaveHistoryRequest): LeaveResponse = TODO()
        override suspend fun leaveBalance(endpoint: String, authorization: String, request: LeaveBalanceRequest): LeaveBalanceResponse = TODO()
        override suspend fun cancelLeave(endpoint: String, authorization: String, request: CancelLeaveRequest): CancelLeaveResponse = TODO()
        override suspend fun attendanceMonthly(endpoint: String, authorization: String, request: MonthlyAttendanceRequest): MonthlyAttendanceResponse = TODO()
        override suspend fun publicHolidays(endpoint: String, authorization: String, request: AttendanceRequest): HolidayResponse = TODO()
        override suspend fun updateProfilePhoto(endpoint: String, authorization: String, request: UpdateProfilePhotoRequest): UpdateProfilePhotoResponse = TODO()
        override suspend fun getProfile(endpoint: String, authorization: String, request: AttendanceRequest): ProfileResponse = TODO()
    }

    // Shape returned by the notifications_list action of skytrack-api
    private val listJson = """
        {"success":true,"unreadCount":2,"hasMore":true,"notifications":[
         {"id":"5b8b2c1e-0000-4000-8000-000000000001","type":"leave_approved","title":"Leave approved",
          "body":"Your leave request for 18 Nov was approved.","data":{"screen":"leave"},
          "createdAt":"2026-10-28T07:38:00.123456+00:00","readAt":null},
         {"id":"5b8b2c1e-0000-4000-8000-000000000002","type":"shift_reminder","title":"Shift starting soon",
          "body":"Your shift starts at 10:00 AM. Remember to check in.","data":{},
          "createdAt":"2026-10-28T04:26:00+00:00","readAt":"2026-10-28T05:00:00+00:00"}]}
    """.trimIndent()

    @Test
    fun parsesTheListAndUpdatesTheBellCount() = runBlocking {
        val api = FakeApi(list = { Gson().fromJson(listJson, NotificationsListResponse::class.java) })
        val repository = NotificationRepository(api)
        val page = repository.list("token").getOrThrow()
        assertEquals(2, page.items.size)
        assertTrue(page.hasMore)
        assertEquals("leave", page.items[0].data?.screen)
        assertEquals(null, page.items[1].data?.screen)
        assertEquals(2, repository.unreadCount.value)
        assertEquals("notifications_list", api.listRequests.single().action)
        assertEquals(null, api.listRequests.single().before)
    }

    @Test
    fun olderPagesSendTheCursor() = runBlocking {
        val api = FakeApi(list = { NotificationsListResponse(success = true, notifications = emptyList(), unreadCount = 0, hasMore = false) })
        NotificationRepository(api).list("token", before = "2026-10-28T04:26:00+00:00").getOrThrow()
        assertTrue(Gson().toJson(api.listRequests.single()).contains("\"before\":\"2026-10-28T04:26:00+00:00\""))
    }

    @Test
    fun markAllReadClearsTheBell() = runBlocking {
        val api = FakeApi(list = { Gson().fromJson(listJson, NotificationsListResponse::class.java) })
        val repository = NotificationRepository(api)
        repository.list("token").getOrThrow()
        repository.markAllRead("token").getOrThrow()
        assertEquals(0, repository.unreadCount.value)
        assertEquals(true, api.markRequests.single().all)
        assertEquals("notifications_mark_read", api.markRequests.single().action)
    }

    @Test
    fun pushesRaiseTheBellCountUntilRefreshed() {
        val repository = NotificationRepository(FakeApi())
        repository.onPushReceived()
        repository.onPushReceived()
        assertEquals(2, repository.unreadCount.value)
        repository.clearUnreadCount()
        assertEquals(0, repository.unreadCount.value)
    }

    @Test
    fun deviceRegistrationSendsTokenPlatformAndVersion() = runBlocking {
        val api = FakeApi()
        val repository = NotificationRepository(api)
        repository.registerDevice("token", "fcm-token-123").getOrThrow()
        repository.unregisterDevice("token", "fcm-token-123").getOrThrow()
        val register = api.deviceRequests[0]
        assertEquals("device_register", register.action)
        assertEquals("fcm-token-123", register.token)
        assertEquals("android", register.platform)
        assertEquals(BuildConfig.VERSION_NAME, register.appVersion)
        assertEquals("device_unregister", api.deviceRequests[1].action)
        assertFalse(Gson().toJson(api.deviceRequests[1]).contains("appVersion"))
    }

    @Test
    fun offlineGivesAFriendlyError() = runBlocking {
        val api = FakeApi(list = { throw IOException("offline") })
        val error = NotificationRepository(api).list("token").exceptionOrNull() as AttendanceApiException
        assertEquals("NETWORK_REQUIRED", error.code)
    }

    @Test
    fun timeLabels() {
        val now = Instant.parse("2026-10-28T10:00:00Z")
        val ist = ZoneId.of("Asia/Kolkata")
        assertEquals("Just now", formatNotificationTime("2026-10-28T09:59:40Z", now, ist))
        assertEquals("12 min ago", formatNotificationTime("2026-10-28T09:48:00+00:00", now, ist))
        assertEquals("3 h ago", formatNotificationTime("2026-10-28T06:30:00.123456+00:00", now, ist))
        assertEquals("26 Oct, 4:00 PM", formatNotificationTime("2026-10-26T10:30:00+00:00", now, ist))
        assertEquals("", formatNotificationTime("not a date", now, ist))
        assertEquals("", formatNotificationTime(null, now, ist))
    }
}
