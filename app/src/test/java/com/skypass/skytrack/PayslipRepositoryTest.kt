package com.skypass.skytrack

import com.google.gson.Gson
import com.skypass.skytrack.data.remote.*
import com.skypass.skytrack.data.repository.AttendanceApiException
import com.skypass.skytrack.data.repository.PayslipRepository
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class PayslipRepositoryTest {

    private class FakeApi(
        var list: () -> PayslipListResponse = { error("unused") },
        var get: (PayslipRequest) -> PayslipResponse = { error("unused") }
    ) : SkyTrackApi {
        val requests = mutableListOf<PayslipRequest>()
        override suspend fun payslipList(endpoint: String, authorization: String, request: PayslipRequest) = list().also { requests += request }
        override suspend fun payslipGet(endpoint: String, authorization: String, request: PayslipRequest) = get(request).also { requests += request }

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

    private val payslipJson = """
        {"success":true,"payslip":{"payslipNumber":"PS-202609-TST001-V2","month":"2026-09","version":2,
         "generatedAt":"2026-10-27T07:10:44.705596+00:00","companyName":"Skypass Visa Services",
         "employee":{"employeeId":"TST-001","name":"Test Employee One","designation":"Staging Tester","department":null},
         "salary":{"status":"ok","monthlyGross":26000,"workingDaysInMonth":25,"paidWorkingDays":25,"perDayRate":1040,
          "earnedGross":26000,"absenceDays":22,"absenceAmount":22880,"lopLeaveDays":0,"lopLeaveAmount":0,"lateMarks":0,
          "lateMarksPerUnit":2,"lateLopDaysPerUnit":0.5,"lateLopDays":0,"lateAmount":0,"totalLopDays":22,
          "totalDeductions":22880,"netPay":3120,"flags":[],"days":[]}}}
    """.trimIndent()

    @Test
    fun parsesThePayslipShape() = runBlocking {
        val api = FakeApi(get = { Gson().fromJson(payslipJson, PayslipResponse::class.java) })
        val payslip = PayslipRepository(api).get("token", "2026-09").getOrThrow()
        assertEquals("PS-202609-TST001-V2", payslip.payslipNumber)
        assertEquals(2, payslip.version)
        assertEquals(3120.0, payslip.salary!!.netPay!!, 0.0)
        assertEquals(22.0, payslip.salary!!.totalLopDays!!, 0.0)
        assertEquals("payslip_get", api.requests.single().action)
        assertEquals("2026-09", api.requests.single().month)
    }

    @Test
    fun listRequestHasNoMonth() = runBlocking {
        val api = FakeApi(list = { PayslipListResponse(success = true, payslips = listOf(PayslipListItemDto("PS-1", "2026-09", 1, null, 100.0))) })
        val list = PayslipRepository(api).list("token").getOrThrow()
        assertEquals(1, list.size)
        assertTrue(!Gson().toJson(api.requests.single()).contains("month"))
    }

    @Test
    fun missingPayslipKeepsServerMessage() = runBlocking {
        val body = """{"success":false,"error":"payslip_not_found","message":"No payslip is available for that month."}"""
        val api = FakeApi(get = { throw HttpException(Response.error<Any>(404, body.toResponseBody("application/json".toMediaType()))) })
        val error = PayslipRepository(api).get("token", "2026-08").exceptionOrNull() as AttendanceApiException
        assertEquals("payslip_not_found", error.code)
        assertEquals("No payslip is available for that month.", error.message)
    }

    @Test
    fun offlineGivesClearMessage() = runBlocking {
        val api = FakeApi(list = { throw IOException("offline") })
        val error = PayslipRepository(api).list("token").exceptionOrNull() as AttendanceApiException
        assertEquals("NETWORK_REQUIRED", error.code)
    }

    @Test
    fun pdfAmountFormatting() {
        assertEquals("Rs. 26,875.00", com.skypass.skytrack.ui.monthly.PayslipPdfGenerator.formatAmount(26875.0))
        assertEquals("Rs. 1,04,000.50", com.skypass.skytrack.ui.monthly.PayslipPdfGenerator.formatAmount(104000.5))
        assertEquals("Rs. 999.00", com.skypass.skytrack.ui.monthly.PayslipPdfGenerator.formatAmount(999.0))
        assertEquals("Rs. 1,23,45,678.90", com.skypass.skytrack.ui.monthly.PayslipPdfGenerator.formatAmount(12345678.9))
        assertEquals("Rs. 0.10", com.skypass.skytrack.ui.monthly.PayslipPdfGenerator.formatAmount(0.1))
        assertEquals("-", com.skypass.skytrack.ui.monthly.PayslipPdfGenerator.formatAmount(null))
    }
}
