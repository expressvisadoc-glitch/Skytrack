package com.skypass.skytrack

import com.skypass.skytrack.data.remote.*
import com.skypass.skytrack.data.repository.AttendanceApiException
import com.skypass.skytrack.data.repository.SalaryRepository
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class SalaryRepositoryTest {

    private class FakeApi(var respond: (SalarySummaryRequest) -> SalarySummaryResponse) : SkyTrackApi {
        val requests = mutableListOf<SalarySummaryRequest>()
        override suspend fun salarySummary(endpoint: String, authorization: String, request: SalarySummaryRequest): SalarySummaryResponse {
            requests += request
            return respond(request)
        }

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

    private val sampleJson = """
        {"success":true,"salary":{"status":"ok","month":"2026-11","monthlyGross":30000,"workingDaysInMonth":24,
         "perDayRate":1250,"earnedGross":30000,"absenceDays":1,"absenceAmount":1250,"lopLeaveDays":1,"lopLeaveAmount":1250,
         "lateMarks":3,"lateMarksPerUnit":2,"lateLopDaysPerUnit":0.5,"lateLopDays":0.5,"lateAmount":625,
         "totalDeductions":3125,"netPay":26875,"flags":[],
         "days":[{"date":"2026-11-02","type":"absent","days":1,"amount":1250}]}}
    """.trimIndent()

    @Test
    fun parsesTheServerShapeAndRequestsTheCurrentMonth() = runBlocking {
        val api = FakeApi { Gson().fromJson(sampleJson, SalarySummaryResponse::class.java) }
        val salary = SalaryRepository(api).currentMonth("token").getOrThrow()

        assertEquals(26875.0, salary.netPay!!, 0.0)
        assertEquals(3, salary.lateMarks)
        assertEquals("absent", salary.days!!.single().type)
        assertEquals("salary_summary", api.requests.single().action)
        assertEquals(null, api.requests.single().month)
        assertTrue(!Gson().toJson(api.requests.single()).contains("month"))
    }

    @Test
    fun noSalaryIsASuccessfulResult() = runBlocking {
        val api = FakeApi { SalarySummaryResponse(success = true, salary = SalaryDto(status = "no_salary")) }
        assertEquals("no_salary", SalaryRepository(api).currentMonth("token").getOrThrow().status)
    }

    @Test
    fun networkFailureGivesAClearMessage() = runBlocking {
        val api = FakeApi { throw IOException("offline") }
        val error = SalaryRepository(api).currentMonth("token").exceptionOrNull() as AttendanceApiException
        assertEquals("NETWORK_REQUIRED", error.code)
        assertTrue(error.message.contains("internet"))
    }

    @Test
    fun serverErrorKeepsCodeAndMessage() = runBlocking {
        val body = """{"success":false,"error":"salary_unavailable","message":"Unable to calculate your salary right now. Please try again."}"""
        val api = FakeApi { throw HttpException(Response.error<Any>(500, body.toResponseBody("application/json".toMediaType()))) }
        val error = SalaryRepository(api).currentMonth("token").exceptionOrNull() as AttendanceApiException
        assertEquals("salary_unavailable", error.code)
        assertTrue(error.message.startsWith("Unable to calculate"))
    }
}
