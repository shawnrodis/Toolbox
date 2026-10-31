package com.shawnrodis.toolbox.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shawnrodis.toolbox.data.AppSettings
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.ui.ToolCardHeader
import com.shawnrodis.toolbox.ui.copyToClipboard
import com.shawnrodis.toolbox.ui.scrollContentBottomPadding
import com.shawnrodis.toolbox.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Flight Tracker / SkyTrack — Phase 2, faithful port of the source's
 * tab-skytrack + sky* functions in the HTML app:
 *
 *  - Request (skyFetchFlight): GET
 *    https://api.aviationstack.com/v1/flights?access_key={key}&flight_iata={CODE}
 *    CODE = input trimmed, uppercased, all whitespace removed. Only data[0]
 *    is used. Errors, in the source's order: missing key ->
 *    "API Key required. Please set your Aviationstack key in Settings.";
 *    non-2xx -> "Aviationstack HTTP Error: {status}"; json.error ->
 *    error.message (or "Aviationstack API error."); no data ->
 *    "No flight records found for \"{code}\"."
 *  - KEY SECURITY: the source's skyGetApiKey() falls back to a key built
 *    into the HTML file. The native app deliberately does NOT reproduce
 *    that built-in key (an APK can be unpacked). Only the user-entered key
 *    from Settings (DataStore "toolbox_aviationstack_key") is ever used;
 *    with no key set, no request is made and the Settings hint is shown.
 *  - Card fields mirror skyRenderFlightCard: airline logo box (airline
 *    IATA), flight IATA, uppercased status badge, airline name, aircraft,
 *    progress row "depCity (depIata) — pct% — arrCity (arrIata)" + bar,
 *    Departure block (airport name, city, "Terminal / Gate", "Scheduled"),
 *    Arrival block (…, "Estimated"), and the statusInsight line under the
 *    "Aviationstack Cross-Reference / Live Stream" footer.
 *    progressPct: 55 when status is "active", 100 when "landed", else 0.
 *    The source uses NO delay fields (verified: zero "delay" references
 *    in the SkyTrack code), so none are shown.
 *  - Times (source: new Date(x).toLocaleTimeString([], {hour:'2-digit',
 *    minute:'2-digit', hour12:false})): departure time comes from
 *    departure.scheduled, arrival time from arrival.estimated, rendered in
 *    the offset the ISO string carries (airport-local wall-clock digits,
 *    matching Google) rather than converted to the device timezone
 *    (v1.3.3 fix). A "24-hour time" toggle in the tool (on by
 *    default, persisted in skytrack_mobile_state) switches the card, saved
 *    rows, and share text between 24-hour "HH:mm" and 12-hour "hh:mm a";
 *    anything missing/unparseable shows "--". The card's times each have a
 *    Copy button that copies the time exactly as shown, for pasting into
 *    time-conversion tools.
 *  - Saved flights + search history persist in DataStore under the
 *    source's own key, "skytrack_mobile_state", with the source's JSON
 *    shape {savedFlights: [flight…], searchHistory: [code…]}. History:
 *    on success only, unshift if new, capped at 5 (skyExecuteSearch).
 *    Save toggles a bookmark (skyInitTab handler); Share copies the
 *    source's summary sentence to the clipboard; "Refresh" re-fetches
 *    every saved flight in order, keeping the old snapshot on error
 *    (sky-refresh-saved-btn handler). The source's one-time legacy
 *    "apiKey" migration inside skyLoadState is intentionally skipped:
 *    it existed to rescue a key previously typed into the web tab, and
 *    there is no web state to migrate on a fresh native install.
 */

