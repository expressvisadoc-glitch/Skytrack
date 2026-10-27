package com.skypass.skytrack.util

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Attendance records are dated with the UTC date (the backend's attendance functions use
 * current_date on a UTC database), so "today" for matching attendance records is the UTC date.
 * Times shown to the user are still converted to the device's local timezone.
 */
object AttendanceDates {
    fun today(): String = LocalDate.now(ZoneOffset.UTC).toString()
}
