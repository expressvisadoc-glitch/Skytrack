package com.skypass.skytrack.data.sync

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.local.ConnectivityObserver
import com.skypass.skytrack.data.local.SessionDataStore
import com.skypass.skytrack.data.local.db.dao.AttendanceDao
import com.skypass.skytrack.data.local.db.dao.PendingActionDao
import com.skypass.skytrack.data.local.db.entity.AttendanceCacheEntity
import com.skypass.skytrack.data.local.db.entity.PendingActionEntity
import com.skypass.skytrack.data.remote.ApiErrorExtractor
import com.skypass.skytrack.data.remote.AttendanceDto
import com.skypass.skytrack.data.remote.AttendanceRequest
import com.skypass.skytrack.data.remote.CancelLeaveRequest
import com.skypass.skytrack.data.remote.LeaveSubmitRequest
import com.skypass.skytrack.data.remote.SkyTrackApi
import com.skypass.skytrack.util.JwtUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

data class LeaveSubmitPayload(
    val from: String,
    val to: String,
    val type: String,
    val reason: String,
    val employeeId: String? = null
)

/** Stored with each queued attendance action: who it belongs to and when the button was tapped. */
data class AttendancePayload(
    val employeeId: String? = null,
    val occurredAt: String? = null
)

data class LeaveCancelPayload(
    val requestId: String,
    val employeeId: String? = null
)

/** What happened to the queue in one run */
data class SyncReport(
    /** Actions the server accepted, by queue id, with the attendance it returned (if any) */
    val delivered: Map<Long, AttendanceDto?> = emptyMap(),
    /** Actions the server refused (e.g. "already on a break"), by queue id: code to message */
    val rejected: Map<Long, Pair<String?, String>> = emptyMap(),
    /** Stopped on a network or server problem; the rest is retried later */
    val waitingForNetwork: Boolean = false,
    /** Stopped because the login for the next action has expired; resumes after sign-in */
    val waitingForSignIn: Boolean = false
)

/**
 * The phone's outbox. Every action is saved here first (so it survives the app going to the
 * background, being closed, or the phone being offline) and then delivered in the order it happened.
 * [SyncWorker] runs this from WorkManager; the app also runs it directly for an immediate answer.
 */
