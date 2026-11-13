package com.skypass.skytrack

import com.skypass.skytrack.data.remote.*
import com.skypass.skytrack.data.repository.AttendanceApiException
import com.skypass.skytrack.data.repository.SalaryRepository
import com.skypass.skytrack.ui.home.formatMinutesLate
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
        override suspend fun changeOwnPassword(endpoint: String, authorization: String, request: com.skypass.skytrack.data.remote.ChangeOwnPasswordRequest): BasicResponse = TODO()
        override suspend fun notificationsList(endpoint: String, authorization: String, request: NotificationsRequest): NotificationsListResponse = TODO()
        override suspend fun notificationsMarkRead(endpoint: String, authorization: String, request: NotificationsRequest): BasicResponse = TODO()
        override suspend fun deviceToken(endpoint: String, authorization: String, request: DeviceTokenRequest): BasicResponse = TODO()
        override suspend fun payslipList(endpoint: String, authorization: String, request: PayslipRequest): PayslipListResponse = TODO()
        override suspend fun payslipGet(endpoint: String, authorization: String, request: PayslipRequest): PayslipResponse = TODO()
    }

    private val sampleJson = """
        {"success":true,"salary":{"status":"ok","month":"2026-11","monthlyGross":30000,"workingDaysInMonth":24,
         "perDayRate":1250,"earnedGross":30000,"absenceDays":1,"absenceAmount":1250,"lopLeaveDays":1,"lopLeaveAmount":1250,
         "lateMarks":3,"lateMarksPerUnit":2,"lateLopDaysPerUnit":0.5,"lateLopDays":0.5,"lateAmount":625,
         "totalDeductions":3125,"netPay":26875,"flags":[],
         "days":[{"date":"2026-11-02","type":"absent","days":1,"amount":1250}]}}
    """.trimIndent()

    /** Option 2: the server charges every late minute and the app shows the minutes and the rate. */
    private val perMinuteJson = """
        {"success":true,"salary":{"status":"ok","month":"2026-10","monthlyGross":20000,"workingDaysInMonth":24,
         "perDayRate":833.33,"earnedGross":20000,"absenceDays":1,"absenceAmount":833.33,"lopLeaveDays":1,"lopLeaveAmount":833.33,
         "latePenaltyMode":"per_minute","lateMarks":5,"lateMarksPerUnit":2,"lateLopDaysPerUnit":0.5,
         "lateMinutes":152,"lateDaysCharged":5,"latePerMinuteBasis":"pro_rata","latePerMinuteAmount":0,
         "latePerMinuteRate":1.7361,"lateLopDays":0.32,"lateAmount":263.89,
         "totalDeductions":1930.56,"netPay":18069,"flags":["joining_date_missing"],
         "days":[{"date":"2026-10-27","type":"late","days":0.125,"amount":104.17,"minutes":60}]}}
    """.trimIndent()

    @Test
    fun parsesThePerMinuteLateOption() = runBlocking {
        val api = FakeApi { Gson().fromJson(perMinuteJson, SalarySummaryResponse::class.java) }
        val salary = SalaryRepository(api).currentMonth("token").getOrThrow()

        assertEquals("per_minute", salary.latePenaltyMode)
        assertEquals(152, salary.lateMinutes)
        assertEquals(5, salary.lateDaysCharged)
        assertEquals("pro_rata", salary.latePerMinuteBasis)
        assertEquals(1.7361, salary.latePerMinuteRate!!, 0.0001)
        assertEquals(263.89, salary.lateAmount!!, 0.01)
        assertEquals(18069.0, salary.netPay!!, 0.0)

        val lateDay = salary.days!!.single()
        assertEquals("late", lateDay.type)
        assertEquals(60, lateDay.minutes)
        assertEquals(104.17, lateDay.amount!!, 0.01)
    }

    @Test
    fun keepsTheLateMarksOptionWhenTheServerSendsIt() = runBlocking {
        val api = FakeApi { Gson().fromJson(sampleJson, SalarySummaryResponse::class.java) }
        val salary = SalaryRepository(api).currentMonth("token").getOrThrow()

        // The older "marks" option carries no minutes; the card must not show a per-minute line
        assertEquals(null, salary.latePenaltyMode)
        assertEquals(null, salary.lateMinutes)
        assertEquals(0.5, salary.lateLopDays!!, 0.0)
    }

    @Test
    fun formatsLateMinutesForTheCard() {
        assertEquals("0 min", formatMinutesLate(null))
        assertEquals("45 min", formatMinutesLate(45))
        assertEquals("1 hr", formatMinutesLate(60))
        assertEquals("2 hr 32 min", formatMinutesLate(152))
    }

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
