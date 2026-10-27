package com.skypass.skytrack.data.repository

import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.remote.ApiErrorExtractor
import com.skypass.skytrack.data.remote.PayslipDto
import com.skypass.skytrack.data.remote.PayslipListItemDto
import com.skypass.skytrack.data.remote.PayslipRequest
import com.skypass.skytrack.data.remote.SkyTrackApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The employee's own payslips for locked payroll months. No local cache. */
@Singleton
class PayslipRepository @Inject constructor(
    private val api: SkyTrackApi
) {
    suspend fun list(token: String): Result<List<PayslipListItemDto>> = call {
        val response = api.payslipList(BuildConfig.SKYTRACK_API_URL, "Bearer $token", PayslipRequest("payslip_list"))
        if (!response.success) throw failure(response.error, response.message)
        response.payslips.orEmpty()
    }

    suspend fun get(token: String, month: String): Result<PayslipDto> = call {
        val response = api.payslipGet(BuildConfig.SKYTRACK_API_URL, "Bearer $token", PayslipRequest("payslip_get", month))
        val payslip = response.payslip
        if (!response.success || payslip == null) throw failure(response.error, response.message)
        payslip
    }

    private fun failure(code: String?, message: String?) = AttendanceApiException(
        code = code,
        message = ApiErrorExtractor.cleanErrorString(message ?: "Unable to load payslips.", "Unable to load payslips. Please try again.")
    )

    private suspend fun <T> call(block: suspend () -> T): Result<T> = runCatching {
        try {
            block()
        } catch (httpException: retrofit2.HttpException) {
            val (code, message) = ApiErrorExtractor.extractCodeAndMessage(httpException, "Unable to load payslips. Please try again.")
            throw AttendanceApiException(code = code ?: "HTTP_${httpException.code()}", message = message)
        } catch (_: IOException) {
            throw AttendanceApiException(
                code = "NETWORK_REQUIRED",
                message = "Payslips need an internet connection. Please reconnect and try again."
            )
        }
    }
}
