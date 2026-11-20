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
import com.google.gson.Gson
import com.skypass.skytrack.data.local.SessionDataStore
import com.skypass.skytrack.data.reminders.ReminderScheduler
import com.skypass.skytrack.data.sync.AttendancePayload
import com.skypass.skytrack.data.sync.OfflineSyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val api: SkyTrackApi,
    private val attendanceDao: AttendanceDao,
    private val pendingActionDao: PendingActionDao,
    private val connectivityObserver: ConnectivityObserver,
    private val reminderScheduler: ReminderScheduler,
    private val offlineSyncManager: OfflineSyncManager,
    private val sessionDataStore: SessionDataStore,
    private val gson: Gson
) {
    private val attendanceActions = setOf("START_WORK", "END_WORK", "START_BREAK", "RESUME_WORK")

    /** True while check-in / break / check-out taps are saved on the phone but not yet on the server */
    private suspend fun hasUnsentAttendance(): Boolean =
        pendingActionDao.getPendingActions().any { it.actionType in attendanceActions }

    suspend fun getToday(token: String): Result<AttendanceDto?> = runCatching {
        val todayStr = LocalDate.now().toString()

        // The phone is ahead of the server until its saved taps are delivered: show the phone's state,
        // so a refresh never puts the employee back "on break" after they ended it offline.
        if (hasUnsentAttendance()) {
            return@runCatching attendanceDao.getAttendance(todayStr)?.toDto()
        }

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
            // Only today's record: an older one would show yesterday's check-out as today's
            val cached = attendanceDao.getAttendance(todayStr)
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

    /**
     * Offline first: the tap is saved on the phone (with the time it happened) and shown straight
     * away, then delivered by the outbox. WorkManager keeps delivering it if the app goes to the
     * background, is closed or the phone is offline, so "Resume Work" is never lost. When the network
     * is up, this waits briefly for the server so mistakes (e.g. "already checked in") still show.
     */
    private suspend fun performWriteAction(action: String, token: String): Result<AttendanceDto?> =
        runCatching {
            val todayStr = LocalDate.now().toString()
            val occurredAt = Instant.now()
            val local = applyLocally(action, todayStr, occurredAt)
            val employeeId = sessionDataStore.session.first()?.employeeId
            val queueId = offlineSyncManager.queue(
                PendingActionEntity(
                    actionType = action.uppercase(),
                    token = token,
                    payloadJson = gson.toJson(AttendancePayload(employeeId, occurredAt.toString())),
                    createdAt = occurredAt.toEpochMilli()
                )
            )
            reminderScheduler.syncBreak(local)

            if (!connectivityObserver.isConnected()) return@runCatching local

            val report = withTimeoutOrNull(IMMEDIATE_SEND_TIMEOUT_MS) { offlineSyncManager.syncPendingActions() }
                ?: return@runCatching local // still on its way; WorkManager finishes it

            report.rejected[queueId]?.let { (code, message) ->
                // Undo the optimistic change: reload what the server really has
                refreshFromServer(token, todayStr)
                throw AttendanceApiException(code = code ?: rejectionCode(action, message), message = message)
            }
            if (queueId in report.delivered) {
                val fromServer = report.delivered[queueId]
                fromServer?.let { reminderScheduler.syncBreak(it) }
                return@runCatching fromServer ?: local
            }
            local
        }

    private suspend fun refreshFromServer(token: String, todayStr: String) {
        if (hasUnsentAttendance()) return
        runCatching {
            val response = api.attendanceToday(BuildConfig.SKYTRACK_API_URL, "Bearer $token", AttendanceRequest("attendance_today", token))
            if (response.success) {
                val fresh = response.attendance
                if (fresh != null) attendanceDao.insertOrUpdate(AttendanceCacheEntity.fromDto(fresh, todayStr))
                reminderScheduler.syncBreak(fresh)
            }
        }
    }

    /** Maps the live server's plain-text refusals to the codes the Home screen already explains */
    private fun rejectionCode(action: String, message: String): String? {
        val text = message.lowercase()
        return when {
            "already" in text && action == "start_work" -> "ALREADY_STARTED"
            "already" in text && action == "start_break" -> "ALREADY_ON_BREAK"
            ("no active break" in text || "not on break" in text) && action == "resume_work" -> "NO_ACTIVE_BREAK"
            "already" in text && action == "end_work" -> "ALREADY_ENDED"
            else -> null
        }
    }

    /** Applies a tap to the phone's copy of today's attendance and returns the new state */
    private suspend fun applyLocally(action: String, todayStr: String, at: Instant): AttendanceDto {
        val existing = attendanceDao.getAttendance(todayStr)
        val nowIso = at.toString()

        val updatedDto = when (action) {
            "start_work" -> {
                existing?.toDto()?.copy(
                    date = todayStr,
                    login = existing.login ?: nowIso,
                    status = existing.status ?: "Present"
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
                val breakStart = com.skypass.skytrack.data.reminders.ReminderRules.parseInstant(current.breakStartedAt)
                val extraSeconds = breakStart?.let { maxOf(0L, java.time.Duration.between(it, at).seconds) } ?: 0L
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

    private companion object {
        /** How long a tap waits for the server before the screen moves on (delivery continues in the background) */
        const val IMMEDIATE_SEND_TIMEOUT_MS = 15_000L
    }
}

class AttendanceApiException(
    val code: String?,
    override val message: String
) : Exception(message)
