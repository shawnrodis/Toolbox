package com.shawnrodis.toolbox.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shawnrodis.toolbox.data.AppSettings
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.ui.ToolCardHeader
import com.shawnrodis.toolbox.ui.NumField
import com.shawnrodis.toolbox.ui.ToolScreen
import com.shawnrodis.toolbox.ui.toolboxBeep
import com.shawnrodis.toolbox.ui.numOr0
import com.shawnrodis.toolbox.ui.toast
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// PTO models mirror the source JSON:
// settings {allowance, carryover} (toolbox_pto_settings), logs [{date, hours, notes}] (toolbox_pto_logs)
data class PtoLog(val date: String, val hours: Double, val notes: String)

private fun fmtHours(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

/**
 * PTO Tracker — source updatePtoUI():
 * used = sum(log.hours); remaining = allowance + carryover - used;
 * days shown as (hours/8).toFixed(1). Add-log is a dialog, like the web modal.
 */
@Composable
fun PtoScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsJson by store.stringState("toolbox_pto_settings", "{}").collectAsState(initial = "{}")
    val logsJson by store.stringState("toolbox_pto_logs", "[]").collectAsState(initial = "[]")

    val ptoSettings = remember(settingsJson) {
        try {
            val o = JSONObject(settingsJson.ifEmpty { "{}" })
            (o.optDouble("allowance", 0.0) to o.optDouble("carryover", 0.0))
        } catch (e: Exception) { 0.0 to 0.0 }
    }
    val logs: List<PtoLog> = remember(logsJson) {
        try {
            val a = JSONArray(logsJson.ifEmpty { "[]" })
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                PtoLog(o.optString("date"), o.optDouble("hours", 0.0), o.optString("notes"))
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveSettings(allowance: Double, carryover: Double) {
        scope.launch {
            store.setStringState("toolbox_pto_settings",
                JSONObject().put("allowance", allowance).put("carryover", carryover).toString())
        }
    }
    fun saveLogs(list: List<PtoLog>) {
        scope.launch {
            val a = JSONArray()
            list.forEach { l -> a.put(JSONObject().put("date", l.date).put("hours", l.hours).put("notes", l.notes)) }
            store.setStringState("toolbox_pto_logs", a.toString())
        }
    }

    val used = logs.sumOf { it.hours }
    val remaining = ptoSettings.first + ptoSettings.second - used
    var showAdd by remember { mutableStateOf(false) }

    ToolScreen(title = "PTO Tracker", onBack = onBack) {
        // v1.0.1 (defects #4/#5): card header with the trio; source field
        // labels are "Allowance (hrs)" / "Carryover Cap (hrs)".
        ToolCardHeader(
            faIcon = "fa-calendar-check",
            title = "PTO Tracker",
            description = "Leave balance & logged days",
            fileBase = "pto-tracker",
            prettyName = "PTO tracker",
            exportHeader = "PTO Tracker",
            lines = listOf(
                "Used: ${fmtHours(used)} hrs",
                "Remaining: ${fmtHours(remaining)} hrs",
                "Allowance: ${fmtHours(ptoSettings.first)} hrs · Carryover: ${fmtHours(ptoSettings.second)} hrs"
            ) + logs.map { "${it.date} - ${it.notes}: ${fmtHours(it.hours)}h" },
            shareText = "PTO balance: ${fmtHours(remaining)} hrs"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            NumField("Allowance (hrs)", if (ptoSettings.first == 0.0) "" else fmtHours(ptoSettings.first),
                { v -> saveSettings(numOr0(v), ptoSettings.second) }, Modifier.weight(1f), "0")
            NumField("Carryover Cap (hrs)", if (ptoSettings.second == 0.0) "" else fmtHours(ptoSettings.second),
                { v -> saveSettings(ptoSettings.first, numOr0(v)) }, Modifier.weight(1f), "0")
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.padding(14.dp)) {
                Text("Used: ${fmtHours(used)} hrs (${String.format(Locale.US, "%.1f", used / 8)} days)")
                Text("Remaining: ${fmtHours(remaining)} hrs (${String.format(Locale.US, "%.1f", remaining / 8)} days)",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Allowance: (${String.format(Locale.US, "%.1f", ptoSettings.first / 8)} days)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("Logged Leave Entries — ${logs.size} Entries", style = MaterialTheme.typography.titleMedium)
        }
        if (logs.isEmpty()) {
            Text("No PTO logged.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        }
        logs.forEachIndexed { idx, log ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${log.date} - ${log.notes}", modifier = Modifier.weight(1f))
                Text("${fmtHours(log.hours)}h", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { saveLogs(logs.filterIndexed { i, _ -> i != idx }) }) { Text("Delete") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            Button(onClick = { showAdd = true }) { Text("Log PTO Leave") }
            if (logs.isNotEmpty()) {
                OutlinedButton(onClick = { saveLogs(emptyList()); toast(context, "All PTO logs cleared.") }) { Text("Clear All") }
            }
        }
    }

    if (showAdd) {
        var date by remember { mutableStateOf("") }
        var hours by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Log PTO Leave") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = date, onValueChange = { date = it },
                        label = { Text("Date (e.g. 2026-10-12)") }, singleLine = true)
                    NumField("Hours", hours, { hours = it }, placeholder = "8")
                    OutlinedTextField(value = notes, onValueChange = { notes = it },
                        label = { Text("Notes (e.g. Vacation)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    // Source handlePtoSubmit(): push {date, hours: parseFloat, notes} as-is.
                    val h = hours.toDoubleOrNull()
                    if (date.isBlank() || h == null) {
                        toast(context, "Date and hours are required.")
                        return@Button
                    }
                    saveLogs(logs + PtoLog(date, h, notes))
                    showAdd = false
                }) { Text("Save Log") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

data class TallyItem(val name: String, val count: Int)

/**
 * Tally Counter — source: tallyItems [{name, count}] in toolbox_tally_items;
 * add with a name (beep 880), +/- (beep), delete. Count may go negative,
 * exactly as in the source (no clamping there).
 * The web version's "Tally Export Data" JPG/PDF export is Phase 3.
 */
@Composable
fun TallyScreen(store: AppStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = AppSettings())
    val tallyJson by store.stringState("toolbox_tally_items", "[]").collectAsState(initial = "[]")
    val items: List<TallyItem> = remember(tallyJson) {
        try {
            val a = JSONArray(tallyJson.ifEmpty { "[]" })
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                TallyItem(o.optString("name"), o.optInt("count", 0))
            }
        } catch (e: Exception) { emptyList() }
    }
    fun save(list: List<TallyItem>) {
        scope.launch {
            val a = JSONArray()
            list.forEach { t -> a.put(JSONObject().put("name", t.name).put("count", t.count)) }
            store.setStringState("toolbox_tally_items", a.toString())
        }
    }
    var newName by remember { mutableStateOf("") }

    ToolScreen(title = "Tally Counter", onBack = onBack) {
        // v1.0.1 (defect #4): card header with the dedicated tally trio.
        ToolCardHeader(
            faIcon = "fa-list-ol",
            title = "Tally Counter",
            description = "Keep track of counts & export",
            fileBase = "tally-export",
            prettyName = "Tally list",
            exportHeader = "Tally Export Data",
            lines = items.map { "${it.name}: ${it.count}" },
            shareText = if (items.isEmpty()) "" else
                "Tally:\n" + items.joinToString("\n") { "- ${it.name}: ${it.count}" }
        )
        if (items.isEmpty()) {
            Text("No tallies added yet.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        items.forEachIndexed { idx, item ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        save(items.mapIndexed { i, t -> if (i == idx) t.copy(count = t.count - 1) else t })
                        // Source updateTally(idx,-1): toolboxBeep(520, 70).
                        toolboxBeep(settings.sound, 520, 70)
                    }) { Text("−") }
                    Text(item.count.toString(), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        save(items.mapIndexed { i, t -> if (i == idx) t.copy(count = t.count + 1) else t })
                        // Source updateTally(idx,+1): toolboxBeep(740, 70).
                        toolboxBeep(settings.sound, 740, 70)
                    }) { Text("+") }
                    TextButton(onClick = {
                        save(items.filterIndexed { i, _ -> i != idx })
                        // Source removeTally(): toolboxBeep(330, 110, 'triangle').
                        toolboxBeep(settings.sound, 330, 110, "triangle")
                    }) { Text("Delete") }
                }
            }
        }
        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New item name...") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Button(onClick = {
            if (newName.isNotEmpty()) {
                save(items + TallyItem(newName, 0))
                newName = ""
                // Source addTallyItem(): toolboxBeep(880, 90).
                toolboxBeep(settings.sound, 880, 90)
            }
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Add") }
        if (items.isNotEmpty()) {
            Text(
                "Total count: ${items.sumOf { it.count }} across ${items.size} tallies",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
