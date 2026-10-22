package com.shawnrodis.toolbox.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shawnrodis.toolbox.data.AppSettings
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.data.UNIT_CONVERSIONS
import com.shawnrodis.toolbox.data.UNIT_OPTIONS
import com.shawnrodis.toolbox.ui.ToolCardHeader
import com.shawnrodis.toolbox.ui.NumField
import com.shawnrodis.toolbox.ui.PickerField
import com.shawnrodis.toolbox.ui.ToolScreen
import com.shawnrodis.toolbox.ui.toolboxBeep
import com.shawnrodis.toolbox.ui.numOr0
import com.shawnrodis.toolbox.ui.toast
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// Models mirror the source JSON shapes exactly:
// comparison option: {id, name, price, qty, unit}  (toolbox_comparison_options)
// shop item:         {id, name, qty, done}        (toolbox_shop_items)
data class CompOption(val id: String, var name: String, var price: String, var qty: String, var unit: String)
data class ShopItem(val id: Long, val name: String, val qty: Int, val done: Boolean)

private fun unitInfo(unit: String) = UNIT_CONVERSIONS[unit] ?: com.shawnrodis.toolbox.data.ShopUnitInfo("count", 1.0, "ct")
private fun normalizedQty(o: CompOption) = numOr0(o.qty) * unitInfo(o.unit.ifEmpty { "oz" }).factor
private fun normalizedUnitPrice(o: CompOption): Double {
    val nq = normalizedQty(o)
    return if (nq > 0) numOr0(o.price) / nq else 0.0
}

/** Source fmtUnitPrice(): <=0 -> $0.00; >=1 -> 2dp; <1 -> 3dp. */
private fun fmtUnitPrice(v: Double): String =
    if (v <= 0) "$0.00" else if (v >= 1) String.format(Locale.US, "$%.2f", v) else String.format(Locale.US, "$%.3f", v)