/** Flight model — field names match the object skyFetchFlight returns. */
data class SkyFlight(
    val flightIata: String,
    val airlineName: String,
    val airlineIata: String,
    val status: String,
    val aircraft: String,
    val progressPct: Int,
    val depIata: String,
    val depName: String,
    val depCity: String,
    val depTerminal: String,
    val depGate: String,
    val depTime: String,
    val depIso: String = "",
    val arrIata: String,
    val arrName: String,
    val arrCity: String,
    val arrTerminal: String,
    val arrGate: String,
    val arrTime: String,
    val arrIso: String = "",
    val statusInsight: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("flightIata", flightIata).put("airlineName", airlineName)
        .put("airlineIata", airlineIata).put("status", status)
        .put("aircraft", aircraft).put("progressPct", progressPct)
        .put("depIata", depIata).put("depName", depName).put("depCity", depCity)
        .put("depTerminal", depTerminal).put("depGate", depGate).put("depTime", depTime)
        .put("depIso", depIso)
        .put("arrIata", arrIata).put("arrName", arrName).put("arrCity", arrCity)
        .put("arrTerminal", arrTerminal).put("arrGate", arrGate).put("arrTime", arrTime)
        .put("arrIso", arrIso)
        .put("statusInsight", statusInsight)

    companion object {
        fun fromJson(o: JSONObject): SkyFlight = SkyFlight(
            flightIata = o.optString("flightIata"), airlineName = o.optString("airlineName"),
            airlineIata = o.optString("airlineIata"), status = o.optString("status"),
            aircraft = o.optString("aircraft"), progressPct = o.optInt("progressPct", 0),
            depIata = o.optString("depIata"), depName = o.optString("depName"),
            depCity = o.optString("depCity"), depTerminal = o.optString("depTerminal"),
            depGate = o.optString("depGate"), depTime = o.optString("depTime"),
            depIso = o.optString("depIso"),
            arrIata = o.optString("arrIata"), arrName = o.optString("arrName"),
            arrCity = o.optString("arrCity"), arrTerminal = o.optString("arrTerminal"),
            arrGate = o.optString("arrGate"), arrTime = o.optString("arrTime"),
            arrIso = o.optString("arrIso"),
            statusInsight = o.optString("statusInsight")
        )
    }
}

private fun JSONObject?.str(key: String): String? {
    if (this == null || isNull(key)) return null
    return optString(key).takeIf { it.isNotEmpty() }
}

