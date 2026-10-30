package com.shawnrodis.toolbox.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
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
import com.shawnrodis.toolbox.ui.PickerField
import com.shawnrodis.toolbox.ui.ToolScreen
import com.shawnrodis.toolbox.ui.toast
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Converts an entered HH:mm time-of-day from [fromZone] to [toZone],
 * using [date] (today in the source zone by default) so DST is handled
 * by the real zone rules. v1.3.4: powers the Tracked Times "Converted"
 * mode; unparseable input renders "--:--".
 * v1.3.5: two input rows (Time 1 / Time 2, each with its own 12h input,
 * zone picker and 24h field), a Compare button that saves the pair and
 * shows each entered time converted into the other row's zone (with a
 * day-shift note when it crosses midnight) plus the offset between the
 * zones; the tracked list's converted mode now converts from Time 1.
 * Per-row Current/Track buttons replace the old header Current button
 * and the picker's auto-track behaviour. Old single-row states migrate
 * to Time 1.
 * v1.3.6: the rows are linked like the currency converter's base/target
 * fields — editing one row refills the other with the converted time,
 * and vice versa; empty clears the other row, invalid input leaves it
 * alone. Zone changes and Current also refill the other row.
 * Reframed as a conversion tool, not a comparison tool: Save conversion
 * appends the pair to a saved list (each entry shows both directions,
 * the day-shift note and the offset, and is deletable). Tracked Times
 * keep their live view.
 */
fun convertTrackedTime(time24: String, fromZone: String, toZone: String, date: LocalDate? = null): String =
    convertTrackedTimeFull(time24, fromZone, toZone, date).text

/** "HH:mm" for an entered time converted into [toZone], or "" when unparseable. */
fun convertTime24(time24: String, fromZone: String, toZone: String, date: LocalDate? = null): String {
    val src = zonedInstant(time24, fromZone, date) ?: return ""
    return try {
        src.withZoneSameInstant(ZoneId.of(toZone)).format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) { "" }
}

/** Rich result of convertTrackedTimeFull: rendered text plus day shift vs the source date. */
data class ConvertedTime(val text: String, val dayShift: Int)

/** One saved conversion: both rows' entered times and zones. */
data class SavedConversion(val timeA: String, val zoneA: String, val timeB: String, val zoneB: String)

fun convertTrackedTimeFull(time24: String, fromZone: String, toZone: String, date: LocalDate? = null): ConvertedTime {
    val src = zonedInstant(time24, fromZone, date) ?: return ConvertedTime("--:--", 0)
    return try {
        val dst = src.withZoneSameInstant(ZoneId.of(toZone))
        val shift = java.time.temporal.ChronoUnit.DAYS.between(src.toLocalDate(), dst.toLocalDate()).toInt()
        ConvertedTime(dst.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US)), shift)
    } catch (e: Exception) { ConvertedTime("--:--", 0) }
}

private fun zonedInstant(time24: String, zone: String, date: LocalDate?): java.time.ZonedDateTime? {
    val parts = time24.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return try {
        val z = ZoneId.of(zone)
        val day = date ?: LocalDate.now(z)
        LocalDateTime.of(day, LocalTime.of(h, m)).atZone(z)
    } catch (e: Exception) { null }
}

/** "hh:mm AM/PM" for an HH:mm value, or "--:--" when unparseable. */
fun time12Of(time24: String): String {
    val parts = time24.trim().split(":")
    if (parts.size != 2) return "--:--"
    val h = parts[0].toIntOrNull() ?: return "--:--"
    val m = parts[1].toIntOrNull() ?: return "--:--"
    if (h !in 0..23 || m !in 0..59) return "--:--"
    val ampm = if (h >= 12) "PM" else "AM"
    var h12 = h % 12
    if (h12 == 0) h12 = 12
    return String.format(Locale.US, "%02d:%02d %s", h12, m, ampm)
}

fun zoneLabelOf(zone: String): String =
    WORLD_TIME_ZONES.find { it.zone == zone }?.label ?: zone

/** "Qatar is 7 hours ahead of New York", from the zones' offsets at [at]; null when unparseable. */
fun zoneOffsetLine(zoneA: String, zoneB: String, at: Instant): String? {
    return try {
        val offA = ZoneId.of(zoneA).rules.getOffset(at)
        val offB = ZoneId.of(zoneB).rules.getOffset(at)
        val diffMin = (offA.totalSeconds - offB.totalSeconds) / 60
        fun city(zone: String) = zone.substringAfterLast('/').replace('_', ' ')
        if (diffMin == 0) return "${city(zoneA)} and ${city(zoneB)} share the same offset at this time"
        val abs = kotlin.math.abs(diffMin)
        val h = abs / 60
        val m = abs % 60
        val amount = buildString {
            append("$h hour")
            if (h != 1) append("s")
            if (m != 0) append(" $m minute" + if (m != 1) "s" else "")
        }
        if (diffMin > 0) "${city(zoneA)} is $amount ahead of ${city(zoneB)}"
        else "${city(zoneB)} is $amount ahead of ${city(zoneA)}"
    } catch (e: Exception) { null }
}

