package com.skypass.skytrack.data.sync

import com.google.gson.Gson
import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.local.ConnectivityObserver
import com.skypass.skytrack.data.local.db.dao.AttendanceDao
import com.skypass.skytrack.data.local.db.dao.PendingActionDao
import com.skypass.skytrack.data.local.db.entity.AttendanceCacheEntity
import com.skypass.skytrack.data.local.db.entity.PendingActionEntity
import com.skypass.skytrack.data.remote.AttendanceRequest
import com.skypass.skytrack.data.remote.LeaveSubmitRequest
import com.skypass.skytrack.data.remote.LocationPayloadDto
import com.skypass.skytrack.data.remote.SkyTrackApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class LeaveSubmitPayload(
    val from: String,
    val to: String,
    val type: String,
    val reason: String
)

@Singleton
class OfflineSyncManager @Inject constructor(
    private val api: SkyTrackApi,
    private val pendingActionDao: PendingActionDao,
    private val attendanceDao: AttendanceDao,
    private val connectivityObserver: ConnectivityObserver,
    private val gson: Gson
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun startObserving() {
        scope.launch {
            connectivityObserver.observe().collectLatest { status ->
                if (status == ConnectivityObserver.Status.AVAILABLE) {
                    syncPendingActions()
                }
            }
        }
    }

    suspend fun syncPendingActions(): Boolean = syncMutex.withLock {
        val pendingList = pendingActionDao.getPendingActions()
        if (pendingList.isEmpty()) {
            return true
        }

        _isSyncing.value = true
        var allSuccess = true

        try {
            for (action in pendingList) {
                val success = processAction(action)
                if (success) {
                    pendingActionDao.delete(action.id)
                } else {
                    allSuccess = false
                    // If network failed, stop further processing
                    if (!connectivityObserver.isConnected()) {
                        break
                    }
                }
            }
        } finally {
            _isSyncing.value = false
        }

        return allSuccess
    }

    private fun queuedLocation(action: PendingActionEntity): LocationPayloadDto? =
        action.payloadJson?.let {
            try {
                gson.fromJson(it, LocationPayloadDto::class.java)
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun processAction(action: PendingActionEntity): Boolean {
        return try {
            when (action.actionType) {
                "START_WORK" -> {
                    val resp = api.startWork(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer ${action.token}",
                        AttendanceRequest("start_work", action.token)
                    )
                    if (resp.success && resp.attendance != null) {
                        attendanceDao.insertOrUpdate(AttendanceCacheEntity.fromDto(resp.attendance))
                    }
                    true
                }
                "END_WORK" -> {
                    val resp = api.endWork(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer ${action.token}",
                        AttendanceRequest("end_work", action.token)
                    )
                    if (resp.success && resp.attendance != null) {
                        attendanceDao.insertOrUpdate(AttendanceCacheEntity.fromDto(resp.attendance))
                    }
                    true
                }
                "START_BREAK" -> {
                    val resp = api.startBreak(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer ${action.token}",
                        AttendanceRequest("start_break", action.token, queuedLocation(action))
                    )
                    if (resp.success && resp.attendance != null) {
                        attendanceDao.insertOrUpdate(AttendanceCacheEntity.fromDto(resp.attendance))
                    }
                    true
                }
                "RESUME_WORK" -> {
                    val resp = api.resumeWork(
                        BuildConfig.SKYTRACK_API_URL,
                        "Bearer ${action.token}",
                        AttendanceRequest("resume_work", action.token, queuedLocation(action))
                    )
                    if (resp.success && resp.attendance != null) {
                        attendanceDao.insertOrUpdate(AttendanceCacheEntity.fromDto(resp.attendance))
                    }
                    true
                }
                "LEAVE_SUBMIT" -> {
                    val payload = action.payloadJson?.let {
                        try {
                            gson.fromJson(it, LeaveSubmitPayload::class.java)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (payload != null) {
                        val resp = api.submitLeave(
                            BuildConfig.SKYTRACK_API_URL,
                            "Bearer ${action.token}",
                            LeaveSubmitRequest(
                                token = action.token,
                                from = payload.from,
                                to = payload.to,
                                type = payload.type,
                                reason = payload.reason
                            )
                        )
                        // If rejected or succeeded, consider action handled to avoid queue deadlock
                        resp.success || resp.error != null
                    } else {
                        // Malformed payload, discard
                        true
                    }
                }
                else -> true
            }
        } catch (e: IOException) {
            // Network failure, will retry when connected
            action.lastError?.let {
                pendingActionDao.update(action.copy(retryCount = action.retryCount + 1, lastError = e.message))
            }
            false
        } catch (e: Exception) {
            // Server error or unexpected exception, remove or mark to prevent blocking
            action.lastError?.let {
                pendingActionDao.update(action.copy(retryCount = action.retryCount + 1, lastError = e.message))
            }
            true
        }
    }
}
