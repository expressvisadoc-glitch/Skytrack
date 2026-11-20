package com.skypass.skytrack

import com.skypass.skytrack.data.reminders.ReminderRules
import com.skypass.skytrack.data.remote.AttendanceDto
import com.skypass.skytrack.data.remote.LeaveDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRulesTest {

    private fun ist(text: String): Instant = ZonedDateTime.of(
        java.time.LocalDateTime.parse(text), ReminderRules.OFFICE_ZONE
    ).toInstant()

    // 20 Nov 2026 is a Friday, 22 Nov a Sunday
    private val friday = LocalDate.parse("2026-11-20")
    private val saturday = LocalDate.parse("2026-11-21")
    private val monday = LocalDate.parse("2026-11-23")

    @Test
    fun shiftStart_readsTheServersShiftName() {
        assertEquals(LocalTime.of(9, 0), ReminderRules.shiftStart("General (09:00 - 18:00)"))
        assertEquals(LocalTime.of(10, 30), ReminderRules.shiftStart("Morning (10:30 – 19:30)"))
        assertEquals(LocalTime.of(7, 5), ReminderRules.shiftStart("7:05"))
        assertNull(ReminderRules.shiftStart("General"))
        assertNull(ReminderRules.shiftStart(""))
        assertNull(ReminderRules.shiftStart(null))
        assertNull(ReminderRules.shiftStart("Broken (25:00 - 18:00)"))
    }

    @Test
    fun nextCheckIn_is15MinutesBeforeTheShift_today() {
        val (date, at) = ReminderRules.nextCheckInReminder(ist("2026-11-20T07:00:00"), LocalTime.of(9, 0))
        assertEquals(friday, date)
        assertEquals(ist("2026-11-20T08:45:00"), at)
    }

    @Test
    fun nextCheckIn_movesToTomorrow_onceTodaysTimeHasPassed() {
        val (date, at) = ReminderRules.nextCheckInReminder(ist("2026-11-20T08:45:00"), LocalTime.of(9, 0))
        assertEquals(saturday, date)
        assertEquals(ist("2026-11-21T08:45:00"), at)
    }

    @Test
    fun nextCheckIn_afterAnEarlyDelivery_bookingFromThatSlotGivesTheNextDay() {
        // Delivered at 08:37 for today's 08:45 slot; the receiver books from 08:46
        val slot = ReminderRules.checkInReminderFor(friday, LocalTime.of(9, 0))
        assertEquals(ist("2026-11-20T08:45:00"), slot)
        val (date, _) = ReminderRules.nextCheckInReminder(slot.plusSeconds(60), LocalTime.of(9, 0))
        assertEquals(saturday, date)
    }

    @Test
    fun nextCheckIn_skipsSunday() {
        val (date, at) = ReminderRules.nextCheckInReminder(ist("2026-11-21T12:00:00"), LocalTime.of(9, 0))
        assertEquals(monday, date)
        assertEquals(ist("2026-11-23T08:45:00"), at)
    }

    @Test
    fun nextCheckIn_shiftJustAfterMidnight_remindsTheEveningBefore() {
        val (date, at) = ReminderRules.nextCheckInReminder(ist("2026-11-20T20:00:00"), LocalTime.of(0, 10))
        assertEquals(saturday, date)
        assertEquals(ist("2026-11-20T23:55:00"), at)
    }

    @Test
    fun nextCheckIn_usesOfficeTime_notThePhonesZone() {
        // 03:00 UTC is 08:30 in India: today's 08:45 reminder is still ahead
        val (date, at) = ReminderRules.nextCheckInReminder(Instant.parse("2026-11-20T03:00:00Z"), LocalTime.of(9, 0))
        assertEquals(friday, date)
        assertEquals(Instant.parse("2026-11-20T03:15:00Z"), at)
    }

    @Test
    fun breakReminder_is30MinutesAfterTheBreakStarted_inEveryServerFormat() {
        val expected = Instant.parse("2026-11-20T08:30:00Z")
        assertEquals(expected, ReminderRules.breakReminderAt("2026-11-20T08:00:00Z"))
        assertEquals(expected, ReminderRules.breakReminderAt("2026-11-20T08:00:00+00:00"))
        assertEquals(expected, ReminderRules.breakReminderAt("2026-11-20T08:00:00.000+00:00"))
        assertEquals(expected, ReminderRules.breakReminderAt("2026-11-20T13:30:00+05:30"))
        assertEquals(expected, ReminderRules.breakReminderAt("2026-11-20 08:00:00"))
        assertNull(ReminderRules.breakReminderAt(null))
        assertNull(ReminderRules.breakReminderAt("not a time"))
    }

    private fun remind(
        attendance: AttendanceDto? = null,
        holidays: List<LocalDate> = emptyList(),
        leaves: List<LeaveDto> = emptyList(),
        joiningDate: String? = null,
        date: LocalDate = friday
    ) = ReminderRules.shouldRemindCheckIn(date, attendance, holidays, leaves, joiningDate)

    @Test
    fun checkIn_remindsWhenNotCheckedIn() {
        assertTrue(remind())
        assertTrue(remind(attendance = AttendanceDto(date = "2026-11-20", login = null)))
    }

    @Test
    fun checkIn_skippedWhenAlreadyCheckedInThatDay() {
        assertFalse(remind(attendance = AttendanceDto(date = "2026-11-20", login = "2026-11-20T03:10:00Z")))
    }

    @Test
    fun checkIn_yesterdaysCachedRecordDoesNotCount() {
        assertTrue(remind(attendance = AttendanceDto(date = "2026-11-19", login = "2026-11-19T03:10:00Z")))
    }

    @Test
    fun checkIn_skippedOnSundayHolidayAndBeforeJoining() {
        assertFalse(remind(date = LocalDate.parse("2026-11-22")))
        assertFalse(remind(holidays = listOf(friday)))
        assertFalse(remind(joiningDate = "2026-11-23"))
        assertTrue(remind(joiningDate = "2026-11-20T00:00:00Z"))
    }

    @Test
    fun checkIn_skippedOnlyForApprovedLeaveCoveringTheDay() {
        val leave = LeaveDto(from = "2026-11-19", to = "2026-11-21", status = "approved")
        assertFalse(remind(leaves = listOf(leave)))
        assertFalse(remind(leaves = listOf(LeaveDto(from = "2026-11-20", to = null, status = "Approved"))))
        assertTrue(remind(leaves = listOf(leave.copy(status = "pending"))))
        assertTrue(remind(leaves = listOf(leave.copy(status = "rejected"))))
        assertTrue(remind(leaves = listOf(leave.copy(from = "2026-11-21", to = "2026-11-22"))))
    }

    @Test
    fun breakStillRunning_onlyForTheSameOpenBreak() {
        val start = "2026-11-20T08:00:00Z"
        assertTrue(ReminderRules.isStillOnBreak(AttendanceDto(breakStartedAt = "2026-11-20T08:00:00.000+00:00"), start))
        assertFalse(ReminderRules.isStillOnBreak(AttendanceDto(breakStartedAt = null), start))
        assertFalse(ReminderRules.isStillOnBreak(AttendanceDto(breakStartedAt = "2026-11-20T09:00:00Z"), start))
        assertFalse(ReminderRules.isStillOnBreak(AttendanceDto(breakStartedAt = start, logout = "2026-11-20T12:00:00Z"), start))
    }
}
