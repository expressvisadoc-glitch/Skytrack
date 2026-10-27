package com.skypass.skytrack

import com.skypass.skytrack.util.AttendanceDates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AttendanceDatesTest {

    @Test
    fun todayIsTheUtcDate() {
        // Matches the backend, which dates attendance rows with the UTC date
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(), AttendanceDates.today())
    }

    @Test
    fun todayIsIsoFormatted() {
        assert(Regex("""\d{4}-\d{2}-\d{2}""").matches(AttendanceDates.today()))
    }
}
