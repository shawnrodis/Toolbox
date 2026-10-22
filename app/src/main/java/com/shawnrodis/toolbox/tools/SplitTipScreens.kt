package com.shawnrodis.toolbox.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.ui.SlimSlider
import com.shawnrodis.toolbox.ui.ToolCardHeader
import com.shawnrodis.toolbox.ui.NumField
import com.shawnrodis.toolbox.ui.ToolScreen
import com.shawnrodis.toolbox.ui.copyToClipboard
import com.shawnrodis.toolbox.ui.money
import com.shawnrodis.toolbox.ui.numOr0
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Split Gas & Parking — source calculateSplitExpense():
 * perPerson = (gas + parking) / people  (people defaults to 1 when invalid)
 * Copy summary text is verbatim: `Split Total: $X.XX each`
 * State persists in toolbox_split_state {gas, parking, people}.
 * (JPG/PDF export buttons from the web version are Phase 3.)
 */
@Composable
fun SplitScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateJson by store.stringState("toolbox_split_state").collectAsState(initial = "")
    var gas by remember { mutableStateOf("") }
    var parking by remember { mutableStateOf("") }
    var people by remember { mutableStateOf("1") }
    var loaded by remember { mutableStateOf(false) }

    fun save() {
        scope.launch {
            store.setStringState("toolbox_split_state",
                JSONObject().put("gas", gas).put("parking", parking).put("people", people).toString())
        }
    }
    LaunchedEffect(stateJson) {
        if (!loaded && stateJson.isNotEmpty()) {
            try {
                val s = JSONObject(stateJson)
                gas = s.optString("gas", ""); parking = s.optString("parking", ""); people = s.optString("people", "1")
            } catch (e: Exception) { }
            loaded = true
        }
    }
    val ppl = people.toIntOrNull() ?: 1
    val perPerson = (numOr0(gas) + numOr0(parking)) / (if (ppl == 0) 1 else ppl)

    ToolScreen(title = "Split Gas & Parking", onBack = onBack) {
        // v1.0.1 (defects #4/#5): card header with the dedicated split
        // trio; the description moved into the header (it was a stray
        // body line); the summary box keeps its Copy Summary button.
        ToolCardHeader(
            faIcon = "fa-gas-pump",
            title = "Split Gas & Parking",
            description = "Share trip costs evenly",
            fileBase = "split-gas-parking",
            prettyName = "Gas split",
            exportHeader = "Split Gas & Parking — Summary",
            lines = listOf(
                "Total Gas: ${money(numOr0(gas))}",
                "Total Parking: ${money(numOr0(parking))}",
                "Number of People: $ppl",
                "Per Person Share: ${money(perPerson)}"
            ),
            shareText = "Split Total: ${money(perPerson)} each"
        )
        NumField("Total Gas ($)", gas, { gas = it; save() }, Modifier.fillMaxWidth().padding(top = 10.dp), "0.00")
        NumField("Total Parking ($)", parking, { parking = it; save() }, Modifier.fillMaxWidth().padding(top = 8.dp), "0.00")
        NumField("Number of People", people, { people = it.filter { c -> c.isDigit() }; save() },
            Modifier.fillMaxWidth().padding(top = 8.dp), "1")
        Text("Split Gas & Parking — Summary", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp))
        Text("Per Person Share", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(perPerson), style = MaterialTheme.typography.headlineMedium)
        Button(
            onClick = { copyToClipboard(context, "Split Total: ${money(perPerson)} each", "Split copied to clipboard!") },
            modifier = Modifier.padding(top = 12.dp)
        ) { Text("Copy Summary") }
    }
}

/**
 * Tip & Bill Split — source calculateTip():
 * tax = bill * taxPct/100; tip = bill * tipPct/100; total = bill + tax + tip
 * outputs: total/people and tip/people. Slider 0–25%, default 15%.
 * Copy summary verbatim: `Bill Split: $X.XX each`
 * State persists in toolbox_tip_state {bill, slider, people, tax}.
 * (JPG/PDF exports are Phase 3.)
 */