@Singleton
class OfflineSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SkyTrackApi,
    private val pendingActionDao: PendingActionDao,
    private val attendanceDao: AttendanceDao,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionDataStore: SessionDataStore,
    private val gson: Gson
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Number of actions saved on this phone and not yet on the server */
    val pendingCount = pendingActionDao.observePendingCount()

    fun startObserving() {
        // Anything left from an earlier session (app closed, phone restarted) goes out now
        SyncWorker.enqueue(context)
        scope.launch {
            connectivityObserver.observe().collectLatest { status ->
                if (status == ConnectivityObserver.Status.AVAILABLE) {
                    runCatching { syncPendingActions() }
                }
            }
        }
        // A new sign-in can unblock actions that were waiting for a fresh login
        scope.launch {
            sessionDataStore.session.collectLatest { session ->
                if (session != null && pendingActionDao.getPendingCount() > 0) SyncWorker.enqueue(context)
            }
        }
    }

    /** Saves an action and asks WorkManager to deliver it, even if the app is closed straight after. */
    suspend fun queue(action: PendingActionEntity): Long {
        val id = pendingActionDao.insert(action)
        SyncWorker.enqueue(context)
        return id
    }

    suspend fun syncPendingActions(): SyncReport = syncMutex.withLock {
        val pendingList = pendingActionDao.getPendingActions()
        if (pendingList.isEmpty()) return SyncReport()

        _isSyncing.value = true
        val delivered = mutableMapOf<Long, AttendanceDto?>()
        val rejected = mutableMapOf<Long, Pair<String?, String>>()
        try {
            // Strictly in order: a "resume work" must never reach the server before its "start break"
            for (action in pendingList) {
                when (val outcome = send(action)) {
                    is Outcome.Delivered -> {
                        pendingActionDao.delete(action.id)
                        delivered[action.id] = outcome.attendance
                    }
                    is Outcome.Rejected -> {
                        // The server will never accept it (e.g. break already ended); keep the queue moving
                        Log.w(TAG, "${action.actionType} refused by the server: ${outcome.message}")
                        pendingActionDao.delete(action.id)
                        rejected[action.id] = outcome.code to outcome.message
                    }
                    is Outcome.Retry -> {
                        pendingActionDao.update(action.copy(retryCount = action.retryCount + 1, lastError = outcome.reason))
                        return SyncReport(delivered, rejected, waitingForNetwork = true)
                    }
                    Outcome.NeedsSignIn -> {
                        return SyncReport(delivered, rejected, waitingForSignIn = true)
                    }
                }
            }
        } finally {
            _isSyncing.value = false
        }
        SyncReport(delivered, rejected)
    }

    private sealed interface Outcome {
        data class Delivered(val attendance: AttendanceDto?) : Outcome
        data class Rejected(val code: String?, val message: String) : Outcome
        data class Retry(val reason: String) : Outcome
        data object NeedsSignIn : Outcome
    }

    private suspend fun send(action: PendingActionEntity): Outcome {
        val ownerId = ownerOf(action)
        val token = usableToken(action.token, ownerId) ?: return Outcome.NeedsSignIn
        val auth = "Bearer $token"
        return try {
            when (action.actionType) {
                "START_WORK", "END_WORK", "START_BREAK", "RESUME_WORK" -> {
                    val name = action.actionType.lowercase()
                    val occurredAt = payload<AttendancePayload>(action)?.occurredAt
                        ?: Instant.ofEpochMilli(action.createdAt).toString()
                    val request = AttendanceRequest(name, token, clientTime = occurredAt, clientSentAt = Instant.now().toString())
                    val url = BuildConfig.SKYTRACK_API_URL
                    val response = when (action.actionType) {
                        "START_WORK" -> api.startWork(url, auth, request)
                        "END_WORK" -> api.endWork(url, auth, request)
                        "START_BREAK" -> api.startBreak(url, auth, request)
                        else -> api.resumeWork(url, auth, request)
                    }
                    if (!response.success) {
                        return refusal(response.error, response.message)
                    }
                    val merged = response.attendance?.let { mergeWithLocal(it) }
                    merged?.let { attendanceDao.insertOrUpdate(AttendanceCacheEntity.fromDto(it)) }
                    Outcome.Delivered(merged)
                }
                "LEAVE_SUBMIT" -> {
                    val p = payload<LeaveSubmitPayload>(action) ?: return Outcome.Rejected(null, "Damaged leave request")
                    val response = api.submitLeave(
                        BuildConfig.SKYTRACK_API_URL, auth,
                        LeaveSubmitRequest(token = token, from = p.from, to = p.to, type = p.type, reason = p.reason)
                    )
                    if (response.success) Outcome.Delivered(null) else refusal(response.error, response.message)
                }
                "LEAVE_CANCEL" -> {
                    val p = payload<LeaveCancelPayload>(action) ?: return Outcome.Rejected(null, "Damaged cancel request")
                    val response = api.cancelLeave(BuildConfig.SKYTRACK_API_URL, auth, CancelLeaveRequest(requestId = p.requestId))
                    if (response.success) Outcome.Delivered(null) else refusal(response.error, response.message)
                }
                else -> Outcome.Rejected(null, "Unknown action ${action.actionType}")
            }
        } catch (e: HttpException) {
            val (code, message) = errorBody(e)
            when (classifyHttpFailure(e.code(), code, message, action.retryCount)) {
                Next.SIGN_IN -> Outcome.NeedsSignIn
                Next.RETRY -> Outcome.Retry("HTTP ${e.code()}")
                Next.GIVE_UP -> Outcome.Rejected(code, message ?: "Request failed (${e.code()}).")
            }
        } catch (e: IOException) {
            Outcome.Retry(e.message ?: "network")
        } catch (e: Exception) {
            // Unexpected (e.g. a response that could not be read): try again later rather than lose it
            Outcome.Retry(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * The live server's check-in / check-out replies leave out the times; keep the phone's times
     * rather than blanking them (the next refresh from the server replaces them anyway).
     */
    private suspend fun mergeWithLocal(fromServer: AttendanceDto): AttendanceDto {
        val date = fromServer.date?.take(10) ?: java.time.LocalDate.now().toString()
        val local = attendanceDao.getAttendance(date)?.toDto() ?: return fromServer
        return fromServer.copy(
            login = fromServer.login?.takeIf { it.isNotBlank() } ?: local.login,
            logout = fromServer.logout?.takeIf { it.isNotBlank() } ?: local.logout
        )
    }

    private fun errorBody(e: HttpException): Pair<String?, String?> {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        if (body.isNullOrBlank()) return null to null
        return runCatching {
            val json = JsonParser.parseString(body).asJsonObject
            val code = json.get("error")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            val message = json.get("message")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                ?.let { ApiErrorExtractor.cleanErrorString(it, "Request refused.") }
            code to message
        }.getOrDefault(null to null)
    }

    private fun refusal(code: String?, message: String?): Outcome =
        if (isSessionProblem(code, message)) Outcome.NeedsSignIn
        else Outcome.Rejected(code, ApiErrorExtractor.cleanErrorString(message ?: "Request refused.", "Request refused."))

    /**
     * The login to send an action with. An expired login is never sent: the server would answer
     * "invalid session" and the app would sign the current user out. If the saved login has expired,
     * the current login is used only when it belongs to the same employee.
     */
    private suspend fun usableToken(savedToken: String, ownerId: String?): String? {
        if (savedToken.isNotBlank() && !JwtUtils.isTokenExpired(savedToken)) return savedToken
        val session = sessionDataStore.session.first() ?: return null
        if (JwtUtils.isTokenExpired(session.token)) return null
        val sameEmployee = ownerId.isNullOrBlank() || ownerId.equals(session.employeeId, ignoreCase = true)
        return if (sameEmployee) session.token else null
    }

    private fun ownerOf(action: PendingActionEntity): String? = when (action.actionType) {
        "LEAVE_CANCEL" -> payload<LeaveCancelPayload>(action)?.employeeId
        "LEAVE_SUBMIT" -> payload<LeaveSubmitPayload>(action)?.employeeId
        else -> payload<AttendancePayload>(action)?.employeeId
    }

    private inline fun <reified T> payload(action: PendingActionEntity): T? =
        action.payloadJson?.let { runCatching { gson.fromJson(it, T::class.java) }.getOrNull() }

    private companion object {
        const val TAG = "OfflineSyncManager"
    }
}

/** What to do with a queued action after the server answered with an HTTP error */
enum class Next { RETRY, GIVE_UP, SIGN_IN }

/** About two hours of WorkManager back-off before an unexplained server error is given up */
const val MAX_SERVER_RETRIES = 8

internal fun isSessionProblem(code: String?, message: String?): Boolean {
    val text = "${code.orEmpty()} ${message.orEmpty()}".lowercase()
    return "invalid_session" in text || "session_expired" in text || "auth_required" in text || "unauthorized" in text
}

/**
 * - Login problems wait for the employee to sign in again (the action is kept).
 * - Gateway errors, timeouts and rate limits mean the server is unreachable or busy: retry.
 * - Another 5xx with no reason is treated as an outage, but given up after [MAX_SERVER_RETRIES].
 * - Anything the server explained (e.g. "work already started", "earlier day") will not change on
 *   retry, so it is dropped to keep the rest of the queue moving.
 */
fun classifyHttpFailure(status: Int, code: String?, message: String?, retryCount: Int): Next = when {
    status == 401 || isSessionProblem(code, message) -> Next.SIGN_IN
    status == 408 || status == 429 || status in 502..504 -> Next.RETRY
    status >= 500 && code == null && message == null && retryCount < MAX_SERVER_RETRIES -> Next.RETRY
    else -> Next.GIVE_UP
}
