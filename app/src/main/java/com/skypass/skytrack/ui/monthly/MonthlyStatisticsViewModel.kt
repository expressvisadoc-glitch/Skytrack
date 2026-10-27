package com.skypass.skytrack.ui.monthly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.skytrack.data.remote.MonthlyAttendanceDto
import com.skypass.skytrack.data.remote.MonthlyAttendanceSummary
import com.skypass.skytrack.data.repository.AuthRepository
import com.skypass.skytrack.data.repository.MonthlyApiException
import com.skypass.skytrack.data.repository.MonthlyStatisticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MonthlyStatisticsUiState(
    val isLoading: Boolean = false,
    val summary: MonthlyAttendanceSummary? = null,
    val attendance: List<MonthlyAttendanceDto> = emptyList(),
    val error: String? = null,
    val totalActiveHoursStr: String = "0h 00m",
    val actualHoursNeededStr: String = "0h",
    val selectedMonthRaw: String = java.time.LocalDate.now().toString().substring(0, 7)
)

@HiltViewModel
class MonthlyStatisticsViewModel @Inject constructor(
    private val repository: MonthlyStatisticsRepository,
    private val authRepository: AuthRepository,
    private val leaveRepository: com.skypass.skytrack.data.repository.LeaveRepository,
    private val sessionDataStore: com.skypass.skytrack.data.local.SessionDataStore
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(MonthlyStatisticsUiState())

    val uiState: StateFlow<MonthlyStatisticsUiState> =
        _uiState.asStateFlow()

    private var selectedMonth: LocalDate =
        LocalDate.now().withDayOfMonth(1)

    init {
        loadMonth()
    }

    fun loadPreviousMonth() {
        selectedMonth = selectedMonth.minusMonths(1)
        loadMonth()
    }

    fun loadNextMonth() {
        val currentMonth = LocalDate.now().withDayOfMonth(1)
        if (selectedMonth.isBefore(currentMonth)) {
            selectedMonth = selectedMonth.plusMonths(1)
            loadMonth()
        }
    }

    fun retry() {
        loadMonth()
    }

    private fun loadMonth() {
        val month = selectedMonth.toString().substring(0, 7)

        viewModelScope.launch {
            _uiState.value = MonthlyStatisticsUiState(isLoading = true, selectedMonthRaw = month)
            android.util.Log.d("MonthlyStats", "Fetching attendance for month: $month")

            repository
                .getMonthlyAttendance(month)
                .onSuccess { response ->
                    android.util.Log.d("MonthlyStats", "API Response Success: $response")
                    
                    val summary = response.actualSummary
                    val attendance = response.actualAttendance

                    val session = sessionDataStore.session.first()
                    val employeeCreatedDate = (session?.joiningDate?.takeIf { it.isNotBlank() }
                        ?: session?.createdAt?.takeIf { it.isNotBlank() }
                        ?: response.employee?.joiningDate?.takeIf { !it.isNullOrBlank() }
                        ?: response.employee?.createdAt?.takeIf { !it.isNullOrBlank() }
                        ?: "")
                        .take(10)
                    val todayStr = com.skypass.skytrack.util.AttendanceDates.today()

                    val filteredAttendance = attendance.filter { item ->
                        val isNotFuture = item.date <= todayStr
                        val isAfterCreation = if (employeeCreatedDate.isNotBlank()) item.date >= employeeCreatedDate else true
                        isNotFuture && isAfterCreation
                    }.map { item ->
                        val inStr = item.login ?: item.rawCheckIn
                        var outStr = item.logout ?: item.rawCheckOut
                        val hasPunch = !inStr.isNullOrBlank()

                        if (hasPunch) {
                            val isPastDay = item.date < todayStr
                            // If there is no checkout from an employee till 11:59 (past day), mark checkout at 6:00 PM
                            if (outStr.isNullOrBlank() && isPastDay) {
                                outStr = "06:00 PM"
                            }

                            val s = item.status.lowercase()
                            val isZeroHours = item.hours.isNullOrBlank() || item.hours == "0h 00m" || item.hours == "00:00"
                            val resolvedWorkedMins = if ((item.workedMinutes ?: 0) > 0) {
                                item.workedMinutes!!
                            } else if (!isZeroHours && !item.hours.isNullOrBlank()) {
                                parseHoursStringToMinutes(item.hours)
                            } else if (!outStr.isNullOrBlank()) {
                                // Includes auto-checkout "06:00 PM" — treat it as a valid checkout time
                                calculateMinutesFromTimes(inStr!!, outStr, item.breakMinutes ?: 0)
                            } else if (item.date == todayStr) {
                                calculateLiveMinutes(inStr!!, item.breakMinutes ?: 0)
                            } else {
                                calculatePastIncompleteMinutes(inStr!!, item.breakMinutes ?: 0)
                            }

                            val resolvedHours = if (!isZeroHours && !item.hours.isNullOrBlank()) {
                                item.hours
                            } else {
                                val h = resolvedWorkedMins / 60
                                val m = resolvedWorkedMins % 60
                                "${h}h ${String.format(java.util.Locale.ENGLISH, "%02dm", m)}"
                            }

                            val resolvedStatus = if (s.contains("leave") || s.contains("absent") || s.contains("incomplete")) {
                                "present"
                            } else {
                                item.status
                            }

                            item.copy(
                                logout = outStr,
                                rawCheckOut = item.rawCheckOut ?: (if (isPastDay) "${item.date}T12:30:00.000Z" else null),
                                status = resolvedStatus,
                                hours = resolvedHours,
                                workedMinutes = resolvedWorkedMins
                            )
                        } else {
                            item
                        }
                    }

                    val totalMinutes = calculateTotalActiveMinutes(summary, filteredAttendance)
                    val totalActiveHoursStr = formatMinutesToHours(totalMinutes)

                    val dynamicPresentDays = filteredAttendance.count { item ->
                        val s = item.status.lowercase()
                        val hasPunch = !item.login.isNullOrBlank() || !item.rawCheckIn.isNullOrBlank()
                        s.contains("present") || s.contains("working") || s.contains("incomplete") || hasPunch
                    }
                    val dynamicLeaveDays = filteredAttendance.count { item ->
                        val s = item.status.lowercase()
                        val hasPunch = !item.login.isNullOrBlank() || !item.rawCheckIn.isNullOrBlank()
                        !hasPunch && (s.contains("leave") || s.contains("sick") || s.contains("casual"))
                    }
                    val dynamicAbsentDays = summary?.absentDays ?: filteredAttendance.count { item ->
                        item.status.equals("absent", ignoreCase = true)
                    }
                    val elapsedWorkingDays = filteredAttendance.count { item ->
                        val s = item.status.lowercase()
                        !s.contains("weekend") && !s.contains("holiday")
                    }
                    val dynamicWorkingDays = summary?.workingDays?.takeIf { it > 0 } ?: elapsedWorkingDays.coerceAtLeast(1)
                    val dynamicPresentPercent = summary?.presentPercent?.takeIf { it > 0 } ?: if (dynamicWorkingDays > 0) {
                        ((dynamicPresentDays.toDouble() / dynamicWorkingDays.toDouble()) * 100).toInt().coerceIn(0, 100)
                    } else 0

                    val resolvedPresentDays = Math.max(summary?.presentDays ?: 0, dynamicPresentDays)
                    val dynamicAverageHoursStr = if (resolvedPresentDays > 0) {
                        val avgMins = totalMinutes / resolvedPresentDays
                        val avgH = avgMins / 60
                        val avgM = avgMins % 60
                        "${avgH}h ${String.format(java.util.Locale.ENGLISH, "%02dm", avgM)}"
                    } else "0h 00m"

                    val targetMinutes = dynamicWorkingDays * 8 * 60
                    val diffMinutes = totalMinutes - targetMinutes
                    val dynamicOvertimeFormatted = if (diffMinutes > 0) formatMinutesToHours(diffMinutes) else "0h 00m"
                    val dynamicDifferenceFormatted = if (diffMinutes >= 0) {
                        "+${formatMinutesToHours(diffMinutes)}"
                    } else {
                        val absDiff = Math.abs(diffMinutes)
                        "-${formatMinutesToHours(absDiff)}"
                    }

                    val resolvedSummary = (summary ?: MonthlyAttendanceSummary(
                        month = month,
                        workingDays = dynamicWorkingDays,
                        presentDays = resolvedPresentDays,
                        absentDays = dynamicAbsentDays,
                        incompleteDays = 0,
                        leaveDays = dynamicLeaveDays,
                        totalHours = totalActiveHoursStr,
                        totalWorkedMinutes = totalMinutes,
                        averageHours = dynamicAverageHoursStr,
                        targetHoursFormatted = "${dynamicWorkingDays * 8}h",
                        presentPercent = dynamicPresentPercent,
                        overtimeFormatted = dynamicOvertimeFormatted,
                        differenceFormatted = dynamicDifferenceFormatted
                    )).copy(
                        workingDays = dynamicWorkingDays,
                        presentDays = resolvedPresentDays,
                        absentDays = dynamicAbsentDays,
                        leaveDays = dynamicLeaveDays,
                        totalHours = totalActiveHoursStr,
                        totalWorkedMinutes = totalMinutes,
                        averageHours = dynamicAverageHoursStr,
                        presentPercent = dynamicPresentPercent,
                        overtimeFormatted = dynamicOvertimeFormatted,
                        differenceFormatted = dynamicDifferenceFormatted
                    )

                    val actualHoursNeededStr = resolvedSummary.targetHoursFormatted?.takeIf { it.isNotBlank() } ?: "${dynamicWorkingDays * 8}h"

                    _uiState.value = MonthlyStatisticsUiState(
                        isLoading = false,
                        summary = resolvedSummary,
                        attendance = filteredAttendance,
                        totalActiveHoursStr = totalActiveHoursStr,
                        actualHoursNeededStr = actualHoursNeededStr,
                        selectedMonthRaw = month
                    )
                }
                .onFailure { error ->
                    android.util.Log.e("MonthlyStats", "API Response Error: ", error)
                    val isSessionExpired = (error as? MonthlyApiException)?.code.equals("invalid_session", ignoreCase = true) ||
                        error.message?.contains("session is invalid", ignoreCase = true) == true ||
                        error.message?.contains("session expired", ignoreCase = true) == true ||
                        error.message?.contains("invalid_session", ignoreCase = true) == true

                    if (isSessionExpired) {
                        authRepository.logout()
                    }

                    _uiState.value = MonthlyStatisticsUiState(
                        isLoading = false,
                        error = error.message ?: "Unable to load attendance.",
                        selectedMonthRaw = month
                    )
                }
        }
    }

    private fun calculateTotalActiveMinutes(
        summary: MonthlyAttendanceSummary?,
        attendance: List<MonthlyAttendanceDto>
    ): Int {
        var minutesFromDays = 0
        for (item in attendance) {
            val itemMinutes = item.workedMinutes ?: 0
            val isZeroHours = item.hours.isNullOrBlank() || item.hours == "0h 00m" || item.hours == "00:00"
            if (itemMinutes > 0) {
                minutesFromDays += itemMinutes
            } else if (!isZeroHours && !item.hours.isNullOrBlank()) {
                minutesFromDays += parseHoursStringToMinutes(item.hours)
            } else {
                val inStr = item.login ?: item.rawCheckIn
                val outStr = item.logout ?: item.rawCheckOut
                if (!inStr.isNullOrBlank()) {
                    if (!outStr.isNullOrBlank()) {
                        // Includes auto-checkout "06:00 PM" — treat it as a valid checkout time
                        minutesFromDays += calculateMinutesFromTimes(inStr, outStr, item.breakMinutes ?: 0)
                    } else if (item.date == com.skypass.skytrack.util.AttendanceDates.today()) {
                        minutesFromDays += calculateLiveMinutes(inStr, item.breakMinutes ?: 0)
                    } else {
                        minutesFromDays += calculatePastIncompleteMinutes(inStr, item.breakMinutes ?: 0)
                    }
                }
            }
        }

        val serverMinutes = summary?.totalWorkedMinutes ?: 0
        return maxOf(minutesFromDays, serverMinutes)
    }

    private fun formatMinutesToHours(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return "${h}h ${String.format(java.util.Locale.ENGLISH, "%02dm", m)}"
    }

    private fun calculatePastIncompleteMinutes(login: String, breakMinutes: Int): Int {
        try {
            if (login.contains("T")) {
                val inInstant = java.time.Instant.parse(login)
                val inZdt = inInstant.atZone(java.time.ZoneId.of("Asia/Kolkata"))
                val shiftEndZdt = inZdt.toLocalDate().atTime(18, 0).atZone(java.time.ZoneId.of("Asia/Kolkata"))
                val grossSeconds = Math.max(3600L, java.time.Duration.between(inInstant, shiftEndZdt.toInstant()).seconds)
                val grossMinutes = (grossSeconds / 60).toInt()
                return Math.max(0, grossMinutes - breakMinutes)
            }
            val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
            val inDate = timeFormat.parse(login)
            if (inDate != null) {
                val calIn = java.util.Calendar.getInstance().apply { time = inDate }
                val inHour = calIn.get(java.util.Calendar.HOUR_OF_DAY)
                val inMin = calIn.get(java.util.Calendar.MINUTE)
                val inTotalMin = inHour * 60 + inMin
                val endTotalMin = 18 * 60 // 18:00 IST
                val grossMinutes = Math.max(60, endTotalMin - inTotalMin)
                return Math.max(0, grossMinutes - breakMinutes)
            }
        } catch (_: Exception) {}
        return 0
    }

    private fun calculateLiveMinutes(login: String, breakMinutes: Int): Int {
        try {
            if (login.contains("T")) {
                val inInstant = java.time.Instant.parse(login)
                val diffSeconds = Math.max(0L, java.time.Duration.between(inInstant, java.time.Instant.now()).seconds)
                val grossMinutes = (diffSeconds / 60).toInt()
                return Math.max(0, grossMinutes - breakMinutes)
            }
            val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
            val inDate = timeFormat.parse(login)
            if (inDate != null) {
                val calNow = java.util.Calendar.getInstance()
                val calIn = java.util.Calendar.getInstance().apply {
                    time = inDate
                    set(java.util.Calendar.YEAR, calNow.get(java.util.Calendar.YEAR))
                    set(java.util.Calendar.MONTH, calNow.get(java.util.Calendar.MONTH))
                    set(java.util.Calendar.DAY_OF_MONTH, calNow.get(java.util.Calendar.DAY_OF_MONTH))
                }
                val diffMs = Math.max(0L, calNow.timeInMillis - calIn.timeInMillis)
                val grossMinutes = (diffMs / 60000).toInt()
                return Math.max(0, grossMinutes - breakMinutes)
            }
        } catch (_: Exception) {}
        return 0
    }

    private fun parseHoursStringToMinutes(hoursStr: String): Int {
        try {
            val hmMatch = Regex("""(\d+)h\s*(\d+)m""").find(hoursStr)
            if (hmMatch != null) {
                val h = hmMatch.groupValues[1].toIntOrNull() ?: 0
                val m = hmMatch.groupValues[2].toIntOrNull() ?: 0
                return (h * 60) + m
            }
            val colonParts = hoursStr.split(":")
            if (colonParts.size == 2) {
                val h = colonParts[0].trim().toIntOrNull() ?: 0
                val m = colonParts[1].trim().toIntOrNull() ?: 0
                return (h * 60) + m
            }
            val hOnlyMatch = Regex("""(\d+)\s*h""").find(hoursStr)
            if (hOnlyMatch != null) {
                return (hOnlyMatch.groupValues[1].toIntOrNull() ?: 0) * 60
            }
            val mOnlyMatch = Regex("""(\d+)\s*m""").find(hoursStr)
            if (mOnlyMatch != null) {
                return mOnlyMatch.groupValues[1].toIntOrNull() ?: 0
            }
        } catch (_: Exception) {}
        return 0
    }

    private fun calculateMinutesFromTimes(login: String, logout: String, breakMinutes: Int): Int {
        // Normalize a time string or ISO timestamp to minutes-since-midnight in IST
        fun toIstMinutes(s: String): Int? {
            // ISO-8601 timestamp e.g. "2026-10-13T07:18:00.000Z"
            if (s.contains('T') && s.contains('Z')) {
                return try {
                    val instant = java.time.Instant.parse(s)
                    val zdt = instant.atZone(java.time.ZoneId.of("Asia/Kolkata"))
                    zdt.hour * 60 + zdt.minute
                } catch (_: Exception) { null }
            }
            // "hh:mm a" e.g. "09:18 AM" / "06:00 PM"
            try {
                val fmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
                val d = fmt.parse(s) ?: return null
                val cal = java.util.Calendar.getInstance().apply { time = d }
                return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            } catch (_: Exception) {}
            // "HH:mm" e.g. "09:18"
            try {
                val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH)
                val d = fmt.parse(s) ?: return null
                val cal = java.util.Calendar.getInstance().apply { time = d }
                return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            } catch (_: Exception) {}
            return null
        }

        val inMins = toIstMinutes(login) ?: return 0
        val outMins = toIstMinutes(logout) ?: return 0
        val grossMinutes = Math.max(0, outMins - inMins)
        return Math.max(0, grossMinutes - breakMinutes)
    }
}
