package com.shawnrodis.toolbox.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
import com.shawnrodis.toolbox.data.WORLD_CURRENCIES
import com.shawnrodis.toolbox.ui.NumField
import com.shawnrodis.toolbox.ui.PickerField
import com.shawnrodis.toolbox.ui.ToolCardHeader
import com.shawnrodis.toolbox.ui.ToolHeaderButton
import com.shawnrodis.toolbox.ui.ToolScreen
import com.shawnrodis.toolbox.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Currency Converter — faithful port:
 *  - rates from https://open.er-api.com/v6/latest/USD (source fetchExchangeRates)
 *  - conversion via USD cross-rate: rate = rates[target] / rates[base]
 *    (source convertCurrency), result toFixed(2), display `1 BASE = rate TARGET` toFixed(4)
 *  - defaults base=USD, target=PHP (source populateDropdowns)
 *  - native addition: last fetched rates are cached on-device so the tool
 *    still works offline ("Using Offline Rates" state in the source).
 */
@Composable
fun CurrencyScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateJson by store.stringState("toolbox_currency_state").collectAsState(initial = "")
    val ratesJson by store.stringState("toolbox_currency_rates").collectAsState(initial = "")

    var amount by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("USD") }
    var target by remember { mutableStateOf("PHP") }
    var rates by remember { mutableStateOf<Map<String, Double>>(mapOf("USD" to 1.0)) }
    var status by remember { mutableStateOf("Using Offline Rates") }
    var loaded by remember { mutableStateOf(false) }

    fun parseRates(json: String): Map<String, Double> {
        if (json.isEmpty()) return mapOf("USD" to 1.0)
        return try {
            val o = JSONObject(json)
            val m = mutableMapOf<String, Double>()
            o.keys().forEach { k -> m[k] = o.getDouble(k) }
            if (m.isEmpty()) mapOf("USD" to 1.0) else m
        } catch (e: Exception) { mapOf("USD" to 1.0) }
    }

    fun saveState() {
        scope.launch {
            store.setStringState(
                "toolbox_currency_state",
                JSONObject().put("amount", amount).put("base", base).put("target", target).toString()
            )
        }
    }

    fun convert(fromBase: Boolean) {
        val rateBase = rates[base] ?: 1.0
        val rateTarget = rates[target] ?: 1.0
        val exchangeRate = rateTarget / rateBase
        if (fromBase) {
            val calc = (amount.toDoubleOrNull() ?: 0.0) * exchangeRate
            targetAmount = if (calc != 0.0) String.format(Locale.US, "%.2f", calc) else ""
        } else {
            val calc = (targetAmount.toDoubleOrNull() ?: 0.0) * (1 / exchangeRate)
            amount = if (calc != 0.0) String.format(Locale.US, "%.2f", calc) else ""
        }
        saveState()
    }

    fun fetchRates(quiet: Boolean) {
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val conn = URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
                }
                val data = JSONObject(text)
                if (data.has("rates")) {
                    val r = data.getJSONObject("rates")
                    val m = mutableMapOf<String, Double>()
                    r.keys().forEach { k -> m[k] = r.getDouble(k) }
                    rates = m
                    store.setStringState("toolbox_currency_rates", r.toString())
                    status = "Live API Rates Connected"
                    convert(true)
                    if (!quiet) toast(context, "Exchange rates updated successfully!")
                }
            } catch (e: Exception) {
                status = "Using Offline Rates"
                if (!quiet) toast(context, "Failed to fetch live rates. Using defaults.")
            }
        }
    }

    LaunchedEffect(stateJson, ratesJson) {
        if (!loaded) {
            if (ratesJson.isNotEmpty()) { rates = parseRates(ratesJson); status = "Using Offline Rates (cached)" }
            if (stateJson.isNotEmpty()) {
                try {
                    val s = JSONObject(stateJson)
                    base = s.optString("base", "USD").ifEmpty { "USD" }
                    target = s.optString("target", "PHP").ifEmpty { "PHP" }
                    amount = s.optString("amount", "")
                } catch (e: Exception) { }
                loaded = true
                convert(true)
                fetchRates(quiet = true)
            } else if (ratesJson.isNotEmpty()) {
                loaded = true
                convert(true)
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!loaded) { loaded = true; fetchRates(quiet = true) }
    }

    val labels = WORLD_CURRENCIES.map { "${it.code} (${it.country})" }
    fun labelFor(code: String) = labels.firstOrNull { it.startsWith("$code ") } ?: code
    fun codeFor(label: String) = label.substringBefore(" ")

    val exchangeRate = (rates[target] ?: 1.0) / (rates[base] ?: 1.0)

    // v1.0.1 (defects #4/#5): the source's layout — card header block with
    // the Clear (fa-xmark) + Refresh (fa-rotate) icon buttons and the
    // Share/JPG/PDF trio; then (From Amount | From Country) and
    // (To Amount | To Country) rows with labels above the fields; then the
    // EXCHANGE RATE summary box with the status inside it, right-aligned.
    // There are no big Refresh/Clear buttons in the source body.
    ToolScreen(title = "Global Currency Converter", onBack = onBack) {
        ToolCardHeader(
            faIcon = "fa-globe",
            title = "Global Currency Converter",
            description = "Convert any world country's currency",
            fileBase = "currency-converter",
            prettyName = "Currency converter",
            exportHeader = "Global Currency Converter",
            lines = listOf(
                "$amount $base = $targetAmount $target",
                String.format(Locale.US, "1 %s = %.4f %s", base, exchangeRate, target),
                status
            ),
            shareText = "$amount $base = $targetAmount $target",
            leadingActions = {
                ToolHeaderButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Clear Currency Fields",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        // Source: clearCurrencyFields().
                        amount = ""; targetAmount = ""; saveState()
                        toast(context, "Currency fields cleared.")
                    }
                )
                ToolHeaderButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Fetch Live Rates",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { fetchRates(quiet = false) }
                )
            }
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            NumField("From Amount", amount, { amount = it; convert(true) }, Modifier.weight(2f), "0.00")
            PickerField(
                "From Country", labels, labelFor(base),
                { base = codeFor(it); convert(true) }, Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            NumField("To Amount", targetAmount, { targetAmount = it; convert(false) }, Modifier.weight(2f), "0.00")
            PickerField(
                "To Country", labels, labelFor(target),
                { target = codeFor(it); convert(true) }, Modifier.weight(1f)
            )
        }
        // Source summary box: bg-slate-50/zinc-950, rounded-xl, border.
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "EXCHANGE RATE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        String.format(Locale.US, "1 %s = %.4f %s", base, exchangeRate, target),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    // v1.0.2 (defect D): weighted so the status WRAPS
                    // inside the summary box instead of pushing out of
                    // its right edge (where the Home FAB overlays).
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
