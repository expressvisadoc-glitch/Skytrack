package com.skypass.skytrack.data.reminders

import com.skypass.skytrack.data.remote.AttendanceDto
import com.skypass.skytrack.data.remote.LeaveDto
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * When the two on-phone reminders fire, and whether they should be shown. No Android code here,
 * so it can be unit tested.
 *
 * - Check-in: [CHECK_IN_LEAD_MINUTES] before the shift starts, on working days only.
 * - Break: once, when a break has run for [BREAK_LIMIT_MINUTES].
 */
object ReminderRules {
    /** Office time. Shift times and attendance dates are in this zone, whatever the phone is set to. */
    val OFFICE_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    const val CHECK_IN_LEAD_MINUTES = 15L
    const val BREAK_LIMIT_MINUTES = 30L

    private val timeInBrackets = Regex("""\(\s*(\d{1,2}):(\d{2})""")
    private val anyTime = Regex("""\b(\d{1,2}):(\d{2})\b""")

    /** The server sends the shift as "General (09:00 - 18:00)"; returns 09:00. */
    fun shiftStart(workShiftName: String?): LocalTime? {
        if (workShiftName.isNullOrBlank()) return null
        val match = timeInBrackets.find(workShiftName) ?: anyTime.find(workShiftName) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /** Sunday is the week-off, as on the server */
    fun isWeekOff(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.SUNDAY

    /** The next check-in reminder strictly after [now]: the shift's date and when to remind. */
    fun nextCheckInReminder(now: Instant, shiftStart: LocalTime): Pair<LocalDate, Instant> {
        val today = now.atZone(OFFICE_ZONE).toLocalDate()
        var day = today.minusDays(1) // a shift just after midnight is reminded the evening before
        while (true) {
            if (!isWeekOff(day)) {
                val remindAt = checkInReminderFor(day, shiftStart)
                if (remindAt.isAfter(now)) return day to remindAt
            }
            day = day.plusDays(1)
        }
    }

    /** When the check-in reminder for the shift on [shiftDate] is due */
    fun checkInReminderFor(shiftDate: LocalDate, shiftStart: LocalTime): Instant =
        shiftDate.atTime(shiftStart).atZone(OFFICE_ZONE).minusMinutes(CHECK_IN_LEAD_MINUTES).toInstant()

    fun breakReminderAt(breakStartedAt: String?): Instant? =
        parseInstant(breakStartedAt)?.plus(Duration.ofMinutes(BREAK_LIMIT_MINUTES))

    /**
     * Show the check-in reminder for [shiftDate] unless it is a day off, before the joining date,
     * a published holiday, approved leave, or the employee has already checked in that day.
     * Missing data (no network) never blocks the reminder.
     */
    fun shouldRemindCheckIn(
        shiftDate: LocalDate,
        attendance: AttendanceDto?,
        holidays: List<LocalDate>,
        leaves: List<LeaveDto>,
        joiningDate: String?
    ): Boolean {
        if (isWeekOff(shiftDate)) return false
        val joined = parseDate(joiningDate)
        if (joined != null && shiftDate.isBefore(joined)) return false
        if (shiftDate in holidays) return false
        val onLeave = leaves.any { leave ->
            val from = parseDate(leave.from)
            val to = parseDate(leave.to) ?: from
            leave.status.equals("approved", ignoreCase = true) &&
                from != null && to != null && !shiftDate.isBefore(from) && !shiftDate.isAfter(to)
        }
        if (onLeave) return false
        val checkedIn = attendance != null && !attendance.login.isNullOrBlank() && attendanceDate(attendance) == shiftDate
        return !checkedIn
    }

    /** True while the break that started at [breakStartedAt] is still running */
    fun isStillOnBreak(attendance: AttendanceDto, breakStartedAt: String): Boolean {
        if (!attendance.logout.isNullOrBlank()) return false
        val current = parseInstant(attendance.breakStartedAt) ?: return false
        val scheduled = parseInstant(breakStartedAt) ?: return true
        return current == scheduled
    }

    private fun attendanceDate(attendance: AttendanceDto): LocalDate? =
        parseDate(attendance.date) ?: parseInstant(attendance.login)?.atZone(OFFICE_ZONE)?.toLocalDate()

    private fun parseDate(value: String?): LocalDate? =
        value?.trim()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Accepts "…Z", "…+00:00" (Supabase) and a bare local timestamp, which is taken as UTC */
    fun parseInstant(value: String?): Instant? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() }?.replace(' ', 'T') ?: return null
        return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }.getOrNull()
    }
}