/**
 * Comparison lines for a saved pair: each entered time converted into the
 * other row's zone (with a day-shift note when it crosses midnight), plus
 * the offset between the zones at the first time's instant.
 */
fun compareTimeRows(timeA: String, zoneA: String, timeB: String, zoneB: String, date: LocalDate? = null): List<String> {
    val labelA = zoneLabelOf(zoneA)
    val labelB = zoneLabelOf(zoneB)
    val aToB = convertTrackedTimeFull(timeA, zoneA, zoneB, date)
    val bToA = convertTrackedTimeFull(timeB, zoneB, zoneA, date)
    fun suffix(shift: Int): String = when {
        shift == 1 -> " (next day)"
        shift == -1 -> " (previous day)"
        shift > 0 -> " ($shift days later)"
        shift < 0 -> " (${-shift} days earlier)"
        else -> ""
    }
    val lines = mutableListOf(
        "${time12Of(timeA)} $labelA = ${aToB.text} $labelB${suffix(aToB.dayShift)}",
        "${time12Of(timeB)} $labelB = ${bToA.text} $labelA${suffix(bToA.dayShift)}"
    )
    val instantA = zonedInstant(timeA, zoneA, date)?.toInstant()
    if (instantA != null) zoneOffsetLine(zoneA, zoneB, instantA)?.let { lines += it }
    return lines
}

