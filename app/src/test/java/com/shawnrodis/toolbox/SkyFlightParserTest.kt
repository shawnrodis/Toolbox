package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.tools.formatSkyTime
import com.shawnrodis.toolbox.tools.parseSkyFlight
import com.shawnrodis.toolbox.tools.skyDisplayTime
import com.shawnrodis.toolbox.tools.skyExtractCityName
import com.shawnrodis.toolbox.tools.skyFindCodeshareMatch
import com.shawnrodis.toolbox.tools.skyFlightProgressExact
import com.shawnrodis.toolbox.tools.skyParseFlightInstant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Verifies the Phase 2 SkyTrack parser against a sample Aviationstack
 * response constructed from exactly the fields skyFetchFlight reads in
 * the source HTML (airline.name/iata, aircraft.iata, flight_status,
 * departure/arrival .iata/.airport/.terminal/.gate/.scheduled/.estimated).
 * Live API behaviour is NOT covered here — that needs Shawn's own key
 * on a device.
 */
class SkyFlightParserTest {

    private val sample = JSONObject(
        """
        {
          "flight_date": "2026-10-22",
          "flight_status": "active",
          "departure": {
            "airport": "John F Kennedy International Airport",
            "iata": "jfk",
            "terminal": "8",
            "gate": "12",
            "scheduled": "2026-10-22T08:45:00+00:00",
            "estimated": "2026-10-22T08:50:00+00:00"
          },
          "arrival": {
            "airport": "Los Angeles International Airport",
            "iata": "LAX",
            "terminal": "4",
            "gate": "52B",
            "scheduled": "2026-10-22T11:55:00+00:00",
            "estimated": "2026-10-22T12:05:00+00:00"
          },
          "airline": { "name": "American Airlines", "iata": "AA" },
          "aircraft": { "iata": "B738" }
        }
        """.trimIndent()
    )

    @Test
    fun parsesAllCardFieldsLikeSource() {
        val f = parseSkyFlight("AA100", sample)
        assertEquals("AA100", f.flightIata)
        assertEquals("American Airlines", f.airlineName)
        assertEquals("AA", f.airlineIata)
        assertEquals("ACTIVE", f.status)
        assertEquals("B738", f.aircraft)
        // v1.3.12: progress is time-based, not the old fixed 55 for
        // "active" — this 2026-10-22 sample is long past arrival, so it
        // clamps to 100 at parse time.
        assertEquals(100, f.progressPct)
        assertEquals("JFK", f.depIata) // source uppercases the IATA codes
        assertEquals("John F Kennedy International Airport", f.depName)
        // No "city" field -> airport name with "International Airport" stripped.
        assertEquals("John F Kennedy", f.depCity)
        assertEquals("8", f.depTerminal)
        assertEquals("12", f.depGate)
        assertEquals("LAX", f.arrIata)
        assertEquals("Los Angeles", f.arrCity)
        assertEquals("4", f.arrTerminal)
        assertEquals("52B", f.arrGate)
        // Times are rendered in 24-hour "HH:mm" (device-local), never the raw ISO string.
        assertTrue(f.depTime.contains(":") && f.depTime != "--")
        assertTrue(f.arrTime.contains(":") && f.arrTime != "--")
        assertTrue(Regex("^\\d{2}:\\d{2}$").matches(f.depTime))
        assertTrue(Regex("^\\d{2}:\\d{2}$").matches(f.arrTime))
        assertEquals(
            "Live Aviationstack Stream: JFK -> LAX | Gate 12 to Gate 52B.",
            f.statusInsight
        )
    }

    @Test
    fun timeFormatFollowsToggleAndFallsBackForLegacySaves() {
        val iso = "2026-10-30T19:50:00+08:00"
        assertTrue(Regex("^\\d{2}:\\d{2}$").matches(formatSkyTime(iso, true)))
        assertTrue(Regex("^\\d{1,2}:\\d{2} [AP]M$").matches(formatSkyTime(iso, false)))
        assertEquals("--", formatSkyTime(null, true))
        // Pre-v1.3.1 saves carry no ISO: the stored string shows until re-tracked.
        assertEquals("07:50 PM", skyDisplayTime("", "07:50 PM", true))
        assertTrue(Regex("^\\d{2}:\\d{2}$").matches(skyDisplayTime(iso, "07:50 PM", true)))
    }

