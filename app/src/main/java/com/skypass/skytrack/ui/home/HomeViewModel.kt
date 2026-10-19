package com.skypass.skytrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.remote.AttendanceDto
import com.skypass.skytrack.data.repository.AttendanceApiException
import com.skypass.skytrack.data.repository.AttendanceRepository
import com.skypass.skytrack.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.ConnectException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val attendance: AttendanceDto? = null,
    val isLoading: Boolean = true,
    val isActionLoading: Boolean = false,
    val isStartingWork: Boolean = false,
    val isEndingWork: Boolean = false,
    val isOnBreak: Boolean = false,
    val breakSeconds: Long = 0L,
    val error: String? = null,
    val signedOut: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var breakJob: Job? = null

    fun loadToday(session: Session) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            attendanceRepository.getToday(session.token)
                .onSuccess { attendance ->
                    // If server returns null and not starting work, today has no attendance record yet
                    val resolvedAttendance = if (attendance != null) {
                        val currentLogin = _state.value.attendance?.login
                        val resolvedLogin = attendance.login?.takeIf { it.isNotBlank() } ?: currentLogin
                        attendance.copy(login = resolvedLogin)
                    } else if (_state.value.isStartingWork) {
                        _state.value.attendance
                    } else {
                        null
                    }

                    val isOnBreak = resolvedAttendance?.breakStartedAt != null
                    val baseBreakSeconds = resolvedAttendance?.totalBreakSeconds ?: 0L
                    var extraBreakSeconds = 0L
                    if (isOnBreak) {
                        val breakStartInstant = parseInstantSafe(resolvedAttendance?.breakStartedAt)
                        if (breakStartInstant != null) {
                            extraBreakSeconds = maxOf(0L, java.time.Duration.between(breakStartInstant, java.time.Instant.now()).seconds)
                        }
                    }

                    _state.value = _state.value.copy(
                        attendance = resolvedAttendance,
                        isLoading = false,
                        isOnBreak = isOnBreak,
                        breakSeconds = if (resolvedAttendance != null) maxOf(0L, baseBreakSeconds + extraBreakSeconds) else 0L
                    )
                    
                    if (isOnBreak) {
                        startBreakTimer()
                    } else {
                        breakJob?.cancel()
                    }
                }
                .onFailure { error ->
                    handleFailure(error, keepAttendance = true)
                }
        }
    }

    fun startWork(session: Session) {
        val nowTime = java.time.Instant.now().toString()
        val current = AttendanceDto(
            date = LocalDate.now().toString(),
            employeeId = session.employeeId,
            name = session.name,
            login = nowTime
        )

        // Optimistically set login so UI cleanly enters State 2
        _state.value = _state.value.copy(
            attendance = current,
            isActionLoading = true,
            isStartingWork = true,
            isOnBreak = false,
            breakSeconds = 0L,
            error = null
        )

        performAction(session, isStart = true) {
            attendanceRepository.startWork(session.token)
        }
    }

    fun endWork(session: Session) {
        if (_state.value.isActionLoading) {
            return
        }
        if (_state.value.isOnBreak) {
            _state.value = _state.value.copy(
                error = "Cannot end work while a break is still active. Please end your break first."
            )
            return
        }
        val nowTime = java.time.Instant.now().toString()
        val current = _state.value.attendance ?: AttendanceDto(
            date = LocalDate.now().toString(),
            employeeId = session.employeeId,
            name = session.name
        )

        _state.value = _state.value.copy(
            attendance = current.copy(logout = nowTime),
            isActionLoading = true,
            isEndingWork = true,
            isOnBreak = false,
            error = null
        )
        breakJob?.cancel()

        performAction(session, isStart = false) {
            attendanceRepository.endWork(session.token)
        }
    }

    fun toggleBreak(session: Session) {
        if (_state.value.isActionLoading) {
            return
        }
        val nextBreak = !_state.value.isOnBreak
        _state.value = _state.value.copy(
            isActionLoading = true,
            error = null
        )

        breakJob?.cancel()

        viewModelScope.launch {
            val result = if (nextBreak) {
                attendanceRepository.startBreak(session.token)
            } else {
                attendanceRepository.resumeWork(session.token)
            }

            result.onSuccess { attendance ->
                val serverIsOnBreak = attendance?.isOnBreak ?: nextBreak
                val baseBreakSeconds = attendance?.totalBreakSeconds ?: _state.value.breakSeconds
                val currentBreakStartedAt = attendance?.breakStartedAt
                    ?: if (serverIsOnBreak) java.time.Instant.now().toString() else null

                val resolvedAttendance = (attendance ?: _state.value.attendance)?.copy(
                    breakStartedAt = currentBreakStartedAt,
                    isOnBreak = serverIsOnBreak
                )

                var extraBreakSeconds = 0L
                if (serverIsOnBreak) {
                    val breakStartInstant = parseInstantSafe(currentBreakStartedAt)
                    if (breakStartInstant != null) {
                        extraBreakSeconds = maxOf(0L, java.time.Duration.between(breakStartInstant, java.time.Instant.now()).seconds)
                    }
                }

                _state.value = _state.value.copy(
                    attendance = resolvedAttendance ?: _state.value.attendance,
                    isOnBreak = serverIsOnBreak,
                    breakSeconds = if (serverIsOnBreak) maxOf(0L, baseBreakSeconds + extraBreakSeconds) else baseBreakSeconds,
                    isActionLoading = false
                )
                if (serverIsOnBreak) {
                    startBreakTimer()
                } else {
                    breakJob?.cancel()
                }
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isActionLoading = false
                )
                handleFailure(error, keepAttendance = true)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun performAction(
        session: Session,
        isStart: Boolean,
        action: suspend () -> Result<AttendanceDto?>
    ) {
        viewModelScope.launch {
            action()
                .onSuccess { attendance ->
                    val stableLogin = attendance?.login?.takeIf { it.isNotBlank() }
                        ?: _state.value.attendance?.login?.takeIf { it.isNotBlank() }
                        ?: java.time.Instant.now().toString()

                    val mergedAttendance = (attendance ?: _state.value.attendance ?: AttendanceDto()).copy(
                        login = if (isStart) stableLogin else (attendance?.login ?: _state.value.attendance?.login)
                    )

                    _state.value = _state.value.copy(
                        attendance = mergedAttendance
                    )

                    // Refresh from backend, but NEVER overwrite a valid login with null/blank
                    attendanceRepository.getToday(session.token)
                        .onSuccess { latest ->
                            val finalLogin = latest?.login?.takeIf { it.isNotBlank() }
                                ?: mergedAttendance.login?.takeIf { it.isNotBlank() }
                                ?: if (isStart) stableLogin else null

                            val finalAttendance = (latest ?: mergedAttendance).copy(
                                login = finalLogin
                            )

                            val isOnBreak = finalAttendance.breakStartedAt != null
                            val baseBreakSeconds = finalAttendance.totalBreakSeconds ?: 0L
                            var extraBreakSeconds = 0L
                            if (isOnBreak) {
                                val breakStartInstant = parseInstantSafe(finalAttendance.breakStartedAt)
                                if (breakStartInstant != null) {
                                    extraBreakSeconds = maxOf(0L, java.time.Duration.between(breakStartInstant, java.time.Instant.now()).seconds)
                                }
                            }

                            _state.value = _state.value.copy(
                                attendance = finalAttendance,
                                isActionLoading = false,
                                isStartingWork = false,
                                isEndingWork = false,
                                isOnBreak = isOnBreak,
                                breakSeconds = maxOf(0L, baseBreakSeconds + extraBreakSeconds)
                            )
                            if (isOnBreak) {
                                startBreakTimer()
                            } else {
                                breakJob?.cancel()
                            }
                        }
                        .onFailure {
                            _state.value = _state.value.copy(
                                isActionLoading = false,
                                isStartingWork = false,
                                isEndingWork = false
                            )
                        }
                }
                .onFailure { error ->
                    val apiError = error as? AttendanceApiException
                    if (isStart && apiError?.code?.uppercase() == "ALREADY_STARTED") {
                        // Work was already started! Keep state stably in State 2
                        val stableLogin = _state.value.attendance?.login?.takeIf { it.isNotBlank() }
                            ?: java.time.Instant.now().toString()
                        _state.value = _state.value.copy(
                            attendance = (_state.value.attendance ?: AttendanceDto()).copy(login = stableLogin),
                            isActionLoading = false,
                            isStartingWork = false,
                            isEndingWork = false,
                            error = null
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isActionLoading = false,
                            isStartingWork = false,
                            isEndingWork = false
                        )
                        handleFailure(error, keepAttendance = true)
                    }
                }
        }
    }

    private fun handleFailure(error: Throwable, keepAttendance: Boolean = false) {
        val apiError = error as? AttendanceApiException

        val isSessionExpired = apiError?.code.equals("invalid_session", ignoreCase = true) ||
            apiError?.code.equals("INVALID_SESSION", ignoreCase = true) ||
            apiError?.code.equals("AUTH_REQUIRED", ignoreCase = true) ||
            apiError?.code.equals("SESSION_EXPIRED", ignoreCase = true) ||
            error.message?.contains("session is invalid", ignoreCase = true) == true ||
            error.message?.contains("session expired", ignoreCase = true) == true

        if (isSessionExpired) {
            viewModelScope.launch {
                authRepository.logout()
            }
            _state.value = _state.value.copy(
                isLoading = false,
                isActionLoading = false,
                isStartingWork = false,
                isEndingWork = false,
                error = "Your session has expired. Please sign in again.",
                signedOut = true
            )
            return
        }

        val message = when (apiError?.code?.uppercase()) {
            "ALREADY_STARTED" -> "Work has already been started for today."
            "NOT_STARTED" -> "You have not started work today."
            "ALREADY_ENDED" -> "Work has already been ended for today."
            "ALREADY_ON_BREAK" -> "Cannot start another break while already on a break."
            "NO_ACTIVE_BREAK" -> "Cannot end a break when no break is active."
            "ACTIVE_BREAK" -> "Cannot end work while a break is still active. Please end your break first."
            "INVALID_REQUEST" -> com.skypass.skytrack.data.remote.ApiErrorExtractor.cleanErrorString(apiError.message, "Invalid attendance request.")
            else -> apiError?.message?.let { com.skypass.skytrack.data.remote.ApiErrorExtractor.cleanErrorString(it, "Attendance request failed.") }
                ?: com.skypass.skytrack.data.remote.ApiErrorExtractor.getMessage(error, "Attendance request failed.")
        }

        _state.value = _state.value.copy(
            isLoading = false,
            isActionLoading = false,
            isStartingWork = false,
            isEndingWork = false,
            error = message,
            attendance = if (keepAttendance) _state.value.attendance else null
        )
    }

    fun refreshTimers() {
        if (_state.value.isOnBreak) {
            val currentAttendance = _state.value.attendance ?: return
            val baseSeconds = currentAttendance.totalBreakSeconds ?: 0L
            val startInstant = parseInstantSafe(currentAttendance.breakStartedAt)
            if (startInstant != null) {
                val liveExtra = maxOf(0L, java.time.Duration.between(startInstant, java.time.Instant.now()).seconds)
                _state.value = _state.value.copy(
                    breakSeconds = maxOf(0L, baseSeconds + liveExtra)
                )
            }
        }
    }

    private fun startBreakTimer() {
        breakJob?.cancel()
        breakJob = viewModelScope.launch {
            while (_state.value.isOnBreak) {
                val currentAttendance = _state.value.attendance
                val baseSeconds = currentAttendance?.totalBreakSeconds ?: 0L
                val startInstant = parseInstantSafe(currentAttendance?.breakStartedAt)

                if (startInstant != null) {
                    val liveExtra = maxOf(0L, java.time.Duration.between(startInstant, java.time.Instant.now()).seconds)
                    _state.value = _state.value.copy(
                        breakSeconds = maxOf(0L, baseSeconds + liveExtra)
                    )
                }
                delay(1000)
            }
        }
    }

    private fun parseInstantSafe(timeStr: String?): java.time.Instant? {
        if (timeStr.isNullOrBlank()) return null
        val trimmed = timeStr.trim()
        return try {
            try {
                java.time.Instant.parse(trimmed)
            } catch (_: Exception) {
                try {
                    java.time.OffsetDateTime.parse(trimmed).toInstant()
                } catch (_: Exception) {
                    java.time.LocalDateTime.parse(trimmed).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
