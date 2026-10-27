package com.skypass.skytrack.data.repository

import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.remote.ApiErrorExtractor
import com.skypass.skytrack.data.remote.SalaryDto
import com.skypass.skytrack.data.remote.SalarySummaryRequest
import com.skypass.skytrack.data.remote.SkyTrackApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the employee's own provisional salary. Deliberately has no local cache: salary figures are
 * only ever held in memory while the card is revealed.
 */
@Singleton
class SalaryRepository @Inject constructor(
    private val api: SkyTrackApi
) {
    suspend fun currentMonth(token: String): Result<SalaryDto> = runCatching {
        try {
            val response = api.salarySummary(
                BuildConfig.SKYTRACK_API_URL,
                "Bearer $token",
                SalarySummaryRequest()
            )
            val salary = response.salary
            if (!response.success || salary == null) {
                throw AttendanceApiException(
                    code = response.error,
                    message = ApiErrorExtractor.cleanErrorString(
                        response.message ?: "Unable to load your salary.",
                        "Unable to load your salary. Please try again."
                    )
                )
            }
            salary
        } catch (httpException: retrofit2.HttpException) {
            val (code, message) = ApiErrorExtractor.extractCodeAndMessage(
                httpException,
                "Unable to load your salary. Please try again."
            )
            throw AttendanceApiException(code = code ?: "HTTP_${httpException.code()}", message = message)
        } catch (_: IOException) {
            throw AttendanceApiException(
                code = "NETWORK_REQUIRED",
                message = "Your salary needs an internet connection. Please reconnect and try again."
            )
        }
    }
}