    @Test
    fun timeRendersAirportLocalDigitsNotDeviceTimezone() {
        // QR929, 2026-10-30: Google shows 11:50 PM Manila dep, 4:03 AM Doha arr.
        // The ISOs carry those wall-clock digits; rendering must keep them
        // whatever the device timezone is (v1.3.3: no device-tz conversion).
        assertEquals("11:50 PM", formatSkyTime("2026-10-30T23:50:00+08:00", false))
        assertEquals("23:50", formatSkyTime("2026-10-30T23:50:00+08:00", true))
        assertEquals("04:03 AM", formatSkyTime("2026-10-31T04:03:00+03:00", false))
        assertEquals("04:03", formatSkyTime("2026-10-31T04:03:00+03:00", true))
        // The served +00:00 form of the same departure digits renders identically.
        assertEquals("11:50 PM", formatSkyTime("2026-10-30T23:50:00+00:00", false))
        assertEquals("11:50 PM", skyDisplayTime("2026-10-30T23:50:00+08:00", "07:50 PM", false))
    }

    @Test
    fun progressIsTimeBasedNotStatusBased() {
        val landed = parseSkyFlight("AA100", JSONObject(sample.toString()).put("flight_status", "landed"))
        assertEquals(100, landed.progressPct)
        val cancelled = parseSkyFlight("AA100", JSONObject(sample.toString()).put("flight_status", "cancelled"))
        assertEquals(0, cancelled.progressPct)
        val noStatus = JSONObject(sample.toString())
        noStatus.remove("flight_status")
        val missing = parseSkyFlight("AA100", noStatus)
        assertEquals("SCHEDULED", missing.status) // source default

        // QR929 served form: wall digits with +00:00 offsets, timezone
        // fields carry the truth (23:50 Manila -> 04:03 Doha = 9h13).
        val qr929Mid = Instant.parse("2026-10-30T20:26:30Z").toEpochMilli()
        assertEquals(
            50f,
            skyFlightProgressExact(
                "ACTIVE", "2026-10-30T23:50:00+00:00", "Asia/Manila",
                "2026-10-31T04:03:00+00:00", "Asia/Qatar", qr929Mid
            ),
            0.1f
        )
        assertEquals(
            Instant.parse("2026-10-30T15:50:00Z"),
            skyParseFlightInstant("2026-10-30T23:50:00+00:00", "Asia/Manila")
        )
        // DOH -> PHL, offset-only fallback (no timezone supplied).
        val dohPhlMid = Instant.parse("2026-10-31T12:17:30Z").toEpochMilli()
        assertEquals(
            50f,
            skyFlightProgressExact(
                "ACTIVE", "2026-10-31T08:00:00+03:00", "",
                "2026-10-31T15:35:00-04:00", "", dohPhlMid
            ),
            0.1f
        )
        // Clamps: before departure 0, after arrival 100.
        assertEquals(
            0f,
            skyFlightProgressExact(
                "ACTIVE", "2026-10-31T08:00:00+03:00", "",
                "2026-10-31T15:35:00-04:00", "",
                Instant.parse("2026-10-31T04:00:00Z").toEpochMilli()
            ),
            0.1f
        )
        assertEquals(
            100f,
            skyFlightProgressExact(
                "ACTIVE", "2026-10-31T08:00:00+03:00", "",
                "2026-10-31T15:35:00-04:00", "",
                Instant.parse("2026-11-01T00:00:00Z").toEpochMilli()
            ),
            0.1f
        )
    }