@Composable
fun ShoppingScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = AppSettings())
    val compJson by store.stringState("toolbox_comparison_options", "[]").collectAsState(initial = "[]")
    val shopJson by store.stringState("toolbox_shop_items", "[]").collectAsState(initial = "[]")

    val options: List<CompOption> = remember(compJson) {
        try {
            val a = JSONArray(compJson.ifEmpty { "[]" })
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                CompOption(
                    o.optString("id"), o.optString("name"),
                    if (o.has("price")) o.get("price").toString().removeSuffix(".0").let { if (it == "0") "" else it } else "",
                    if (o.has("qty")) o.get("qty").toString().removeSuffix(".0").let { if (it == "0") "" else it } else "",
                    o.optString("unit", "oz")
                )
            }
        } catch (e: Exception) { emptyList() }
    }
    val shopItems: List<ShopItem> = remember(shopJson) {
        try {
            val a = JSONArray(shopJson.ifEmpty { "[]" })
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                ShopItem(o.optLong("id"), o.optString("name"), o.optInt("qty", 1), o.optBoolean("done", false))
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveOptions(list: List<CompOption>) {
        scope.launch {
            val a = JSONArray()
            list.forEach { o ->
                a.put(JSONObject().put("id", o.id).put("name", o.name)
                    .put("price", numOr0(o.price)).put("qty", numOr0(o.qty)).put("unit", o.unit))
            }
            store.setStringState("toolbox_comparison_options", a.toString())
        }
    }
    fun saveShop(list: List<ShopItem>) {
        scope.launch {
            val a = JSONArray()
            list.forEach { it2 ->
                a.put(JSONObject().put("id", it2.id).put("name", it2.name).put("qty", it2.qty).put("done", it2.done))
            }
            store.setStringState("toolbox_shop_items", a.toString())
        }
    }

    // Best value: source refreshComparisonComputed() — only when every priced
    // option shares one dimension (weight/volume/count), lowest normalized
    // unit price wins.
    val priced = options.filter { numOr0(it.price) > 0 && normalizedQty(it) > 0 }
    val dims = priced.map { unitInfo(it.unit.ifEmpty { "oz" }).dim }.toSet()
    val bestId = if (dims.size == 1) priced.minByOrNull { normalizedUnitPrice(it) }?.id else null
    val mixedHint = priced.size > 1 && dims.size != 1

    // Export content (source __shareTextFor shopping branch), hoisted:
    // v1.0.1 (defect #4) the trio lives in the card header now, so the
    // summary lines are computed before the screen body.
    val shareParts = mutableListOf<String>()
    if (options.isNotEmpty()) {
        val lines = options.map { o ->
            val up = normalizedUnitPrice(o)
            "- ${o.name}: $${o.price} / ${o.qty}${o.unit}" +
                (if (up > 0) " ($${String.format(java.util.Locale.US, "%.3f", up)}/${o.unit})" else "") +
                (if (o.id == bestId) " [best value]" else "")
        }
        shareParts.add("Price comparison:\n" + lines.joinToString("\n"))
    }
    if (shopItems.isNotEmpty()) {
        val done = shopItems.count { it.done }
        shareParts.add(
            "Shopping list ($done of ${shopItems.size} done):\n" +
                shopItems.joinToString("\n") {
                    (if (it.done) "[x] " else "[ ] ") + it.name +
                        (if (it.qty > 1) " x${it.qty}" else "")
                }
        )
    }

    ToolScreen(title = "Shopping Assistant", onBack = onBack) {
        ToolCardHeader(
            faIcon = "fa-cart-shopping",
            title = "Shopping Assistant",
            description = "Compare prices & check off your list",
            fileBase = "shopping-assistant",
            prettyName = "Shopping assistant",
            exportHeader = "Shopping Assistant",
            lines = shareParts.flatMap { it.split("\n") }
                .ifEmpty { listOf("No items yet.") },
            shareText = if (shareParts.isEmpty()) "Shopping assistant: no items yet."
            else shareParts.joinToString("\n\n")
        )
        Text("Compare Options", style = MaterialTheme.typography.titleLarge)
        if (options.isEmpty()) {
            Text("No comparison options added. Tap Add Option.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        options.forEachIndexed { idx, opt ->
            val isBest = opt.id == bestId
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = if (isBest) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                else CardDefaults.cardColors()
            ) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Option #${idx + 1}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isBest) Text("Best Value", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { saveOptions(options.filter { it.id != opt.id }) }) { Text("Remove") }
                    }
                    OutlinedTextField(
                        value = opt.name,
                        onValueChange = { v -> saveOptions(options.map { if (it.id == opt.id) it.copy(name = v) else it }) },
                        label = { Text("Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        NumField("Price ($)", opt.price,
                            { v -> saveOptions(options.map { if (it.id == opt.id) it.copy(price = v) else it }) },
                            Modifier.weight(1f))
                        NumField("Qty", opt.qty,
                            { v -> saveOptions(options.map { if (it.id == opt.id) it.copy(qty = v) else it }) },
                            Modifier.weight(1f))
                        PickerField("Unit", UNIT_OPTIONS, opt.unit,
                            { u -> saveOptions(options.map { if (it.id == opt.id) it.copy(unit = u) else it }) },
                            Modifier.weight(1f))
                    }
                    Text(
                        "Per ${unitInfo(opt.unit.ifEmpty { "oz" }).base}: ${fmtUnitPrice(normalizedUnitPrice(opt))}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        if (mixedHint) {
            Text("Options use different unit types — best value can't be compared across types.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = {
                saveOptions(options + CompOption("c" + System.currentTimeMillis(), "", "", "", "oz"))
            }) { Text("Add Option") }
            OutlinedButton(onClick = { saveOptions(emptyList()) }) { Text("Clear") }
        }

        // ---------------- Shopping List ----------------
        Text("Shopping List", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
        val doneCount = shopItems.count { it.done }
        val pct = if (shopItems.isEmpty()) 0 else Math.round(doneCount * 100.0 / shopItems.size).toInt()
        Text("$doneCount of ${shopItems.size} done · $pct%", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        if (shopItems.isEmpty()) {
            Text("Your list is empty. Add the first item below.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        // Source renderShopItems(): unchecked items first, then checked.
        val ordered = shopItems.filter { !it.done } + shopItems.filter { it.done }
        ordered.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    val updated = shopItems.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                    saveShop(updated)
                    // Source toggleShopItem(): toolboxBeep(done ? 660 : 520, 60)
                    // with the NEW done state.
                    toolboxBeep(settings.sound, if (!item.done) 660 else 520, 60)
                }.padding(vertical = 2.dp)
            ) {
                Checkbox(checked = item.done, onCheckedChange = { checked ->
                    saveShop(shopItems.map { if (it.id == item.id) it.copy(done = checked) else it })
                })
                Text(
                    item.name,
                    modifier = Modifier.weight(1f),
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (item.qty > 1) Text("×${item.qty}", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { saveShop(shopItems.filter { it.id != item.id }) }) { Text("✕") }
            }
        }
        var newName by remember { mutableStateOf("") }
        var newQty by remember { mutableStateOf("1") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Item name") },
                singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val q = (newQty.toIntOrNull() ?: 1) - 1
                newQty = q.coerceIn(1, 99).toString()
            }) { Text("−") }
            Text(newQty, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = {
                val q = (newQty.toIntOrNull() ?: 1) + 1
                newQty = q.coerceIn(1, 99).toString()
            }) { Text("+") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = {
                val name = newName.trim()
                if (name.isEmpty()) { toast(context, "Enter an item name first."); return@Button }
                // Source addShopItem(): qty clamped 1..99, id = Date.now()
                val qty = (newQty.toIntOrNull() ?: 1).coerceIn(1, 99)
                saveShop(shopItems + ShopItem(System.currentTimeMillis(), name, qty, false))
                newName = ""; newQty = "1"
                // Source addShopItem(): toolboxBeep(880, 70).
                toolboxBeep(settings.sound, 880, 70)
            }) { Text("Add") }
            OutlinedButton(onClick = {
                if (shopItems.any { it.done }) {
                    saveShop(shopItems.filter { !it.done })
                    toast(context, "Checked items cleared.")
                }
            }) { Text("Clear checked") }
        }
    }
}
