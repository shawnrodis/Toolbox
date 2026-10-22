package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.tools.parseSkyFlight
import com.shawnrodis.toolbox.tools.skyExtractCityName
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(55, f.progressPct) // status "active" -> 55 in the source
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
        // Times are rendered (device-local "hh:mm a"), never the raw ISO string.
        assertTrue(f.depTime.contains(":") && f.depTime != "--")
        assertTrue(f.arrTime.contains(":") && f.arrTime != "--")
        assertEquals(
            "Live Aviationstack Stream: JFK -> LAX | Gate 12 to Gate 52B.",
            f.statusInsight
        )
    }

    @Test
    fun progressFollowsSourceStatusRules() {
        val landed = parseSkyFlight("AA100", JSONObject(sample.toString()).put("flight_status", "landed"))
        assertEquals(100, landed.progressPct)
        val scheduled = parseSkyFlight("AA100", JSONObject(sample.toString()).put("flight_status", "scheduled"))
        assertEquals(0, scheduled.progressPct)
        val noStatus = JSONObject(sample.toString())
        noStatus.remove("flight_status")
        val missing = parseSkyFlight("AA100", noStatus)
        assertEquals("SCHEDULED", missing.status) // source default
        assertEquals(0, missing.progressPct)
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
}
