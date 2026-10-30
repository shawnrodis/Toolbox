package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.tools.compareTimeRows
import com.shawnrodis.toolbox.tools.convertTime24
import com.shawnrodis.toolbox.tools.convertTrackedTime
import com.shawnrodis.toolbox.tools.convertTrackedTimeFull
import com.shawnrodis.toolbox.tools.zoneOffsetLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

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

    @Test
    fun fullConversionReportsDayShift() {
        // Qatar 04:03 on Oct 31 lands on Oct 30 evening in New York.
        val back = convertTrackedTimeFull("04:03", "Asia/Qatar", "America/New_York", LocalDate.of(2026, 10, 31))
        assertEquals("09:03 PM", back.text)
        assertEquals(-1, back.dayShift)
        // New York 23:00 on Oct 30 lands the next morning in Qatar.
        val forward = convertTrackedTimeFull("23:00", "America/New_York", "Asia/Qatar", LocalDate.of(2026, 10, 30))
        assertEquals("06:00 AM", forward.text)
        assertEquals(1, forward.dayShift)
        // Same-day conversion has no shift.
        val same = convertTrackedTimeFull("04:03", "America/New_York", "Asia/Qatar", LocalDate.of(2026, 10, 30))
        assertEquals("11:03 AM", same.text)
        assertEquals(0, same.dayShift)
    }

    @Test
    fun offsetLineNamesAheadZoneWithMinutes() {
        val at = OffsetDateTime.parse("2026-10-31T04:03:00+03:00").toInstant()
        assertEquals(
            "Qatar is 7 hours ahead of New York",
            zoneOffsetLine("Asia/Qatar", "America/New_York", at)
        )
        assertEquals(
            "Kathmandu is 9 hours 45 minutes ahead of New York",
            zoneOffsetLine("Asia/Kathmandu", "America/New_York", at)
        )
        assertEquals(
            "New York and New York share the same offset at this time",
            zoneOffsetLine("America/New_York", "America/New_York", at)
        )
    }

    @Test
    fun linkedRowConversionReturns24HourTime() {
        assertEquals(
            "11:03",
            convertTime24("04:03", "America/New_York", "Asia/Qatar", LocalDate.of(2026, 10, 30))
        )
        assertEquals(
            "21:03",
            convertTime24("04:03", "Asia/Qatar", "America/New_York", LocalDate.of(2026, 10, 31))
        )
        assertEquals("", convertTime24("", "America/New_York", "Asia/Qatar", LocalDate.of(2026, 10, 30)))
        assertEquals("", convertTime24("25:00", "America/New_York", "Asia/Qatar", LocalDate.of(2026, 10, 30)))
        assertEquals("", convertTime24("04:03", "No/Such_Zone", "Asia/Qatar", LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun compareRowsShowsBothDirectionsAndOffset() {
        val lines = compareTimeRows(
            "04:03", "Asia/Qatar", "04:03", "America/New_York", LocalDate.of(2026, 10, 31)
        )
        assertEquals(3, lines.size)
        assertEquals(
            "04:03 AM Qatar (Doha - UTC+3) = 09:03 PM USA (Eastern / New York - UTC-5) (previous day)",
            lines[0]
        )
        assertEquals(
            "04:03 AM USA (Eastern / New York - UTC-5) = 11:03 AM Qatar (Doha - UTC+3)",
            lines[1]
        )
        assertEquals("Qatar is 7 hours ahead of New York", lines[2])
    }
}
