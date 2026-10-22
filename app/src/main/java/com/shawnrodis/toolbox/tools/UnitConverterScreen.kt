package com.shawnrodis.toolbox.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.data.UC_DEFS
import com.shawnrodis.toolbox.data.ucFmt
import com.shawnrodis.toolbox.data.ucFromBase
import com.shawnrodis.toolbox.data.ucToBase
import com.shawnrodis.toolbox.ui.ToolCardHeader
import com.shawnrodis.toolbox.ui.NumField
import com.shawnrodis.toolbox.ui.PickerField
import com.shawnrodis.toolbox.ui.ToolScreen
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Unit Converter — port of UC_DEFS / ucToBase / ucFromBase / ucFmt / ucConvert:
 * state {cat, from, to, value} persists in toolbox_uc_state (default
 * weight kg -> lb, per source __ucState). Result line plus the source's
 * "All units in this category" list (every unit except the From unit).
 */
@Composable
fun UnitConverterScreen(store: AppStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stateJson by store.stringState("toolbox_uc_state").collectAsState(initial = "")

    var catId by remember { mutableStateOf("weight") }
    var fromId by remember { mutableStateOf("kg") }
    var toId by remember { mutableStateOf("lb") }
    var value by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    val cat = UC_DEFS.find { it.id == catId } ?: UC_DEFS.first()

    fun save() {
        scope.launch {
            store.setStringState(
                "toolbox_uc_state",
                JSONObject().put("cat", catId).put("from", fromId).put("to", toId).put("value", value).toString()
            )
        }
    }

    LaunchedEffect(stateJson) {
        if (!loaded && stateJson.isNotEmpty()) {
            try {
                val s = JSONObject(stateJson)
                catId = s.optString("cat", "weight")
                val c = UC_DEFS.find { it.id == catId } ?: UC_DEFS.first()
                val ids = c.units.map { it.id }
                fromId = s.optString("from", "").takeIf { ids.contains(it) } ?: ids.first()
                toId = s.optString("to", "").takeIf { ids.contains(it) } ?: ids.getOrElse(1) { ids.first() }
                value = s.optString("value", "")
            } catch (e: Exception) { }
            loaded = true
        }
    }

    val v = value.toDoubleOrNull()
    val base = if (v != null) ucToBase(cat, fromId, v) else null
    val result = if (base != null) ucFromBase(cat, toId, base) else null
    val toShort = cat.units.find { it.id == toId }?.short ?: toId

    ToolScreen(title = "Unit Converter", onBack = onBack) {
        // v1.0.1 (defects #4/#5): card header with the trio; source hint
        // line under the category pills; source field labels.
        ToolCardHeader(
            faIcon = "fa-ruler-combined",
            title = "Unit Converter",
            description = "Weight, temperature, length, volume & area",
            fileBase = "unit-converter",
            prettyName = "Unit converter",
            exportHeader = "Unit Converter",
            lines = listOf(
                if (v == null) "No value entered"
                else "$value $fromId = ${ucFmt(result)} $toShort"
            ),
            shareText = if (v == null || result == null) "" else
                "$value $fromId = ${ucFmt(result)} $toShort $toId"
        )
        // Category chips (source ucRenderCats)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            UC_DEFS.take(3).forEach { c -> CategoryChip(c.label, c.id == catId) {
                catId = c.id
                val ids = c.units.map { it.id }
                if (fromId !in ids) fromId = ids.first()
                if (toId !in ids) toId = ids.getOrElse(1) { ids.first() }
                save()
            } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            UC_DEFS.drop(3).forEach { c -> CategoryChip(c.label, c.id == catId) {
                catId = c.id
                val ids = c.units.map { it.id }
                if (fromId !in ids) fromId = ids.first()
                if (toId !in ids) toId = ids.getOrElse(1) { ids.first() }
                save()
            } }
        }
        Text(
            "Pick a category above, enter a value, then choose the units to convert between.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        NumField("Value to convert", value, { value = it; save() }, Modifier.fillMaxWidth().padding(top = 12.dp), "0")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            PickerField("From unit", cat.units.map { "${it.name} (${it.short})" },
                cat.units.find { it.id == fromId }?.let { "${it.name} (${it.short})" } ?: "",
                { sel -> fromId = cat.units.find { "${it.name} (${it.short})" == sel }?.id ?: fromId; save() },
                Modifier.weight(1f))
            // Source: ucSwap icon button (fa-arrow-right-arrow-left) between the selects.
            IconButton(onClick = {
                val t = fromId; fromId = toId; toId = t; save()
            }) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap units")
            }
            PickerField("To unit", cat.units.map { "${it.name} (${it.short})" },
                cat.units.find { it.id == toId }?.let { "${it.name} (${it.short})" } ?: "",
                { sel -> toId = cat.units.find { "${it.name} (${it.short})" == sel }?.id ?: toId; save() },
                Modifier.weight(1f))
        }
        Text(
            if (v == null) "–" else "${ucFmt(result)} $toShort",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "All units in this category",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        if (v == null || base == null) {
            Text("Enter a value to see every conversion.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            cat.units.filter { it.id != fromId }.forEach { u ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(u.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${ucFmt(ucFromBase(cat, u.id, base))} ${u.short}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}
