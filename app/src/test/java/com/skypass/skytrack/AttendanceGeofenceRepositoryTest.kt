package com.skypass.skytrack

import com.google.gson.Gson
import com.skypass.skytrack.data.local.ConnectivityObserver
import com.skypass.skytrack.data.local.db.dao.AttendanceDao
import com.skypass.skytrack.data.local.db.dao.PendingActionDao
import com.skypass.skytrack.data.local.db.entity.AttendanceCacheEntity
import com.skypass.skytrack.data.local.db.entity.PendingActionEntity
import com.skypass.skytrack.data.remote.*
import com.skypass.skytrack.data.repository.AttendanceApiException
import com.skypass.skytrack.data.repository.AttendanceRepository
import com.skypass.skytrack.data.repository.GeofenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AttendanceGeofenceRepositoryTest {

    private class FakeConnectivity(var online: Boolean) : ConnectivityObserver {
        override fun observe(): Flow<ConnectivityObserver.Status> = flowOf(ConnectivityObserver.Status.AVAILABLE)
        override fun isConnected(): Boolean = online
    }

    private class FakeAttendanceDao : AttendanceDao {
        val rows = mutableMapOf<String, AttendanceCacheEntity>()
        override suspend fun getAttendance(date: String) = rows[date]
        override suspend fun getLatestAttendance() = rows.values.maxByOrNull { it.date }
        override fun observeAttendance(date: String): Flow<AttendanceCacheEntity?> = flowOf(rows[date])
        override suspend fun insertOrUpdate(entity: AttendanceCacheEntity) { rows[entity.date] = entity }
        override suspend fun clear() = rows.clear()
    }

    private class FakePendingActionDao : PendingActionDao {
        val actions = mutableListOf<PendingActionEntity>()
        override suspend fun insert(action: PendingActionEntity): Long { actions += action; return actions.size.toLong() }
        override suspend fun getPendingActions() = actions.toList()
        override fun observePendingCount(): Flow<Int> = flowOf(actions.size)
        override suspend fun getPendingCount() = actions.size
        override suspend fun delete(id: Long) { actions.removeAll { it.id == id } }
        override suspend fun update(action: PendingActionEntity) {}
        override suspend fun clearAll() = actions.clear()
    }

    /** Records attendance requests; [respond] decides the outcome for each call. */
    private class FakeApi(var respond: (AttendanceRequest) -> AttendanceResponse) : SkyTrackApi {
        val requests = mutableListOf<AttendanceRequest>()
        var geofenceResponse: () -> GeofenceConfigResponse = { error("not configured") }

        private fun record(request: AttendanceRequest): AttendanceResponse {
            requests += request
            return respond(request)
        }

        override suspend fun startWork(endpoint: String, authorization: String, request: AttendanceRequest) = record(request)
        override suspend fun endWork(endpoint: String, authorization: String, request: AttendanceRequest) = record(request)
        override suspend fun startBreak(endpoint: String, authorization: String, request: AttendanceRequest) = record(request)
        override suspend fun resumeWork(endpoint: String, authorization: String, request: AttendanceRequest) = record(request)
        override suspend fun attendanceToday(endpoint: String, authorization: String, request: AttendanceRequest) = record(request)
        override suspend fun geofenceConfig(endpoint: String, authorization: String, request: AttendanceRequest) = geofenceResponse()
        override suspend fun salarySummary(endpoint: String, authorization: String, request: SalarySummaryRequest): SalarySummaryResponse = TODO()
        override suspend fun payslipList(endpoint: String, authorization: String, request: PayslipRequest): PayslipListResponse = TODO()
        override suspend fun payslipGet(endpoint: String, authorization: String, request: PayslipRequest): PayslipResponse = TODO()

        override suspend fun login(endpoint: String, request: LoginRequest): LoginResponse = TODO()
        override suspend fun submitLeave(endpoint: String, authorization: String, request: LeaveSubmitRequest): LeaveResponse = TODO()
        override suspend fun leaveHistory(endpoint: String, authorization: String, request: LeaveHistoryRequest): LeaveResponse = TODO()
        override suspend fun leaveBalance(endpoint: String, authorization: String, request: LeaveBalanceRequest): LeaveBalanceResponse = TODO()
        override suspend fun cancelLeave(endpoint: String, authorization: String, request: CancelLeaveRequest): CancelLeaveResponse = TODO()
        override suspend fun attendanceMonthly(endpoint: String, authorization: String, request: MonthlyAttendanceRequest): MonthlyAttendanceResponse = TODO()
        override suspend fun publicHolidays(endpoint: String, authorization: String, request: AttendanceRequest): HolidayResponse = TODO()
        override suspend fun updateProfilePhoto(endpoint: String, authorization: String, request: UpdateProfilePhotoRequest): UpdateProfilePhotoResponse = TODO()
        override suspend fun getProfile(endpoint: String, authorization: String, request: AttendanceRequest): ProfileResponse = TODO()
    }

    private val location = LocationPayloadDto(
        latitude = 9.9312,
        longitude = 76.2673,
        accuracy = 12f,
        capturedAt = "2026-10-25T04:00:00Z",
        ageMs = 1500L,
        isMock = false
    )

    private val gson = Gson()

    private fun httpError(code: Int, body: String) =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    private fun repository(api: FakeApi, online: Boolean, pending: FakePendingActionDao = FakePendingActionDao()) =
        AttendanceRepository(api, FakeAttendanceDao(), pending, FakeConnectivity(online), gson)

    @Test
    fun checkInSendsLocationPayload() = runBlocking {
        val api = FakeApi { AttendanceResponse(success = true, attendance = AttendanceDto(date = "2026-10-25", login = "09:00 AM")) }
        val result = repository(api, online = true).startWork("token", location)

        assertTrue(result.isSuccess)
        assertEquals("start_work", api.requests.single().action)
        assertEquals(location, api.requests.single().location)
        val json = gson.toJson(api.requests.single())
        assertTrue(json.contains("\"latitude\":9.9312"))
        assertTrue(json.contains("\"platform\":\"android\""))
    }

    @Test
    fun offlineCheckInAndCheckOutAreNotQueued() = runBlocking {
        val pending = FakePendingActionDao()
        val api = FakeApi { error("must not be called") }
        val repo = repository(api, online = false, pending = pending)

        val checkIn = repo.startWork("token", location).exceptionOrNull() as AttendanceApiException
        val checkOut = repo.endWork("token", location).exceptionOrNull() as AttendanceApiException

        assertEquals("NETWORK_REQUIRED", checkIn.code)
        assertEquals("NETWORK_REQUIRED", checkOut.code)
        assertTrue(checkIn.message.contains("internet connection"))
        assertTrue(pending.actions.isEmpty())
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun networkFailureDuringCheckInIsNotQueued() = runBlocking {
        val pending = FakePendingActionDao()
        val api = FakeApi { throw IOException("connection reset") }
        val error = repository(api, online = true, pending = pending).startWork("token", location).exceptionOrNull()

        assertEquals("NETWORK_REQUIRED", (error as AttendanceApiException).code)
        assertTrue(pending.actions.isEmpty())
    }

    @Test
    fun offlineBreakIsStillQueuedWithItsLocation() = runBlocking {
        val pending = FakePendingActionDao()
        val result = repository(FakeApi { error("offline") }, online = false, pending = pending).startBreak("token", location)

        assertTrue(result.isSuccess)
        val queued = pending.actions.single()
        assertEquals("START_BREAK", queued.actionType)
        assertEquals(location, gson.fromJson(queued.payloadJson, LocationPayloadDto::class.java))
    }

    @Test
    fun breakWithoutLocationStillProceeds() = runBlocking {
        val api = FakeApi { AttendanceResponse(success = true, attendance = AttendanceDto(breakStartedAt = "2026-10-25T05:00:00Z")) }
        val result = repository(api, online = true).startBreak("token", null)

        assertTrue(result.isSuccess)
        assertNull(api.requests.single().location)
        assertTrue(!gson.toJson(api.requests.single()).contains("location"))
    }

    @Test
    fun outsideGeofenceRejectionKeepsServerCodeAndMessage() = runBlocking {
        val body = """{"success":false,"error":"outside_geofence","message":"You are about 150 m from the office, outside the permitted 100 m radius. Move closer to the office and try again.","geofence":{"distanceMeters":150,"radiusMeters":100}}"""
        val api = FakeApi { throw httpError(422, body) }
        val error = repository(api, online = true).startWork("token", location).exceptionOrNull() as AttendanceApiException

        assertEquals("outside_geofence", error.code)
        assertTrue(error.message.contains("150 m"))
    }

    @Test
    fun missingConfigurationRejectionIsSurfaced() = runBlocking {
        val body = """{"success":false,"error":"geofence_not_configured","message":"The office location has not been configured, so you cannot check out right now. Please contact your administrator."}"""
        val api = FakeApi { throw httpError(409, body) }
        val error = repository(api, online = true).endWork("token", location).exceptionOrNull() as AttendanceApiException

        assertEquals("geofence_not_configured", error.code)
        assertTrue(error.message.contains("not been configured"))
    }

    @Test
    fun geofenceConfigFetchFailureIsReported() = runBlocking {
        val api = FakeApi { error("unused") }
        api.geofenceResponse = { throw IOException("timeout") }
        val repo = GeofenceRepository(api, FakeConnectivity(true))
        val failure = repo.fetchConfig("token").exceptionOrNull()
        assertNotNull(failure)
        assertEquals("GEOFENCE_CONFIG_UNAVAILABLE", (failure as AttendanceApiException).code)

        api.geofenceResponse = {
            GeofenceConfigResponse(
                success = true,
                geofence = GeofenceConfigDto(configured = true, active = true, latitude = 1.0, longitude = 2.0, radiusMeters = 150)
            )
        }
        assertEquals(150, repo.fetchConfig("token").getOrThrow().radiusMeters)
    }
}
