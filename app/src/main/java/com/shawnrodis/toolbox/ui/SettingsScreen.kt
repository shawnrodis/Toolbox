package com.shawnrodis.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.shawnrodis.toolbox.data.ACCENTS
import com.shawnrodis.toolbox.ui.ToolCardHeader
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.data.SYNC_URL_KEY
import com.shawnrodis.toolbox.data.SYNC_INTERVAL_KEY
import com.shawnrodis.toolbox.data.SYNC_INTERVAL_OPTIONS
import com.shawnrodis.toolbox.data.SYNC_TOKEN_KEY
import com.shawnrodis.toolbox.data.SyncManager
import com.shawnrodis.toolbox.data.syncConfigured
import com.shawnrodis.toolbox.data.syncGetInterval
import kotlinx.coroutines.launch

/**
 * Native Settings — scoped to Phase 1 per the port plan:
 * appearance (Light/Dark/Black), accent presets + custom hex, sound toggle,
 * and API-key fields that store ONLY what the user types here, on-device.
 * (Sync, home-view styles and Desktop Mode from the web Settings are Phase 3 /
 * have no 1:1 native equivalent; the web Desktop Mode is replaced by native
 * navigation, see audit.)
 */
@Composable
fun SettingsScreen(store: AppStore, onBack: () -> Unit) {
    val settings by store.settings.collectAsState(initial = com.shawnrodis.toolbox.data.AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ToolScreen(title = "Settings & Preferences", onBack = onBack) {
        // v1.0.1 (defect #4): the source's card header block for tab-settings
        // (fa-gear, "Settings & Preferences"; Settings has no export trio).
        ToolCardHeader(
            faIcon = "fa-gear",
            title = "Settings & Preferences",
            description = ""
        )
        Text("Appearance Theme", style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            listOf("light" to "Light", "dark" to "Dark", "black" to "Black").forEach { (mode, label) ->
                val selected = settings.themeMode == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { scope.launch { store.setSetting("toolbox_dark", mode) } }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Text(
            "Accent Color Theme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        // Source: 12 accent preset buttons + "Custom color…" (hex via color picker).
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            ACCENTS.chunked(4).forEach { rowAccents ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowAccents.forEach { a ->
                        val selected = settings.accent == a.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(a.primary)
                                    .then(
                                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { scope.launch { store.setSetting("toolbox_accent", a.id) } }
                            )
                            Text(a.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        // Parity pass — "Custom color…" (source toggleCustomPicker) opens
        // the FULL HSV picker (__cp*), not just a hex field.
        var pickerOpen by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        try {
                            Color(android.graphics.Color.parseColor(settings.customAccent))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            OutlinedButton(onClick = { pickerOpen = !pickerOpen }) { Text("Custom color…") }
            if (settings.accent == "custom") {
                Text(
                    settings.customAccent.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (pickerOpen) {
            HsvColorPicker(
                initialHex = settings.customAccent,
                onApply = { hex ->
                    scope.launch {
                        // Source applyCustomColor/__applyCustomAccentVars.
                        store.setSetting("toolbox_custom_accent", hex)
                        store.setSetting("toolbox_accent", "custom")
                    }
                    pickerOpen = false
                    toast(context, "Custom accent applied.")
                }
            )
        }

        // ---- Home Screen Style (source section, setHomeView) -------------
        Text(
            "Home Screen Style",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            listOf(
                "list" to "List", "iphone" to "iPhone", "android" to "Android"
            ).forEach { (view, label) ->
                val selected = settings.homeView == view
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            scope.launch { store.setSetting("toolbox_home_view", view) }
                            toast(
                                context,
                                when (view) {
                                    "list" -> "Home style: List."
                                    "iphone" -> "Home style: iPhone pages."
                                    else -> "Home style: Android drawer."
                                }
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // ---- UI Size & Scale (source section, updateUIScale) -------------
        Text(
            "UI Size & Scale",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            androidx.compose.material3.Slider(
                value = settings.uiScale.toFloat(),
                onValueChange = { v ->
                    val stepped = (v / 5f).toInt() * 5
                    scope.launch { store.setSetting("toolbox_ui_scale", stepped.toString()) }
                },
                valueRange = 25f..150f,
                steps = 24,
                modifier = Modifier.weight(1f)
            )
            Text("${settings.uiScale}%", style = MaterialTheme.typography.bodyMedium)
            // Source resetUIScale().
            OutlinedButton(onClick = {
                scope.launch { store.setSetting("toolbox_ui_scale", "100") }
                toast(context, "UI scale reset to 100%.")
            }) { Text("Reset") }
        }
        Text(
            "Scales the whole app interface (the web version sets the root font size).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- Desktop Mode (source section, toggleDesktopMode) ------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Desktop Mode", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Windows, taskbar and Start menu — the desktop layout from the web version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = {
                val next = !settings.desktopMode
                scope.launch { store.setSetting("toolbox_desktop", next.toString()) }
                toast(context, if (next) "Desktop layout enabled." else "Desktop layout disabled.")
            }) { Text(if (settings.desktopMode) "Disable" else "Enable") }
        }

        // ---- Fullscreen (source toggleFullscreen, header/taskbar) -------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fullscreen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Hide the system bars (immersive mode), like the web fullscreen toggle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { FullscreenState.toggle() }) {
                Text(if (FullscreenState.active.value) "Exit" else "Enter")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sound", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Beeps for tally / shopping actions, as in the web version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.sound,
                onCheckedChange = { v -> scope.launch { store.setSound(v) } }
            )
        }

        Text(
            "API Keys",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Stored only on this device. The native app ships with no built-in keys — " +
                "paste your own here (Flight Tracker uses the Aviationstack key; " +
                "PDF AI OCR will use the Gemini keys in Phase 3).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = settings.aviationstackKey,
            onValueChange = { v -> scope.launch { store.setSetting("toolbox_aviationstack_key", v) } },
            label = { Text("Aviationstack API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = settings.geminiKey1,
            onValueChange = { v -> scope.launch { store.setSetting("toolbox_gemini_key_1", v) } },
            label = { Text("PDF Tools · Gemini API Key 1") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = settings.geminiKey2,
            onValueChange = { v -> scope.launch { store.setSetting("toolbox_gemini_key_2", v) } },
            label = { Text("PDF Tools · Gemini API Key 2") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        // Keep the themed background visible behind the password dots row.
        Box(modifier = Modifier.size(1.dp).background(Color.Transparent))

        SyncSettingsSection(store)

        ResetEverythingSection(store)

        // v1.0.5: version footer parity with the web editions, which show
        // "Toolbox · Web Edition · Version X" at the bottom of Settings.
        Text(
            "Toolbox · Android Edition · Version 1.0.6",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)
        )
    }
}

/**
 * Parity pass — "Reset Everything" (source resetSettings()): a danger
 * button, the source's confirmation text, then a wipe of exactly the keys
 * the source clears. If sync was configured, the wipe is pushed first
 * (best effort, 4-second cap, as in the source) so other devices do not
 * resurrect the data; sync credentials/interval/bookkeeping are cleared
 * after the push, again as in the source. The source then reloads the
 * page; natively the settings flow falls back to defaults (black theme,
 * indigo accent, iPhone home) and tool state re-reads as empty.
 */
@Composable
private fun ResetEverythingSection(store: AppStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync = remember { SyncManager.get(context.applicationContext, store) }
    var confirmOpen by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }

    Text(
        "Reset Everything",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp)
    )
    Text(
        "Erase all saved Toolbox data on this device and return every setting to its default.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Button(
        enabled = !resetting,
        onClick = { confirmOpen = true },
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error
        )
    ) { Text(if (resetting) "Resetting…" else "Reset Everything") }

    if (confirmOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Reset Everything?") },
            text = {
                Text(
                    "This permanently erases ALL saved data:\n" +
                        "• Theme, UI scale, desktop mode, tool order, home style, sound\n" +
                        "• PTO logs & settings, tally items, tracked times\n" +
                        "• Shopping list & saved price comparisons\n" +
                        "• Notes\n" +
                        "• Flight Tracker history\n" +
                        "• PDF preview scale\n" +
                        "• API key overrides\n" +
                        "• Sync secret token\n\n" +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    resetting = true
                    scope.launch {
                        // Source: if syncing, push the wipe first (4s cap)
                        // while the token is still present.
                        val token = store.snapshot(listOf(com.shawnrodis.toolbox.data.SYNC_TOKEN_KEY))[
                            com.shawnrodis.toolbox.data.SYNC_TOKEN_KEY
                        ]
                        if (!token.isNullOrEmpty()) {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(4000) { sync.pushNow() }
                            } catch (e: Exception) { }
                        }
                        store.clearKeys(
                            com.shawnrodis.toolbox.data.RESET_KEYS_PRIMARY
                        )
                        store.clearKeys(
                            com.shawnrodis.toolbox.data.RESET_KEYS_SYNC
                        )
                        resetting = false
                        toast(context, "Everything has been reset.")
                    }
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * "Sync Across Devices" — the Settings sync card:
 *  - Server URL is a user-entered field (stored under SYNC_URL_KEY,
 *    default empty; there is no built-in endpoint — a deliberate,
 *    user-directed deviation from the HTML build, whose endpoint is
 *    built in, made so the app can be published openly).
 *  - Token is a password field with a show/hide toggle
 *    (toggleSyncTokenVisibility). Saving applies syncSaveSettings()
 *    semantics via SyncManager.saveSettings (store URL + token, wipe
 *    meta+pending, schedule, startup tick) — an explicit Save button
 *    stands in for the web field's onchange so bookkeeping isn't wiped
 *    per keystroke.
 *  - Sync Now button + #sync-status line, and the Auto-sync interval
 *    select with the source's exact options.
 */
@Composable
private fun SyncSettingsSection(store: AppStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync = remember { SyncManager.get(context.applicationContext, store) }
    val storedUrl by store.stringState(SYNC_URL_KEY, "").collectAsState(initial = "")
    val storedToken by store.stringState(SYNC_TOKEN_KEY, "").collectAsState(initial = "")
    val intervalRaw by store.stringState(SYNC_INTERVAL_KEY, "").collectAsState(initial = "")
    val syncStatus by sync.status.collectAsState(initial = null)

    var urlInput by remember { mutableStateOf("") }
    var urlLoaded by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var tokenLoaded by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    LaunchedEffect(storedUrl) {
        if (!urlLoaded) {
            urlInput = storedUrl
            urlLoaded = true
        }
    }
    LaunchedEffect(storedToken) {
        if (!tokenLoaded) {
            tokenInput = storedToken
            tokenLoaded = true
        }
    }

    val intervalMs = syncGetInterval(intervalRaw)
    val intervalLabel = SYNC_INTERVAL_OPTIONS.find { it.first == intervalMs.toString() }?.second
        ?: SYNC_INTERVAL_OPTIONS.first().second
    val isConfigured = syncConfigured(storedUrl, storedToken)
    val statusText = syncStatus
        ?: if (isConfigured) "Configured"
        else "Not configured — enter your sync server address and token"

    Text(
        "Sync Across Devices",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp)
    )
    Text(
        "Mirror this device's data to your own sync server · " +
            "clear the URL or token to turn sync off",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    OutlinedTextField(
        value = urlInput,
        onValueChange = { urlInput = it },
        label = { Text("Sync Server URL") },
        placeholder = { Text("e.g. http://192.168.0.10:9000") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
    OutlinedTextField(
        value = tokenInput,
        onValueChange = { tokenInput = it },
        label = { Text("Sync Token") },
        placeholder = { Text("Secret token (matches toolbox-sync-server.py)") },
        singleLine = true,
        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { tokenVisible = !tokenVisible }) {
                Text(if (tokenVisible) "Hide" else "Show")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        Button(onClick = {
            scope.launch { sync.saveSettings(urlInput, tokenInput) }
        }) { Text("Save") }
        OutlinedButton(
            enabled = !syncing,
            onClick = {
                syncing = true
                scope.launch {
                    val msg = sync.syncNow()
                    toast(context, msg)
                    syncing = false
                }
            }
        ) { Text(if (syncing) "Syncing…" else "Sync Now") }
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Text("Auto-sync", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        PickerField(
            label = "Interval",
            options = SYNC_INTERVAL_OPTIONS.map { it.second },
            selected = intervalLabel,
            onSelect = { label ->
                val value = SYNC_INTERVAL_OPTIONS.find { it.second == label }?.first ?: "0"
                scope.launch { sync.saveInterval(value) }
            },
            modifier = Modifier.weight(1f)
        )
    }
    Text(
        "While the app is open it syncs on this interval, like the web page. " +
            "In the background Android enforces a 15-minute minimum, so shorter " +
            "intervals fully apply only while the app is open.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}
