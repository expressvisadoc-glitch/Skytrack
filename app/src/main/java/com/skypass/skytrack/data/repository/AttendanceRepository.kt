package com.skypass.skytrack.data.repository

import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.local.ConnectivityObserver
import com.skypass.skytrack.data.local.db.dao.AttendanceDao
import com.skypass.skytrack.data.local.db.dao.PendingActionDao
import com.skypass.skytrack.data.local.db.entity.AttendanceCacheEntity
import com.skypass.skytrack.data.local.db.entity.PendingActionEntity
import com.skypass.skytrack.data.remote.AttendanceDto
import com.skypass.skytrack.data.remote.AttendanceRequest
import com.skypass.skytrack.data.remote.SkyTrackApi
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import com.skypass.skytrack.data.reminders.ReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val api: SkyTrackApi,
    private val attendanceDao: AttendanceDao,
    private val pendingActionDao: PendingActionDao,
    private val connectivityObserver: ConnectivityObserver,
    private val reminderScheduler: ReminderScheduler
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
            // Fresh from the server: keep the 30-minute break reminder in step
            reminderScheduler.syncBreak(attendance)
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

    suspend fun startWork(token: String): Result<AttendanceDto?> =
        performWriteAction("start_work", token)

    suspend fun endWork(token: String): Result<AttendanceDto?> =
        performWriteAction("end_work", token)

    suspend fun startBreak(token: String): Result<AttendanceDto?> =
        performWriteAction("start_break", token)

    suspend fun resumeWork(token: String): Result<AttendanceDto?> =
        performWriteAction("resume_work", token)

    private suspend fun performWriteAction(action: String, token: String): Result<AttendanceDto?> =
        runCatching {
            val todayStr = LocalDate.now().toString()
            val isOnline = connectivityObserver.isConnected()

            if (!isOnline) {
                return@runCatching handleOfflineWrite(action, token, todayStr)
            }

            try {
                val response = when (action) {
                    "start_work" -> api.startWork(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token)
                    )
                    "end_work" -> api.endWork(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token)
                    )
                    "start_break" -> api.startBreak(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token)
                    )
                    "resume_work" -> api.resumeWork(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token)
                    )
                    else -> api.attendanceToday(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer $token",
                        AttendanceRequest(action, token)
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
                when {
                    attendance != null -> reminderScheduler.syncBreak(attendance)
                    action == "start_break" -> reminderScheduler.scheduleBreak(Instant.now().toString())
                    action == "resume_work" || action == "end_work" -> reminderScheduler.cancelBreak()
                }
                attendance
            } catch (httpException: retrofit2.HttpException) {
                val friendly = com.skypass.skytrack.data.remote.ApiErrorExtractor.getMessage(
                    httpException,
                    "Attendance request failed. Please try again."
                )
                throw AttendanceApiException(
                    code = "HTTP_${httpException.code()}",
                    message = friendly
                )
            } catch (ioException: IOException) {
                // Network failure during write -> fallback to offline queue
                handleOfflineWrite(action, token, todayStr)
            }
        }

    private suspend fun handleOfflineWrite(action: String, token: String, todayStr: String): AttendanceDto {
        // Queue the action
        pendingActionDao.insert(
            PendingActionEntity(
                actionType = action.uppercase(),
                token = token
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
        reminderScheduler.syncBreak(updatedDto)
        return updatedDto
    }
}

class AttendanceApiException(
    val code: String?,
    override val message: String
) : Exception(message)