/** Source skyExtractCityName(endpoint, fallbackCode), verbatim logic. */
fun skyExtractCityName(endpoint: JSONObject?, fallbackCode: String): String {
    if (endpoint == null) return fallbackCode
    endpoint.str("city")?.let { return it }
    endpoint.str("airport")?.let { airport ->
        val cleanAirport = airport
            .replace(Regex("International Airport|Airport|Field|Regional|Intercontinental", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleanAirport.isNotEmpty()) return cleanAirport
    }
    return fallbackCode
}

private val skyTime24Fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val skyTime12Fmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

/**
 * Formats an Aviationstack ISO time in the offset it carries — the
 * airport-local wall-clock digits, the way Google shows flight times —
 * NOT converted to the device timezone. [use24h] selects 24-hour "HH:mm"
 * (true) or 12-hour "hh:mm a" (false).
 * v1.3.3: device-timezone conversion made the card disagree with Google
 * (QR929 on 2026-10-30: a 23:50 departure served as +00:00 showed as
 * 07:50 PM Eastern instead of 11:50 PM); rendering the ISO's own offset
 * shows the digits exactly as served.
 */
fun formatSkyTime(iso: String?, use24h: Boolean = true): String {
    if (iso.isNullOrEmpty()) return "--"
    val fmt = if (use24h) skyTime24Fmt else skyTime12Fmt
    return try {
        OffsetDateTime.parse(iso).format(fmt)
    } catch (e: Exception) {
        try {
            Instant.parse(iso).atZone(ZoneOffset.UTC).format(fmt)
        } catch (e2: Exception) {
            try {
                LocalDateTime.parse(iso).format(fmt)
            } catch (e3: Exception) {
                "--"
            }
        }
    }
}

/**
 * The time a flight row actually shows. Flights tracked before v1.3.1 have no
 * stored ISO, so they fall back to the string saved at parse time until they
 * are re-tracked; every flight tracked since carries its ISO and follows the
 * toggle.
 */
fun skyDisplayTime(iso: String, stored: String, use24h: Boolean): String =
    if (iso.isNotEmpty()) formatSkyTime(iso, use24h) else stored

/** Maps one Aviationstack data[0] object to the card model, as skyFetchFlight does. */
fun parseSkyFlight(cleanCode: String, raw: JSONObject): SkyFlight {
    val statusLower = (raw.str("flight_status") ?: "scheduled").lowercase(Locale.US)
    val progressPct = when (statusLower) {
        "active" -> 55
        "landed" -> 100
        else -> 0
    }
    val dep = raw.optJSONObject("departure")
    val arr = raw.optJSONObject("arrival")
    val airline = raw.optJSONObject("airline")
    val aircraftObj = raw.optJSONObject("aircraft")
    val depIata = (dep.str("iata") ?: "N/A").uppercase(Locale.US)
    val arrIata = (arr.str("iata") ?: "N/A").uppercase(Locale.US)
    return SkyFlight(
        flightIata = cleanCode,
        airlineName = airline.str("name") ?: "Commercial Airline",
        airlineIata = airline.str("iata") ?: cleanCode.take(2),
        status = (raw.str("flight_status") ?: "SCHEDULED").uppercase(Locale.US),
        aircraft = aircraftObj.str("iata") ?: "Aircraft",
        progressPct = progressPct,
        depIata = depIata,
        depName = dep.str("airport") ?: "Departure Airport",
        depCity = skyExtractCityName(dep, "N/A"),
        depTerminal = dep.str("terminal") ?: "--",
        depGate = dep.str("gate") ?: "--",
        depTime = formatSkyTime(dep.str("scheduled")),
        depIso = dep.str("scheduled") ?: "",
        arrIata = arrIata,
        arrName = arr.str("airport") ?: "Arrival Airport",
        arrCity = skyExtractCityName(arr, "N/A"),
        arrTerminal = arr.str("terminal") ?: "--",
        arrGate = arr.str("gate") ?: "--",
        arrTime = formatSkyTime(arr.str("estimated")),
        arrIso = arr.str("estimated") ?: "",
        statusInsight = "Live Aviationstack Stream: $depIata -> $arrIata | " +
            "Gate ${dep.str("gate") ?: "--"} to Gate ${arr.str("gate") ?: "--"}."
    )
}

/** Source skyFetchFlight — blocking; call from an IO context. Throws with the source's messages. */
fun fetchSkyFlight(apiKeyRaw: String, flightCode: String): SkyFlight {
    val cleanCode = flightCode.trim().uppercase(Locale.US).replace(Regex("\\s+"), "")
    val apiKey = apiKeyRaw.trim()
    if (apiKey.isEmpty()) {
        throw Exception("API Key required. Please set your Aviationstack key in Settings.")
    }
    val url = URL(
        "https://api.aviationstack.com/v1/flights?access_key=" +
            URLEncoder.encode(apiKey, "UTF-8") +
            "&flight_iata=" + URLEncoder.encode(cleanCode, "UTF-8")
    )
    val text = try {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        try {
            // Source order: HTTP status is checked before the body is parsed.
            if (conn.responseCode !in 200..299) {
                throw Exception("Aviationstack HTTP Error: ${conn.responseCode}")
            }
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    } catch (e: IOException) {
        // Source surfaces a bare fetch failure as a toast; native wording.
        throw Exception("Failed to fetch flight data. Check your connection and try again.")
    }
    val json = try {
        JSONObject(text)
    } catch (e: Exception) {
        throw Exception("Aviationstack API error.")
    }
    if (!json.isNull("error")) {
        val err = json.optJSONObject("error")
        throw Exception(err?.str("message") ?: "Aviationstack API error.")
    }
    val data = json.optJSONArray("data")
    if (data == null || data.length() == 0) {
        throw Exception("No flight records found for \"$cleanCode\".")
    }
    return parseSkyFlight(cleanCode, data.getJSONObject(0))
}

// Status badge colours in the source are a fixed emerald style for every status.
private val SkyBadgeBg = Color(0x3322C55E)
private val SkyBadgeFg = Color(0xFF16A34A)

@Composable
private fun SkyStatusBadge(status: String) {
    Surface(shape = RoundedCornerShape(50), color = SkyBadgeBg) {
        Text(
            status,
            color = SkyBadgeFg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/**
 * Full-screen host for the Flight Tracker around [FlightTrackerContent].
 * Desktop Mode hosts the content directly inside a draggable window, so
 * the content composable stays window-agnostic: it takes a [Modifier]
 * from its host and keeps all persistent state in the shared [AppStore]
 * (DataStore "skytrack_mobile_state"), never in activity-scoped state.
 * v1.0.1 (defect #4c): no Material TopAppBar — v1.3.7's bottom bar
 * frames the tab, as in the source; [onBack] is retained for the
 * shared call-site signature.
 */
@Composable
fun FlightTrackerScreen(store: AppStore, onBack: () -> Unit) {
    FlightTrackerContent(store, Modifier.fillMaxSize())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightTrackerContent(store: AppStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = AppSettings())
    val stateJson by store.stringState("skytrack_mobile_state", "").collectAsState(initial = "")

    // skyLoadState(): defaults are empty lists when nothing is stored.
    val savedFlights: List<SkyFlight> = remember(stateJson) {
        try {
            val a = JSONObject(stateJson.ifEmpty { "{}" }).optJSONArray("savedFlights")
            if (a != null) List(a.length()) { SkyFlight.fromJson(a.getJSONObject(it)) } else emptyList()
        } catch (e: Exception) { emptyList() }
    }
    val searchHistory: List<String> = remember(stateJson) {
        try {
            val a = JSONObject(stateJson.ifEmpty { "{}" }).optJSONArray("searchHistory")
            if (a != null) List(a.length()) { a.optString(it) }.filter { it.isNotEmpty() } else emptyList()
        } catch (e: Exception) { emptyList() }
    }
    // 24-hour toggle, on by default; absent in pre-v1.3.1 state, hence optBoolean's default.
    val use24h: Boolean = remember(stateJson) {
        try {
            JSONObject(stateJson.ifEmpty { "{}" }).optBoolean("use24h", true)
        } catch (e: Exception) { true }
    }

    var input by remember { mutableStateOf("") }
    var currentFlight by remember { mutableStateOf<SkyFlight?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    /** skySaveState(): persists {savedFlights, searchHistory, use24h}. */
    fun saveState(saved: List<SkyFlight>, history: List<String>, use24hValue: Boolean) {
        scope.launch {
            val flightsArr = JSONArray()
            saved.forEach { flightsArr.put(it.toJson()) }
            val histArr = JSONArray()
            history.forEach { histArr.put(it) }
            store.setStringState(
                "skytrack_mobile_state",
                JSONObject().put("savedFlights", flightsArr).put("searchHistory", histArr)
                    .put("use24h", use24hValue).toString()
            )
        }
    }

    /** window.skyExecuteSearch — also used by recents chips and saved-flight refresh. */
    fun executeSearch(code: String) {
        if (code.isBlank() || loading) return
        loading = true
        scope.launch {
            try {
                val flight = withContext(Dispatchers.IO) {
                    fetchSkyFlight(settings.aviationstackKey, code)
                }
                currentFlight = flight
                if (!searchHistory.contains(flight.flightIata)) {
                    saveState(savedFlights, (listOf(flight.flightIata) + searchHistory).take(5), use24h)
                }
                statusMessage = "Loaded live data for ${flight.flightIata}"
                statusIsError = false
                toast(context, "Loaded live data for ${flight.flightIata}")
                scrollState.animateScrollTo(0)
            } catch (e: Exception) {
                val msg = e.message ?: "Aviationstack API error."
                statusMessage = msg
                statusIsError = true
                toast(context, msg)
            } finally {
                loading = false
            }
        }
    }

    /** sky-refresh-saved-btn handler: re-fetch each, keep old on error. */
    fun refreshSaved() {
        if (savedFlights.isEmpty() || refreshing) return
        refreshing = true
        scope.launch {
            val key = settings.aviationstackKey
            val updated = savedFlights.map { saved ->
                try {
                    withContext(Dispatchers.IO) { fetchSkyFlight(key, saved.flightIata) }
                } catch (e: Exception) {
                    saved
                }
            }
            saveState(updated, searchHistory, use24h)
            refreshing = false
            toast(context, "Refreshed saved flights")
        }
    }

    /** v1.3.11: pull-to-refresh — the displayed flight when one is
        shown (and its saved copy, if it has one), else the saved list;
        with neither, a toast and no spinner hang. */
    fun onPullRefresh() {
        if (loading || refreshing) return
        val target = currentFlight
        if (target != null) {
            refreshing = true
            scope.launch {
                try {
                    val fresh = withContext(Dispatchers.IO) {
                        fetchSkyFlight(settings.aviationstackKey, target.flightIata)
                    }
                    currentFlight = fresh
                    if (savedFlights.any { it.flightIata == target.flightIata }) {
                        saveState(
                            savedFlights.map { if (it.flightIata == target.flightIata) fresh else it },
                            searchHistory, use24h
                        )
                    }
                    statusMessage = "Refreshed live data for ${fresh.flightIata}"
                    statusIsError = false
                    toast(context, "Refreshed live data for ${fresh.flightIata}")
                } catch (e: Exception) {
                    val msg = e.message ?: "Aviationstack API error."
                    statusMessage = msg
                    statusIsError = true
                    toast(context, msg)
                } finally {
                    refreshing = false
                }
            }
        } else if (savedFlights.isNotEmpty()) {
            refreshSaved()
        } else {
            toast(context, "Nothing to refresh yet — track a flight first")
        }
    }

    val current = currentFlight
    val currentIsSaved = current != null && savedFlights.any { it.flightIata == current.flightIata }

        // v1.3.11: swipe down to refresh — the standard Material3 pull
        // indicator over the tracker's scroll content. The pull
        // refreshes the displayed flight when one is shown, else the
        // saved list (same as the Refresh button, which stays as the
        // tap path). The box owns the outer modifier; the column keeps
        // its own padding and scroll.
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { onPullRefresh() },
            modifier = modifier
        ) {
        Column(
            modifier = Modifier
                // v1.3.7: bottom = breathing gap only; the Scaffold
                // bottomBar slot pads content above the bar.
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = scrollContentBottomPadding())
                .verticalScroll(scrollState)
        ) {
            // v1.0.1 (defect #4): the source's sky-header block — plane
            // icon box, "Flight Tracker" title, "Aviationstack Telemetry"
            // sub-line, and the Share/JPG/PDF trio (was a bottom ExportBar).
            ToolCardHeader(
                faIcon = "fa-plane-departure",
                title = "Flight Tracker",
                description = "Aviationstack Telemetry",
                fileBase = "flight-tracker",
                prettyName = "Flight tracker",
                exportHeader = "Flight Tracker",
                lines = if (current != null) listOf(
                    "Flight ${current.flightIata} (${current.airlineName})",
                    "${current.depCity} (${current.depIata}) -> ${current.arrCity} (${current.arrIata})",
                    "Status: ${current.status}",
                    "Scheduled: ${skyDisplayTime(current.depIso, current.depTime, use24h)} · Estimated: ${skyDisplayTime(current.arrIso, current.arrTime, use24h)}"
                ) else listOf("No flight loaded."),
                shareText = if (current != null)
                    "Flight ${current.flightIata} (${current.airlineName}): " +
                        "${current.depCity} (${current.depIata}) -> ${current.arrCity} (${current.arrIata}). " +
                        "Status: ${current.status}. Scheduled: ${skyDisplayTime(current.depIso, current.depTime, use24h)}."
                else ""
            )

            if (settings.aviationstackKey.isBlank()) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Aviationstack key needed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add your Aviationstack key in Settings to track flights. " +
                                "The native app ships with no built-in key — searches won't " +
                                "call the API until your key is saved on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Flight #") },
                    placeholder = { Text("Enter Flight # (e.g. AA100)...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { executeSearch(input) }),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { executeSearch(input) }, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(end = 8.dp).size(18.dp)
                        )
                    }
                    Text(if (loading) "Fetching..." else "Track Flight")
                }
            }

            // 24-hour toggle (v1.3.1): tool-scoped, persisted in skytrack_mobile_state.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text(
                    "24-hour time",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = use24h,
                    onCheckedChange = { saveState(savedFlights, searchHistory, it) }
                )
            }

            statusMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Recent searches (skyRenderSearchHistory)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            ) {
                Text("Recent:", style = MaterialTheme.typography.titleSmall)
                if (searchHistory.isEmpty()) {
                    Text(
                        "None",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    searchHistory.forEach { code ->
                        AssistChip(onClick = { input = code; executeSearch(code) }, label = { Text(code) })
                    }
                    TextButton(onClick = {
                        // skyClearRecents()
                        saveState(savedFlights, emptyList(), use24h)
                        toast(context, "Cleared search history")
                    }) { Text("Clear") }
                }
            }

            // Flight details card (skyRenderFlightCard)
            if (current != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text(
                        "Aviationstack Live Telemetry",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        // Bookmark toggle (skyInitTab sky-bookmark-btn handler)
                        if (currentIsSaved) {
                            saveState(
                                savedFlights.filterNot { it.flightIata == current.flightIata },
                                searchHistory, use24h
                            )
                            toast(context, "Removed ${current.flightIata}")
                        } else {
                            saveState(savedFlights + current, searchHistory, use24h)
                            toast(context, "Saved flight ${current.flightIata}")
                        }
                    }) { Text(if (currentIsSaved) "Saved" else "Save") }
                    OutlinedButton(onClick = {
                        // Share (skyInitTab sky-share-btn handler): copies the summary.
                        val f = current
                        val summary = "Flight ${f.flightIata} (${f.airlineName}): " +
                            "${f.depCity} (${f.depIata}) -> ${f.arrCity} (${f.arrIata}). " +
                            "Status: ${f.status}. Scheduled: ${skyDisplayTime(f.depIso, f.depTime, use24h)}."
                        copyToClipboard(context, summary, "Summary copied to clipboard!")
                    }) { Text("Share") }
                }

                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    current.airlineIata,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        current.flightIata,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                    SkyStatusBadge(current.status)
                                }
                                Text(
                                    current.airlineName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "AIRCRAFT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(current.aircraft, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) {
                            Text("${current.depCity} (${current.depIata})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "${current.progressPct}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("${current.arrCity} (${current.arrIata})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold)
                        }
                        LinearProgressIndicator(
                            progress = { current.progressPct / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )

                        SkyEndpointBlock(
                            title = "Departure",
                            iata = current.depIata, name = current.depName, city = current.depCity,
                            terminal = current.depTerminal, gate = current.depGate,
                            timeLabel = "Scheduled",
                            time = skyDisplayTime(current.depIso, current.depTime, use24h),
                            onCopyTime = { copyToClipboard(context, it, "Departure time copied to clipboard!") }
                        )
                        SkyEndpointBlock(
                            title = "Arrival",
                            iata = current.arrIata, name = current.arrName, city = current.arrCity,
                            terminal = current.arrTerminal, gate = current.arrGate,
                            timeLabel = "Estimated",
                            time = skyDisplayTime(current.arrIso, current.arrTime, use24h),
                            onCopyTime = { copyToClipboard(context, it, "Arrival time copied to clipboard!") }
                        )

                        Text(
                            "Aviationstack Cross-Reference · Live Stream",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                        Text(current.statusInsight, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Saved flights (skyRenderSavedFlights)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) {
                Text("Saved Flights", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        "${savedFlights.size} Saved",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                OutlinedButton(
                    enabled = savedFlights.isNotEmpty() && !refreshing,
                    onClick = { refreshSaved() }
                ) { Text(if (refreshing) "Refreshing..." else "Refresh") }
            }

            if (savedFlights.isEmpty()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("No saved flights", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Track a flight and click \"Save\" to keep tabs here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    savedFlights.forEach { flight ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            flight.airlineIata,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(flight.flightIata, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                                        Text(
                                            flight.airlineName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    SkyStatusBadge(flight.status)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                                ) {
                                    Column {
                                        Text(flight.depIata, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
                                        Text(skyDisplayTime(flight.depIso, flight.depTime, use24h), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    LinearProgressIndicator(
                                        progress = { flight.progressPct / 100f },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(flight.arrIata, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
                                        Text(skyDisplayTime(flight.arrIso, flight.arrTime, use24h), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    TextButton(onClick = {
                                        // skyViewSavedFlight: show the stored snapshot, scroll to top.
                                        currentFlight = flight
                                        scope.launch { scrollState.animateScrollTo(0) }
                                    }) { Text("Details") }
                                    TextButton(onClick = {
                                        // skyRemoveSavedFlight
                                        saveState(
                                            savedFlights.filterNot { it.flightIata == flight.flightIata },
                                            searchHistory, use24h
                                        )
                                        toast(context, "Removed ${flight.flightIata}")
                                    }) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }
        }
        } // PullToRefreshBox
}

@Composable
private fun SkyEndpointBlock(
    title: String,
    iata: String,
    name: String,
    city: String,
    terminal: String,
    gate: String,
    timeLabel: String,
    time: String,
    onCopyTime: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    iata,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(city, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Column {
                Text("Terminal / Gate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$terminal / $gate", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(time, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (time != "--") {
                    TextButton(
                        onClick = { onCopyTime(time) },
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Copy", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
