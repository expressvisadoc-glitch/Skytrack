package com.skypass.skytrack.data.repository

import com.google.gson.Gson
import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.local.ConnectivityObserver
import com.skypass.skytrack.data.local.db.dao.AttendanceDao
import com.skypass.skytrack.data.local.db.dao.PendingActionDao
import com.skypass.skytrack.data.local.db.entity.AttendanceCacheEntity
import com.skypass.skytrack.data.local.db.entity.PendingActionEntity
import com.skypass.skytrack.data.remote.AttendanceDto
import com.skypass.skytrack.data.remote.AttendanceRequest
import com.skypass.skytrack.data.remote.LocationPayloadDto
import com.skypass.skytrack.data.remote.SkyTrackApi
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val api: SkyTrackApi,
    private val attendanceDao: AttendanceDao,
    private val pendingActionDao: PendingActionDao,
    private val connectivityObserver: ConnectivityObserver,
    private val gson: Gson
) {
    suspend fun getToday(token: String): Result<AttendanceDto?> = runCatching {
        val todayStr = LocalDate.now().toString()

        try {
            val response = api.attendanceToday(
                BuildConfig.SKYTRACK_API_URL,
                "Bearer $token",
                AttendanceRequest("attendance_today", token)
            )

            if (!response.success) {
                val cleanMsg = com.skypass.skytrack.data.remote.ApiErrorExtractor.cleanErrorString(
                    response.message ?: "Attendance request failed.",
                    "Attendance request failed. Please try again."
                )
                throw AttendanceApiException(
                    code = response.error,
                    message = cleanMsg
                )
            }

            val attendance = response.attendance
            if (attendance != null) {
                attendanceDao.insertOrUpdate(AttendanceCacheEntity.fromDto(attendance, todayStr))
            }
            attendance
        } catch (e: Exception) {
            if (e is AttendanceApiException) {
                // If invalid session, do not fallback to cache
                if (e.code.equals("invalid_session", ignoreCase = true) ||
                    e.code.equals("AUTH_REQUIRED", ignoreCase = true)
                ) {
                    throw e
                }
            }

            // Fallback to local cache when offline or on network failure
            val cached = attendanceDao.getAttendance(todayStr) ?: attendanceDao.getLatestAttendance()
            if (cached != null) {
                cached.toDto()
            } else if (e is IOException || !connectivityObserver.isConnected()) {
                null
            } else {
                throw e
            }
        }
    }

    // Check-in and check-out are verified against the office geofence by the server,
    // so they need a live connection and a fresh location fix (never queued offline).
    suspend fun startWork(token: String, location: LocationPayloadDto): Result<AttendanceDto?> =
        performWriteAction("start_work", token, location)

    suspend fun endWork(token: String, location: LocationPayloadDto): Result<AttendanceDto?> =
        performWriteAction("end_work", token, location)

    // Break location is recorded when available but never blocks the break.
    suspend fun startBreak(token: String, location: LocationPayloadDto? = null): Result<AttendanceDto?> =
        performWriteAction("start_break", token, location)

    suspend fun resumeWork(token: String, location: LocationPayloadDto? = null): Result<AttendanceDto?> =
        performWriteAction("resume_work", token, location)

    private fun requiresLiveVerification(action: String): Boolean =
        action == "start_work" || action == "end_work"

    private fun networkRequiredException(action: String) = AttendanceApiException(
        code = "NETWORK_REQUIRED",
        message = "${if (action == "start_work") "Check-in" else "Check-out"} needs an internet connection so your location can be verified. Please reconnect and try again."
    )

    private suspend fun performWriteAction(
        action: String,
        token: String,
        location: LocationPayloadDto?
    ): Result<AttendanceDto?> =
        runCatching {
            val todayStr = LocalDate.now().toString()
            val isOnline = connectivityObserver.isConnected()

            if (!isOnline) {
                if (requiresLiveVerification(action)) throw networkRequiredException(action)
                return@runCatching handleOfflineWrite(action, token, todayStr, location)
            }

            try {
                val response = when (action) {
                    "start_work" -> api.startWork(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token, location)
                    )
                    "end_work" -> api.endWork(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token, location)
                    )
                    "start_break" -> api.startBreak(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token, location)
                    )
                    "resume_work" -> api.resumeWork(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token, location)
                    )
                    else -> api.attendanceToday(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token, location)
                    )
                }

                if (!response.success) {
                    val cleanMsg = com.skypass.skytrack.data.remote.ApiErrorExtractor.cleanErrorString(
                        response.message ?: "Attendance request failed.",
                        "Attendance request failed. Please try again."
                    )
                    throw AttendanceApiException(
                        code = response.error,
                        message = cleanMsg
                    )
                }

                val attendance = response.attendance
                if (attendance != null) {
                    attendanceDao.insertOrUpdate(AttendanceCacheEntity.fromDto(attendance, todayStr))
                }
                attendance
            } catch (httpException: retrofit2.HttpException) {
                val (errorCode, friendly) = com.skypass.skytrack.data.remote.ApiErrorExtractor.extractCodeAndMessage(
                    httpException,
                    "Attendance request failed. Please try again."
                )
                throw AttendanceApiException(
                    code = errorCode ?: "HTTP_${httpException.code()}",
                    message = friendly
                )
            } catch (ioException: IOException) {
                if (requiresLiveVerification(action)) throw networkRequiredException(action)
                // Network failure during write -> fallback to offline queue
                handleOfflineWrite(action, token, todayStr, location)
            }
        }

    private suspend fun handleOfflineWrite(
        action: String,
        token: String,
        todayStr: String,
        location: LocationPayloadDto?
    ): AttendanceDto {
        // Queue the action (with its location, recorded by the server as captured earlier)
        pendingActionDao.insert(
            PendingActionEntity(
                actionType = action.uppercase(),
                token = token,
                payloadJson = location?.let { gson.toJson(it) }
            )
        )

        // Optimistically update local Room cache
        val existing = attendanceDao.getAttendance(todayStr) ?: attendanceDao.getLatestAttendance()
        val nowIso = Instant.now().toString()

        val updatedDto = when (action) {
            "start_work" -> {
                existing?.toDto()?.copy(
                    date = todayStr,
                    login = existing.login ?: nowIso,
                    status = "Present"
                ) ?: AttendanceDto(
                    date = todayStr,
                    login = nowIso,
                    status = "Present"
                )
            }
            "end_work" -> {
                val current = existing?.toDto() ?: AttendanceDto(date = todayStr)
                current.copy(
                    logout = nowIso,
                    isOnBreak = false,
                    breakStartedAt = null
                )
            }
            "start_break" -> {
                val current = existing?.toDto() ?: AttendanceDto(date = todayStr)
                current.copy(
                    isOnBreak = true,
                    breakStartedAt = nowIso
                )
            }
            "resume_work" -> {
                val current = existing?.toDto() ?: AttendanceDto(date = todayStr)
                val breakStart = current.breakStartedAt
                var extraSeconds = 0L
                if (breakStart != null) {
                    try {
                        val startInstant = Instant.parse(breakStart)
                        extraSeconds = maxOf(0L, java.time.Duration.between(startInstant, Instant.now()).seconds)
                    } catch (_: Exception) {}
                }
                val total = (current.totalBreakSeconds ?: 0L) + extraSeconds
                current.copy(
                    isOnBreak = false,
                    breakStartedAt = null,
                    totalBreakSeconds = total
                )
            }
            else -> existing?.toDto() ?: AttendanceDto(date = todayStr)
        }

        attendanceDao.insertOrUpdate(AttendanceCacheEntity.fromDto(updatedDto, todayStr))
        return updatedDto
    }
}

class AttendanceApiException(
    val code: String?,
    override val message: String
) : Exception(message)
