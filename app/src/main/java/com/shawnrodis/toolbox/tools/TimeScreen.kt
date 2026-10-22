package com.shawnrodis.toolbox.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.data.WORLD_TIME_ZONES
import com.shawnrodis.toolbox.ui.ToolCardHeader
import com.shawnrodis.toolbox.ui.ToolHeaderButton
import com.shawnrodis.toolbox.ui.PickerField
import com.shawnrodis.toolbox.ui.ToolScreen
import com.shawnrodis.toolbox.ui.toast
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Time Converter — port of the source behaviour:
 *  - a single HH:mm (24-hour clock value, as <input type=time> yields) shown
 *    formatted both ways by updateTimeDisplays(): "hh:mm AM/PM" and "HH:mm (24-Hour)"
 *  - zone picker (WORLD_TIME_ZONES labels); selecting a zone adds it to the
 *    tracked list (source onTimeZoneSelected -> addCurrentZoneToTracked, deduped)
 *  - "Current" loads the current time in the selected zone (source setCurrentTime,
 *    Intl.DateTimeFormat with timeZone + hour12:false)
 *  - tracked times render the live time per zone (source renderTrackedTimes,
 *    'en-US' hour12 format) and persist in toolbox_tracked_times.
 */
@Composable
fun TimeScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateJson by store.stringState("toolbox_time_state").collectAsState(initial = "")
    val trackedJson by store.stringState("toolbox_tracked_times", "[]").collectAsState(initial = "[]")

    var zone by remember { mutableStateOf("America/New_York") }
    var time24 by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    // Recompute tracked clocks every 30s while the screen is open.
    var tick by remember { mutableStateOf(0) }

    val tracked: List<com.shawnrodis.toolbox.data.TimeZoneInfo> = remember(trackedJson) {
        try {
            val a = JSONArray(trackedJson.ifEmpty { "[]" })
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                com.shawnrodis.toolbox.data.TimeZoneInfo(o.optString("country"), o.optString("zone"), o.optString("label"))
            }.filter { it.zone.isNotEmpty() }
        } catch (e: Exception) { emptyList() }
    }

    fun saveState() {
        scope.launch {
            store.setStringState(
                "toolbox_time_state",
                JSONObject().put("zone", zone).put("time12", time24).put("time24", time24).toString()
            )
        }
    }

    fun saveTracked(list: List<com.shawnrodis.toolbox.data.TimeZoneInfo>) {
        scope.launch {
            val a = JSONArray()
            list.forEach { t -> a.put(JSONObject().put("country", t.country).put("zone", t.zone).put("label", t.label)) }
            store.setStringState("toolbox_tracked_times", a.toString())
        }
    }

    LaunchedEffect(stateJson) {
        if (!loaded && stateJson.isNotEmpty()) {
            try {
                val s = JSONObject(stateJson)
                zone = s.optString("zone", zone).ifEmpty { zone }
                time24 = s.optString("time24", "")
            } catch (e: Exception) { }
            loaded = true
        }
    }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(30000); tick++ }
    }

    // updateTimeDisplays() formatting, verbatim rules.
    val parsed = remember(time24) {
        val parts = time24.trim().split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull(); val m = parts[1].toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) h to m else null
        } else null
    }
    val display12 = parsed?.let { (h, m) ->
        val ampm = if (h >= 12) "PM" else "AM"
        var h12 = h % 12; if (h12 == 0) h12 = 12
        String.format(Locale.US, "%02d:%02d %s", h12, m, ampm)
    } ?: "--:--"
    val zoneLabel = WORLD_TIME_ZONES.find { it.zone == zone }?.label ?: zone

    // v1.0.1 (defects #4/#5): source layout — card header (trio + the
    // "Current" fa-clock button AFTER the trio, as in tab-time's header),
    // one shared label over the (12h input | zone select) pair (7/12 + 5/12
    // per the source grid), the "24-Hour Time (HH:MM)" field, then the
    // Formatted 12h / 24h / Selected Region results box. The 12h field is
    // an editable "hh:mm AM/PM" text (the source's <input type=time> shows
    // 12h format; its value still drives the same single time state).
    var time12Text by remember { mutableStateOf("") }
    fun parse12(text: String): String? {
        val m = Regex("""^\s*(\d{1,2}):(\d{2})\s*([AaPp][Mm])?\s*$""").find(text.trim()) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val ap = m.groupValues[3].uppercase()
        if (ap == "PM" && h < 12) h += 12
        if (ap == "AM" && h == 12) h = 0
        if (h !in 0..23 || min !in 0..59) return null
        return String.format(Locale.US, "%02d:%02d", h, min)
    }
    LaunchedEffect(time24) {
        if (time24.isNotEmpty() && parse12(time12Text) != time24) time12Text = display12
        if (time24.isEmpty()) time12Text = ""
    }
    fun setCurrentTime() {
        // Source: setCurrentTime() — now in the selected zone, hour12:false HH:mm.
        try {
            val now = ZonedDateTime.now(ZoneId.of(zone))
            time24 = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            saveState()
            toast(context, "Loaded current regional time.")
        } catch (e: Exception) { toast(context, "Could not load time for that zone.") }
    }

    ToolScreen(title = "12 / 24-Hour Time Converter", onBack = onBack) {
        ToolCardHeader(
            faIcon = "fa-clock",
            title = "12 / 24-Hour Time Converter",
            description = "Convert between 12h & 24h across time zones",
            fileBase = "time-converter",
            prettyName = "Time converter",
            exportHeader = "12 / 24-Hour Time Converter",
            lines = listOf(
                "12-Hour: $display12",
                "24-Hour: ${if (parsed != null) time24 else "--:--"}",
                zoneLabel
            ) + tracked.map { "${it.label} (${it.zone})" },
            shareText = if (time24.isEmpty()) "" else
                "Time conversion: $display12 = $time24 [$zone]",
            trailingActions = {
                ToolHeaderButton(
                    icon = Icons.Filled.Schedule,
                    contentDescription = "Use Current Time",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = "Current",
                    onClick = { setCurrentTime() }
                )
            }
        )
        com.shawnrodis.toolbox.ui.FieldLabel(
            "12-Hour Time & Time Zone / Country",
            Modifier.padding(top = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = time12Text,
                onValueChange = { v ->
                    time12Text = v
                    parse12(v)?.let { time24 = it; saveState() }
                },
                placeholder = { Text("hh:mm AM/PM") },
                singleLine = true,
                modifier = Modifier.weight(7f)
            )
            PickerField(
                null,
                WORLD_TIME_ZONES.map { it.label },
                zoneLabel,
                { label ->
                    val tz = WORLD_TIME_ZONES.find { it.label == label } ?: return@PickerField
                    zone = tz.zone
                    // Source: selecting a zone also tracks it (deduped by zone).
                    if (tracked.none { it.zone == tz.zone }) {
                        saveTracked(tracked + tz)
                        toast(context, "Added ${tz.country} to tracked times.")
                    }
                    saveState()
                },
                Modifier.weight(5f)
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            com.shawnrodis.toolbox.ui.FieldLabel("24-Hour Time (HH:MM)")
            OutlinedTextField(
                value = time24,
                onValueChange = { v ->
                    time24 = v.filter { it.isDigit() || it == ':' }.take(5)
                    saveState()
                },
                placeholder = { Text("HH:MM") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Source results box: Formatted 12h / Formatted 24h / Selected Region.
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Row {
                    Text("Formatted 12h: ", fontWeight = FontWeight.SemiBold)
                    Text(if (parsed != null) display12 else "-")
                }
                Row {
                    Text("Formatted 24h: ", fontWeight = FontWeight.SemiBold)
                    Text(if (parsed != null) time24 else "-")
                }
                Row {
                    Text("Selected Region: ", fontWeight = FontWeight.SemiBold)
                    Text(zoneLabel)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            Text("Tracked Times (${tracked.size})", style = MaterialTheme.typography.titleMedium)
            if (tracked.isNotEmpty()) {
                TextButton(onClick = { saveTracked(emptyList()); toast(context, "Cleared all tracked time zones.") }) {
                    Text("Clear All")
                }
            }
        }
        if (tracked.isEmpty()) {
            Text(
                "No country time zones tracked yet. Select a country above to add it to tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // `tick` is read here so the cards re-render on the 30s refresh.
        if (tick < 0) Text("")
        tracked.forEachIndexed { idx, item ->
            val live = try {
                ZonedDateTime.now(ZoneId.of(item.zone))
                    .format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
            } catch (e: Exception) { "--:--" }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        Text(item.zone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(live, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        saveTracked(tracked.filterIndexed { i, _ -> i != idx })
                        toast(context, "Removed tracked time zone.")
                    }) { Text("✕") }
                }
            }
        }
    }
}
