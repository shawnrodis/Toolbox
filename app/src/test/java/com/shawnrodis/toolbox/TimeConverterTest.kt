package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.tools.convertTrackedTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TimeConverterTest {

    @Test
    fun convertsEnteredTimeAcrossZonesWithDst() {
        // 2026-10-30: Eastern is on daylight time (UTC-4).
        assertEquals(
            "11:03 AM",
            convertTrackedTime("04:03", "America/New_York", "Asia/Qatar", LocalDate.of(2026, 10, 30))
        )
        // 2026-01-15: Eastern is on standard time (UTC-5).
        assertEquals(
            "12:03 PM",
            convertTrackedTime("04:03", "America/New_York", "Asia/Qatar", LocalDate.of(2026, 1, 15))
        )
        // The QR929 arrival, reverse direction: 4:03 AM Doha -> 9:03 PM Eastern (EDT).
        assertEquals(
            "09:03 PM",
            convertTrackedTime("04:03", "Asia/Qatar", "America/New_York", LocalDate.of(2026, 10, 31))
        )
        // Same zone is identity.
        assertEquals(
            "04:03 AM",
            convertTrackedTime("04:03", "America/New_York", "America/New_York", LocalDate.of(2026, 10, 30))
        )
    }

    @Test
    fun invalidInputRendersPlaceholder() {
        val day = LocalDate.of(2026, 10, 30)
        assertEquals("--:--", convertTrackedTime("", "America/New_York", "Asia/Qatar", day))
        assertEquals("--:--", convertTrackedTime("25:00", "America/New_York", "Asia/Qatar", day))
        assertEquals("--:--", convertTrackedTime("04:03", "No/Such_Zone", "Asia/Qatar", day))
    }
}