    @Test
    fun fallbacksMatchSourceDefaults() {
        val f = parseSkyFlight("DL42", JSONObject("{}"))
        assertEquals("DL42", f.flightIata)
        assertEquals("Commercial Airline", f.airlineName)
        assertEquals("DL", f.airlineIata) // first 2 chars of the cleaned code
        assertEquals("Aircraft", f.aircraft)
        assertEquals("N/A", f.depIata)
        assertEquals("Departure Airport", f.depName)
        assertEquals("Arrival Airport", f.arrName)
        assertEquals("N/A", f.depCity)
        assertEquals("--", f.depTerminal)
        assertEquals("--", f.depGate)
        assertEquals("--", f.depTime)
        assertEquals("--", f.arrTime)
        assertEquals("Live Aviationstack Stream: N/A -> N/A | Gate -- to Gate --.", f.statusInsight)
    }

    @Test
    fun cityExtractionMatchesSource() {
        // endpoint.city wins when present.
        val withCity = JSONObject("""{"city": "New York", "airport": "Some Airport"}""")
        assertEquals("New York", skyExtractCityName(withCity, "N/A"))
        // Cleaning strips the source's regex words anywhere they appear.
        val dfw = JSONObject("""{"airport": "Dallas/Fort Worth International Airport"}""")
        assertEquals("Dallas/Fort Worth", skyExtractCityName(dfw, "N/A"))
        val regional = JSONObject("""{"airport": "Springfield Regional"}""")
        // The source regex has no word boundaries, so "Field" also matches
        // inside "Springfield" — JS yields "Spring" here too. Faithful.
        assertEquals("Spring", skyExtractCityName(regional, "N/A"))
        // Null endpoint / empty airport fall back.
        assertEquals("N/A", skyExtractCityName(null, "N/A"))
        assertEquals("JFK", skyExtractCityName(JSONObject("{}"), "JFK"))
    }

    @Test
    fun savedFlightJsonRoundTrips() {
        val f = parseSkyFlight("AA100", sample)
        val restored = com.shawnrodis.toolbox.tools.SkyFlight.fromJson(f.toJson())
        assertEquals(f, restored)
    }

    @Test
    fun codeshareMatchFindsMarketingCodeAndRejectsBareNumber() {
        // v1.3.13: AA5812 on 2026-10-31 is really PT5812 (Piedmont,
        // PHL -> RIC); another carrier's 5812 must never be accepted.
        val data = JSONArray(
            """
            [
              {"flight": {"number": "5812", "iata": "DL5812", "icao": "DAL5812", "codeshared": null},
               "flight_status": "scheduled",
               "departure": {"iata": "ATL"}, "arrival": {"iata": "MSY"},
               "airline": {"name": "Delta Air Lines", "iata": "DL"}},
              {"flight": {"number": "5812", "iata": "PT5812", "icao": "PDT5812",
                 "codeshared": {"airline_name": "American Airlines", "airline_iata": "AA",
                   "airline_icao": "AAL", "flight_number": "5812",
                   "flight_iata": "AA5812", "flight_icao": "AAL5812"}},
               "flight_status": "scheduled",
               "departure": {"iata": "PHL"}, "arrival": {"iata": "RIC"},
               "airline": {"name": "American Airlines", "iata": "AA"}}
            ]
            """.trimIndent()
        )
        val match = skyFindCodeshareMatch(data, "AA5812")
        assertNotNull(match)
        assertEquals("PT5812", match!!.optJSONObject("flight")?.optString("iata"))
        assertEquals("PHL", match.optJSONObject("departure")?.optString("iata"))
        // ICAO form of the same codeshare also matches.
        assertNotNull(skyFindCodeshareMatch(data, "AAL5812"))
        // An unrelated code must not match either record, even though
        // both carry flight number 5812.
        assertNull(skyFindCodeshareMatch(data, "XX9999"))
        // And a bare number with no codeshare at all matches nothing.
        val noCodeshare = JSONArray(
            """[{"flight": {"number": "5812", "iata": "DL5812", "icao": "DAL5812"}}]"""
        )
        assertNull(skyFindCodeshareMatch(noCodeshare, "AA5812"))
        // The operating record parses under its own code, so a later
        // refresh hits PT5812 directly without another fallback.
        val parsed = parseSkyFlight("PT5812", match)
        assertEquals("PT5812", parsed.flightIata)
        assertEquals("PHL", parsed.depIata)
        assertEquals("RIC", parsed.arrIata)
    }
}