@Composable
private fun TimeRow(
    title: String,
    time12Text: String,
    on12Change: (String) -> Unit,
    zoneLabel: String,
    onZoneLabel: (String) -> Unit,
    time24: String,
    on24Change: (String) -> Unit,
    onCurrent: () -> Unit,
    onTrack: () -> Unit
) {
    com.shawnrodis.toolbox.ui.FieldLabel(
        "$title — 12-Hour Time & Time Zone / Country",
        Modifier.padding(top = 8.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = time12Text,
            onValueChange = on12Change,
            placeholder = { Text("hh:mm AM/PM") },
            singleLine = true,
            modifier = Modifier.weight(7f)
        )
        PickerField(
            null,
            WORLD_TIME_ZONES.map { it.label },
            zoneLabel,
            onZoneLabel,
            Modifier.weight(5f)
        )
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        com.shawnrodis.toolbox.ui.FieldLabel("24-Hour Time (HH:MM)")
        OutlinedTextField(
            value = time24,
            onValueChange = on24Change,
            placeholder = { Text("HH:MM") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onCurrent) { Text("Current") }
        TextButton(onClick = onTrack) { Text("Track zone") }
    }
}

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
 *  - v1.3.4: a "Show converted time" switch above the tracked list flips
 *    the rows between live clocks (default) and the entered time converted
 *    from the selected region into each tracked zone.
 */
@Composable
fun TimeScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateJson by store.stringState("toolbox_time_state").collectAsState(initial = "")
    val trackedJson by store.stringState("toolbox_tracked_times", "[]").collectAsState(initial = "[]")

    var zoneA by remember { mutableStateOf("America/New_York") }
    var time24A by remember { mutableStateOf("") }
    var zoneB by remember { mutableStateOf("Asia/Qatar") }
    var time24B by remember { mutableStateOf("") }
    var trackedMode by remember { mutableStateOf("live") }
    // Saved conversions, set by Save conversion; restored on reopen.
    var savedConversions by remember { mutableStateOf<List<SavedConversion>>(emptyList()) }
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
            val state = JSONObject()
                .put("zoneA", zoneA).put("time24A", time24A)
                .put("zoneB", zoneB).put("time24B", time24B)
                .put("trackedMode", trackedMode)
            val savedArr = JSONArray()
            savedConversions.forEach { c ->
                savedArr.put(JSONObject().put("time24A", c.timeA).put("zoneA", c.zoneA).put("time24B", c.timeB).put("zoneB", c.zoneB))
            }
            state.put("savedConversions", savedArr)
            store.setStringState("toolbox_time_state", state.toString())
        }
    }

    fun saveTracked(list: List<com.shawnrodis.toolbox.data.TimeZoneInfo>) {
        scope.launch {
            val a = JSONArray()
            list.forEach { t -> a.put(JSONObject().put("country", t.country).put("zone", t.zone).put("label", t.label)) }
            store.setStringState("toolbox_tracked_times", a.toString())
        }
    }

    // Currency-style linking: editing one row refills the other with the
    // converted time; empty clears the other row; invalid input leaves it
    // alone. Callers save state afterwards.
    fun propagateFromA() {
        if (time24A.isEmpty()) time24B = ""
        else convertTime24(time24A, zoneA, zoneB).takeIf { it.isNotEmpty() }?.let { time24B = it }
    }
    fun propagateFromB() {
        if (time24B.isEmpty()) time24A = ""
        else convertTime24(time24B, zoneB, zoneA).takeIf { it.isNotEmpty() }?.let { time24A = it }
    }

    LaunchedEffect(stateJson) {
        if (!loaded && stateJson.isNotEmpty()) {
            try {
                val s = JSONObject(stateJson)
                // Pre-v1.3.5 states carry the single zone/time24: they become Time 1.
                zoneA = s.optString("zoneA", "").ifEmpty { s.optString("zone", "America/New_York") }.ifEmpty { "America/New_York" }
                time24A = s.optString("time24A", "").ifEmpty { s.optString("time24", "") }
                zoneB = s.optString("zoneB", "Asia/Qatar").ifEmpty { "Asia/Qatar" }
                time24B = s.optString("time24B", "")
                trackedMode = s.optString("trackedMode", "live").ifEmpty { "live" }
                val savedArr = s.optJSONArray("savedConversions")
                savedConversions = if (savedArr != null) {
                    buildList {
                        for (i in 0 until savedArr.length()) {
                            val o = savedArr.optJSONObject(i) ?: continue
                            add(SavedConversion(o.optString("time24A"), o.optString("zoneA"), o.optString("time24B"), o.optString("zoneB")))
                        }
                    }
                } else {
                    // Pre-reframe states carry a single saved comparison pair.
                    val a = s.optJSONObject("compareA")
                    val b = s.optJSONObject("compareB")
                    if (a != null && b != null) listOf(
                        SavedConversion(a.optString("time24"), a.optString("zone"), b.optString("time24"), b.optString("zone"))
                    ) else emptyList()
                }
                // Linked rows: the set row refills the empty one on load,
                // mirroring the currency tool recomputing its target.
                if (time24A.isNotEmpty()) propagateFromA()
                else if (time24B.isNotEmpty()) propagateFromB()
            } catch (e: Exception) { }
            loaded = true
        }
    }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(30000); tick++ }
    }

    val display12A = time12Of(time24A)
    val display12B = time12Of(time24B)
    val zoneLabelA = zoneLabelOf(zoneA)
    val zoneLabelB = zoneLabelOf(zoneB)

    // v1.0.1 (defects #4/#5): source layout — card header (trio + the
    // "Current" fa-clock button AFTER the trio, as in tab-time's header),
    // one shared label over the (12h input | zone select) pair (7/12 + 5/12
    // per the source grid), the "24-Hour Time (HH:MM)" field, then the
    // Formatted 12h / 24h / Selected Region results box. The 12h field is
    // an editable "hh:mm AM/PM" text (the source's <input type=time> shows
    // 12h format; its value still drives the same single time state).
    var time12TextA by remember { mutableStateOf("") }
    var time12TextB by remember { mutableStateOf("") }
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
    LaunchedEffect(time24A) {
        if (time24A.isNotEmpty() && parse12(time12TextA) != time24A) time12TextA = display12A
        if (time24A.isEmpty()) time12TextA = ""
    }
    LaunchedEffect(time24B) {
        if (time24B.isNotEmpty() && parse12(time12TextB) != time24B) time12TextB = display12B
        if (time24B.isEmpty()) time12TextB = ""
    }
    fun setCurrentTimeForRow(setTime: (String) -> Unit, zoneValue: String) {
        // Source: setCurrentTime() — now in the row's zone, hour12:false HH:mm.
        try {
            val now = ZonedDateTime.now(ZoneId.of(zoneValue))
            setTime(now.format(DateTimeFormatter.ofPattern("HH:mm")))
            saveState()
            toast(context, "Loaded current regional time.")
        } catch (e: Exception) { toast(context, "Could not load time for that zone.") }
    }
    fun trackZone(zoneValue: String) {
        val tz = WORLD_TIME_ZONES.find { it.zone == zoneValue } ?: return
        if (tracked.none { it.zone == tz.zone }) {
            saveTracked(tracked + tz)
            toast(context, "Added ${tz.country} to tracked times.")
        }
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
                "Time 1: $display12A [$zoneA]",
                "Time 2: $display12B [$zoneB]"
            ) + tracked.map { "${it.label} (${it.zone})" },
            shareText = if (time24A.isEmpty()) "" else
                "Time conversion: $display12A [$zoneA] = ${convertTrackedTime(time24A, zoneA, zoneB)} [$zoneB]"
        )
        // Each row keeps the full 12h <-> 24h conversion (12h input + 24h field);
        // the old single results box is retired because the fields already show both formats.
        TimeRow(
            title = "Time 1",
            time12Text = time12TextA,
            on12Change = { v ->
                time12TextA = v
                if (v.isEmpty()) { time24A = ""; propagateFromA(); saveState() }
                else parse12(v)?.let { time24A = it; propagateFromA(); saveState() }
            },
            zoneLabel = zoneLabelA,
            onZoneLabel = { label ->
                val tz = WORLD_TIME_ZONES.find { it.label == label } ?: return@TimeRow
                zoneA = tz.zone
                propagateFromA()
                saveState()
            },
            time24 = time24A,
            on24Change = { v ->
                time24A = v.filter { it.isDigit() || it == ':' }.take(5)
                propagateFromA()
                saveState()
            },
            onCurrent = { setCurrentTimeForRow({ time24A = it }, zoneA); propagateFromA(); saveState() },
            onTrack = { trackZone(zoneA) }
        )
        TimeRow(
            title = "Time 2",
            time12Text = time12TextB,
            on12Change = { v ->
                time12TextB = v
                if (v.isEmpty()) { time24B = ""; propagateFromB(); saveState() }
                else parse12(v)?.let { time24B = it; propagateFromB(); saveState() }
            },
            zoneLabel = zoneLabelB,
            onZoneLabel = { label ->
                val tz = WORLD_TIME_ZONES.find { it.label == label } ?: return@TimeRow
                zoneB = tz.zone
                propagateFromB()
                saveState()
            },
            time24 = time24B,
            on24Change = { v ->
                time24B = v.filter { it.isDigit() || it == ':' }.take(5)
                propagateFromB()
                saveState()
            },
            onCurrent = { setCurrentTimeForRow({ time24B = it }, zoneB); propagateFromB(); saveState() },
            onTrack = { trackZone(zoneB) }
        )
        Button(
            onClick = {
                if (time24A.isEmpty() || time24B.isEmpty() ||
                    time12Of(time24A) == "--:--" || time12Of(time24B) == "--:--") {
                    toast(context, "Enter valid times in both rows first.")
                    return@Button
                }
                val entry = SavedConversion(time24A, zoneA, time24B, zoneB)
                if (savedConversions.contains(entry)) {
                    toast(context, "That conversion is already saved.")
                } else {
                    savedConversions = savedConversions + entry
                    saveState()
                    toast(context, "Conversion saved.")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) { Text("Save conversion") }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            Text("Saved Conversions (${savedConversions.size})", style = MaterialTheme.typography.titleMedium)
            if (savedConversions.isNotEmpty()) {
                TextButton(onClick = { savedConversions = emptyList(); saveState(); toast(context, "Cleared all saved conversions.") }) {
                    Text("Clear All")
                }
            }
        }
        if (savedConversions.isEmpty()) {
            Text(
                "No conversions saved yet. Enter times above and tap Save conversion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        savedConversions.forEachIndexed { idx, entry ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        compareTimeRows(entry.timeA, entry.zoneA, entry.timeB, entry.zoneB).forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(onClick = {
                        savedConversions = savedConversions.filterIndexed { i, _ -> i != idx }
                        saveState()
                        toast(context, "Removed saved conversion.")
                    }) { Text("✕") }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Show converted time from Time 1", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = trackedMode == "converted",
                onCheckedChange = {
                    trackedMode = if (it) "converted" else "live"
                    saveState()
                }
            )
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
                "No country time zones tracked yet. Use a row's Track zone button to add it to tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // `tick` is read here so the cards re-render on the 30s refresh.
        if (tick < 0) Text("")
        tracked.forEachIndexed { idx, item ->
            val shown = if (trackedMode == "converted") {
                convertTrackedTime(time24A, zoneA, item.zone)
            } else try {
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
                    Text(shown, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        saveTracked(tracked.filterIndexed { i, _ -> i != idx })
                        toast(context, "Removed tracked time zone.")
                    }) { Text("✕") }
                }
            }
        }
    }
}