@Composable
fun TipScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateJson by store.stringState("toolbox_tip_state").collectAsState(initial = "")
    var bill by remember { mutableStateOf("") }
    var tipPct by remember { mutableFloatStateOf(15f) }
    var taxPct by remember { mutableStateOf("") }
    var people by remember { mutableStateOf("1") }
    var loaded by remember { mutableStateOf(false) }

    fun save() {
        scope.launch {
            store.setStringState("toolbox_tip_state",
                JSONObject().put("bill", bill).put("slider", tipPct.toInt().toString())
                    .put("people", people).put("tax", taxPct).toString())
        }
    }
    LaunchedEffect(stateJson) {
        if (!loaded && stateJson.isNotEmpty()) {
            try {
                val s = JSONObject(stateJson)
                bill = s.optString("bill", "")
                tipPct = (s.optString("slider", "15").toFloatOrNull() ?: 15f)
                people = s.optString("people", "1")
                taxPct = s.optString("tax", "")
            } catch (e: Exception) { }
            loaded = true
        }
    }
    val b = numOr0(bill)
    val tax = b * (numOr0(taxPct) / 100)
    val tip = b * (tipPct / 100f)
    val total = b + tax + tip
    val pplRaw = people.toIntOrNull() ?: 1
    val ppl = if (pplRaw == 0) 1 else pplRaw

    ToolScreen(title = "Tip & Bill Split", onBack = onBack) {
        // v1.0.1 (defects #4/#5): card header with the dedicated tip trio;
        // description moved into the header; the slider row mirrors the
        // source box (label left, accent % badge right).
        ToolCardHeader(
            faIcon = "fa-receipt",
            title = "Tip & Bill Split",
            description = "Calculate tips & split bills",
            fileBase = "tip-calculator",
            prettyName = "Tip breakdown",
            exportHeader = "Tip & Bill Split — Breakdown",
            lines = listOf(
                "Pre-Tax Bill: ${money(b)}",
                "Tax (${numOr0(taxPct)}%): ${money(tax)}",
                "Tip (${tipPct.toInt()}%): ${money(tip)}",
                "Grand Total: ${money(total)}",
                "Total / Person: ${money(total / ppl)}",
                "Tip / Person: ${money(tip / ppl)}"
            ),
            shareText = "Bill Split: ${money(total / ppl)} each"
        )
        NumField("Pre-Tax Bill ($)", bill, { bill = it; save() }, Modifier.fillMaxWidth().padding(top = 10.dp), "0.00")
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        ) {
            com.shawnrodis.toolbox.ui.FieldLabel("Tip Percentage")
            Text(
                "${tipPct.toInt()}%",
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        // v1.2.2: SlimSlider replaces the stock M3 slider (dotted step
        // ticks, bar thumb). The source input is the same `h-2`
        // appearance-none range as the Settings UI-scale slider, so it
        // gets the same 8 dp track / 20 dp thumb; its step of 1 is
        // snapped in code.
        SlimSlider(
            value = tipPct,
            onValueChange = { tipPct = it.roundToInt().toFloat().coerceIn(0f, 25f); save() },
            valueRange = 0f..25f,
            trackHeight = 8.dp,
            thumbDiameter = 20.dp,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NumField("Split (People)", people, { people = it.filter { c -> c.isDigit() }; save() }, Modifier.weight(1f), "1")
            NumField("Tax (%)", taxPct, { taxPct = it; save() }, Modifier.weight(1f), "0.0")
        }
        Text("Tip & Bill Split — Breakdown", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp))
        Text("Total / Person", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(total / ppl), style = MaterialTheme.typography.headlineMedium)
        Text("Tip / Person: ${money(tip / ppl)}", style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp))
        Text("Tax amount: ${money(tax)} · Tip amount: ${money(tip)} · Grand total: ${money(total)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
        Button(
            onClick = { copyToClipboard(context, "Bill Split: ${money(total / ppl)} each", "Tip split copied to clipboard!") },
            modifier = Modifier.padding(top = 12.dp)
        ) { Text("Copy Tip Breakdown") }
    }
}
