package com.shawnrodis.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnrodis.toolbox.data.ACCENTS
import com.shawnrodis.toolbox.data.AppSettings
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.data.FB_CONFIG_KEY
import com.shawnrodis.toolbox.data.FB_EMAIL_KEY
import com.shawnrodis.toolbox.data.FB_REFRESH_KEY
import com.shawnrodis.toolbox.data.FbConfigParse
import com.shawnrodis.toolbox.data.SYNC_INTERVAL_KEY
import com.shawnrodis.toolbox.data.SYNC_INTERVAL_OPTIONS
import com.shawnrodis.toolbox.data.SYNC_PROVIDER_FIREBASE
import com.shawnrodis.toolbox.data.SYNC_PROVIDER_KEY
import com.shawnrodis.toolbox.data.SYNC_PROVIDER_OFF
import com.shawnrodis.toolbox.data.SYNC_PROVIDER_SERVER
import com.shawnrodis.toolbox.data.SYNC_TOKEN_KEY
import com.shawnrodis.toolbox.data.SYNC_URL_KEY
import com.shawnrodis.toolbox.data.SyncManager
import com.shawnrodis.toolbox.data.parseFirebaseConfig
import com.shawnrodis.toolbox.data.syncConfigured
import com.shawnrodis.toolbox.data.syncGetInterval
import com.shawnrodis.toolbox.data.syncProviderOf
import kotlinx.coroutines.launch

/**
 * Native Settings — v1.1.0 restyle (Workstream D): the screen is built
 * in the HTML Settings page's own visual language instead of stock
 * Material — one outer page card (rounded-2xl, bordered) holding
 * section cards (rounded-xl, slate-50 / zinc-950 fills, slate-200 /
 * zinc-800 borders) with the source's icon-box section headers, row
 * layouts (label + description left, control right), segmented
 * controls, pill buttons and field styling, using the source's exact
 * Tailwind palette values per theme mode. Section order, labels and
 * descriptions follow the source Settings markup:
 *   header (Toolbox / Settings & Preferences) → Accent Color Theme →
 *   Home Screen Style → UI Size & Scale → Desktop Mode → Fullscreen →
 *   Appearance Theme → Sounds & Haptics → API Keys → Sync Across
 *   Devices (+ Auto-sync) → Reset Everything → version footer.
 * Fullscreen and the Sound toggle are header/taskbar actions in the
 * source; they live here as native citizens of the same card design
 * (Sound since Phase 1, Fullscreen since the parity pass), and the
 * v1.1.0 Haptics toggle sits beside Sound. Functionality, storage keys
 * and behaviour are unchanged by the restyle.
 */

/** The source Settings palette (Tailwind slate/zinc values), per mode. */
private data class SettingsPalette(
    val pageBg: Color,
    val cardBg: Color,
    val border: Color,
    val iconBox: Color,
    val fieldBg: Color,
    val main: Color,
    val label: Color,
    val subtle: Color
)

private fun settingsPalette(themeMode: String): SettingsPalette = when (themeMode) {
    "light" -> SettingsPalette(
        pageBg = Color.White,
        cardBg = Color(0xFFF8FAFC),          // slate-50
        border = Color(0xFFE2E8F0),          // slate-200
        iconBox = Color(0xFFE2E8F0),         // slate-200
        fieldBg = Color.White,
        main = Color(0xFF0F172A),            // slate-900
        label = Color(0xFF334155),           // slate-700
        subtle = Color(0xFF94A3B8)           // slate-400
    )
    "dark" -> SettingsPalette(
        pageBg = Color(0xFF0B1526),          // source dark body
        cardBg = Color(0xFF09090B),          // zinc-950
        border = Color(0xFF27272A),          // zinc-800
        iconBox = Color(0xFF18181B),         // zinc-900
        fieldBg = Color.Black,
        main = Color.White,
        label = Color(0xFFD4D4D8),           // zinc-300
        subtle = Color(0xFFA1A1AA)           // zinc-400
    )
    else -> SettingsPalette(                 // "black"
        pageBg = Color.Black,
        cardBg = Color(0xFF09090B),
        border = Color(0xFF27272A),
        iconBox = Color(0xFF18181B),
        fieldBg = Color.Black,
        main = Color.White,
        label = Color(0xFFD4D4D8),
        subtle = Color(0xFFA1A1AA)
    )
}

/** Source section card: rounded-xl, bordered, p-3, space-y-2. */
@Composable
private fun SettingsCard(palette: SettingsPalette, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.cardBg)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        content = content
    )
}

/** Source card header: neutral icon box (rounded-lg) + bold title + hint. */
@Composable
private fun SettingsCardHeader(
    palette: SettingsPalette,
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.iconBox)
        ) {
            Icon(icon, contentDescription = null, tint = palette.label, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.main)
            Text(description, fontSize = 10.sp, color = palette.subtle)
        }
    }
}

/** Source pill button (px-3 py-1.5, rounded-xl, bold text-xs). */
@Composable
private fun SettingsPillButton(
    palette: SettingsPalette,
    text: String,
    accent: Boolean = false,
    container: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = container ?: if (accent) MaterialTheme.colorScheme.primary else palette.iconBox
    val fg = if (accent || container != null) Color.White else palette.label
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** Source segmented control (Appearance theme): bordered pill group. */
@Composable
private fun SettingsSegmented(
    palette: SettingsPalette,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.fieldBg)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .padding(2.dp)
    ) {
        options.forEach { opt ->
            val isSel = opt == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) accent else Color.Transparent)
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    opt, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (isSel) Color.White else palette.label
                )
            }
        }
    }
}

/** Source text field: rounded-xl bordered input, text-xs, px-3 py-2. */
@Composable
private fun SettingsField(
    palette: SettingsPalette,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    showToggle: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    val style = TextStyle(
        fontSize = 12.sp,
        color = palette.main,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.fieldBg)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 12.sp, color = palette.subtle)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = if (singleLine) 1 else 6,
                    textStyle = style,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = if (password && !visible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showToggle) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                    tint = palette.subtle,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { visible = !visible }
                        .padding(4.dp)
                )
            }
        }
    }
}

/** Source-style picker (Auto-sync select): field box + dropdown menu. */
@Composable
private fun SettingsPicker(
    palette: SettingsPalette,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.fieldBg)
                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected, fontSize = 12.sp, color = palette.main,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown, contentDescription = null,
                    tint = palette.subtle, modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 12.sp) },
                    onClick = { expanded = false; onSelect(opt) }
                )
            }
        }
    }
}

/** Field label (source: text-[11px] font-semibold). */
@Composable
private fun SettingsFieldLabel(palette: SettingsPalette, text: String) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.label,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

/** Hint line (source: text-[10px] text-slate-400). */
@Composable
private fun SettingsHint(palette: SettingsPalette, text: String) {
    Text(text, fontSize = 10.sp, color = palette.subtle)
}

@Composable
fun SettingsScreen(store: AppStore, onBack: () -> Unit) {
    val settings by store.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val palette = settingsPalette(settings.themeMode)
    val accent = MaterialTheme.colorScheme.primary

    ToolScreen(title = "Settings & Preferences", onBack = onBack) {
        // ---- Source page card: rounded-2xl, bordered, p-4 ------------
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.pageBg)
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            // Brand header (source: accent box + "Toolbox" / "Shawn Rodis").
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                ) {
                    Icon(Icons.Filled.Work, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Toolbox", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = palette.main)
                    Text("Shawn Rodis", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.subtle)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
            // Settings header (source: accent-icon-box gear + titles).
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolIconBox("fa-gear", boxDp = 40.dp, glyphDp = 20.dp, cornerDp = 12.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Settings & Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.main)
                    Text("Customize appearance & manage tools", fontSize = 11.sp, color = palette.subtle)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))

            // ---- Accent Color Theme -----------------------------------
            SettingsCard(palette) {
                SettingsCardHeader(palette, Icons.Filled.Palette, "Accent Color Theme", "Choose your app's accent color")
                ACCENTS.chunked(4).forEach { rowAccents ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowAccents.forEach { a ->
                            val selected = settings.accent == a.id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) accent else palette.border,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .background(if (selected) accent.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable { scope.launch { store.setSetting("toolbox_accent", a.id) } }
                                    .padding(vertical = 8.dp)
                            ) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(a.primary))
                                Spacer(Modifier.height(4.dp))
                                Text(a.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = palette.main)
                            }
                        }
                    }
                }
                // Source "Custom color…" row (rainbow conic dot) + the
                // full HSV picker (toggleCustomPicker/__cp*) below it.
                var pickerOpen by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .clickable { pickerOpen = !pickerOpen }
                        .padding(vertical = 8.dp)
                ) {
                    Box(
                        Modifier.size(16.dp).clip(CircleShape).background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                                    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                                    Color(0xFFFF0000)
                                )
                            )
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Custom color…", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = palette.main)
                    if (settings.accent == "custom") {
                        Spacer(Modifier.width(8.dp))
                        Text(settings.customAccent.uppercase(), fontSize = 10.sp, color = palette.subtle)
                    }
                }
                if (pickerOpen) {
                    HsvColorPicker(
                        initialHex = settings.customAccent,
                        onApply = { hex ->
                            scope.launch {
                                store.setSetting("toolbox_custom_accent", hex)
                                store.setSetting("toolbox_accent", "custom")
                            }
                            pickerOpen = false
                            toast(context, "Custom accent applied.")
                        }
                    )
                }
            }

            // ---- Home Screen Style -------------------------------------
            SettingsCard(palette) {
                SettingsCardHeader(palette, Icons.Filled.GridView, "Home Screen Style", "How tools are shown on mobile")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("list", "List", Icons.Filled.List),
                        Triple("iphone", "iPhone", Icons.Filled.GridView),
                        Triple("android", "Android", Icons.Filled.Apps)
                    ).forEach { (view, label, icon) ->
                        val selected = settings.homeView == view
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) accent else palette.border,
                                    RoundedCornerShape(12.dp)
                                )
                                .background(if (selected) accent.copy(alpha = 0.08f) else Color.Transparent)
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
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(icon, contentDescription = null, tint = palette.main, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = palette.main)
                        }
                    }
                }
                SettingsHint(palette, "List scrolls up & down · iPhone (default) swipes left & right · Android is an app-drawer grid")
            }

            // ---- UI Size & Scale ---------------------------------------
            SettingsCard(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.iconBox)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = palette.label, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "UI Size & Scale", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = palette.main, modifier = Modifier.weight(1f)
                    )
                    Text("${settings.uiScale}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.RotateLeft, contentDescription = "Reset to 100%",
                        tint = palette.subtle,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch { store.setSetting("toolbox_ui_scale", "100") }
                                toast(context, "UI scale reset to 100%.")
                            }
                            .padding(5.dp)
                    )
                }
                Slider(
                    value = settings.uiScale.toFloat(),
                    onValueChange = { v ->
                        val stepped = (v / 5f).toInt() * 5
                        scope.launch { store.setSetting("toolbox_ui_scale", stepped.toString()) }
                    },
                    valueRange = 25f..150f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = palette.iconBox
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- Desktop Mode (source row card) -------------------------
            SettingsCard(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.iconBox)
                    ) {
                        Icon(Icons.Filled.DesktopWindows, contentDescription = null, tint = palette.label, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Desktop Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.main)
                        Text("Expand layout width", fontSize = 10.sp, color = palette.subtle)
                    }
                    SettingsPillButton(palette, if (settings.desktopMode) "Disable" else "Enable") {
                        val next = !settings.desktopMode
                        scope.launch { store.setSetting("toolbox_desktop", next.toString()) }
                        toast(context, if (next) "Desktop layout enabled." else "Desktop layout disabled.")
                    }
                }
            }

            // ---- Fullscreen (header/taskbar action in the source;
            // a native citizen row in the same card style) ---------------
            SettingsCard(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.iconBox)
                    ) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = null, tint = palette.label, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fullscreen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.main)
                        Text("Hide the system bars (immersive mode), like the web fullscreen toggle.", fontSize = 10.sp, color = palette.subtle)
                    }
                    SettingsPillButton(palette, if (FullscreenState.active.value) "Exit" else "Enter") {
                        FullscreenState.toggle()
                    }
                }
            }

            // ---- Appearance Theme (source row card + segmented) ---------
            SettingsCard(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.iconBox)
                    ) {
                        Icon(Icons.Filled.Contrast, contentDescription = null, tint = palette.label, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Appearance Theme", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.main)
                        Text("Light, dark, or pure black", fontSize = 10.sp, color = palette.subtle)
                    }
                    SettingsSegmented(
                        palette,
                        options = listOf("Light", "Dark", "Black"),
                        selected = when (settings.themeMode) {
                            "light" -> "Light"; "dark" -> "Dark"; else -> "Black"
                        },
                        onSelect = { label ->
                            scope.launch {
                                store.setSetting(
                                    "toolbox_dark",
                                    when (label) { "Light" -> "light"; "Dark" -> "dark"; else -> "black" }
                                )
                            }
                        }
                    )
                }
            }

            // ---- Sounds & Haptics (v1.1.0: the Haptics toggle sits
            // beside Sound; both are native-citizen rows — in the source
            // sound is toggled from the header/taskbar buttons) ---------
            SettingsCard(palette) {
                SettingsCardHeader(palette, Icons.Filled.VolumeUp, "Sounds & Haptics", "Tap feedback for tally / shopping actions")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sound", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.main)
                        Text("Beeps for tally / shopping actions, as in the web version.", fontSize = 10.sp, color = palette.subtle)
                    }
                    SettingsPillButton(palette, if (settings.sound) "On" else "Off", accent = settings.sound) {
                        scope.launch { store.setSound(!settings.sound) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptics", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.main)
                        Text("A light vibration alongside the tap beeps.", fontSize = 10.sp, color = palette.subtle)
                    }
                    SettingsPillButton(palette, if (settings.haptics) "On" else "Off", accent = settings.haptics) {
                        scope.launch { store.setHaptics(!settings.haptics) }
                    }
                }
            }

            // ---- API Keys ------------------------------------------------
            SettingsCard(palette) {
                SettingsCardHeader(palette, Icons.Filled.VpnKey, "API Keys", "Keys for tools that need them · stored only on this device")
                Column {
                    SettingsFieldLabel(palette, "Flight Tracker · Aviationstack")
                    SettingsField(
                        palette,
                        value = settings.aviationstackKey,
                        onValueChange = { v -> scope.launch { store.setApiKey("toolbox_aviationstack_key", v) } },
                        placeholder = "Paste your own Aviationstack key",
                        password = true, mono = true, showToggle = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsHint(palette, "Get a free key at aviationstack.com. Your key is stored only on this device.")
                }
                Column {
                    SettingsFieldLabel(palette, "PDF Tools · Gemini API Key 1")
                    SettingsField(
                        palette,
                        value = settings.geminiKey1,
                        onValueChange = { v -> scope.launch { store.setApiKey("toolbox_gemini_key_1", v) } },
                        placeholder = "Paste your own Gemini key 1",
                        password = true, mono = true, showToggle = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column {
                    SettingsFieldLabel(palette, "PDF Tools · Gemini API Key 2")
                    SettingsField(
                        palette,
                        value = settings.geminiKey2,
                        onValueChange = { v -> scope.launch { store.setApiKey("toolbox_gemini_key_2", v) } },
                        placeholder = "Paste your own Gemini key 2",
                        password = true, mono = true, showToggle = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsHint(palette, "Used by AI Vision OCR with failover (key 1, then key 2). Your keys are stored only on this device.")
                }
            }

            // ---- Sync Across Devices ------------------------------------
            SyncSettingsCard(store, palette)

            // ---- Reset Everything ---------------------------------------
            ResetEverythingCard(store, palette)
        }

        // v1.0.5: version footer parity with the web editions.
        Text(
            "Toolbox · Android Edition · Version 1.1.1",
            fontSize = 11.sp,
            color = palette.subtle,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
        )
    }
}

/**
 * "Sync Across Devices" card — v1.1.0: a provider segmented control
 * (Off / Server / Firebase, in the page's own segmented style) above
 * the provider-specific fields; the Auto-sync picker and status line
 * are shared. The Own-server fields keep the exact v1.0.3 behaviour
 * (user-entered URL + token, explicit Save = syncSaveSettings
 * semantics). The Firebase block takes ONE pasted web config (JSON or
 * the console's firebaseConfig snippet), then an email/password sync
 * account (sign up / sign in / sign out) — see FirebaseSync.kt and
 * FIREBASE_SYNC.md. No Firebase SDK is involved: REST only.
 */
@Composable
private fun SyncSettingsCard(store: AppStore, palette: SettingsPalette) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync = remember { SyncManager.get(context.applicationContext, store) }
    val providerRaw by store.stringState(SYNC_PROVIDER_KEY, "").collectAsState(initial = "")
    val provider = syncProviderOf(providerRaw)
    val storedUrl by store.stringState(SYNC_URL_KEY, "").collectAsState(initial = "")
    val storedToken by store.stringState(SYNC_TOKEN_KEY, "").collectAsState(initial = "")
    val storedConfig by store.stringState(FB_CONFIG_KEY, "").collectAsState(initial = "")
    val storedEmail by store.stringState(FB_EMAIL_KEY, "").collectAsState(initial = "")
    val storedRefresh by store.stringState(FB_REFRESH_KEY, "").collectAsState(initial = "")
    val intervalRaw by store.stringState(SYNC_INTERVAL_KEY, "").collectAsState(initial = "")
    val syncStatus by sync.status.collectAsState(initial = null)

    var urlInput by remember { mutableStateOf("") }
    var urlLoaded by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var tokenLoaded by remember { mutableStateOf(false) }
    var configInput by remember { mutableStateOf("") }
    var configLoaded by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var emailLoaded by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(storedUrl) { if (!urlLoaded) { urlInput = storedUrl; urlLoaded = true } }
    LaunchedEffect(storedToken) { if (!tokenLoaded) { tokenInput = storedToken; tokenLoaded = true } }
    LaunchedEffect(storedConfig) { if (!configLoaded) { configInput = storedConfig; configLoaded = true } }
    LaunchedEffect(storedEmail) { if (!emailLoaded) { emailInput = storedEmail; emailLoaded = true } }

    val intervalMs = syncGetInterval(intervalRaw)
    val intervalLabel = SYNC_INTERVAL_OPTIONS.find { it.first == intervalMs.toString() }?.second
        ?: SYNC_INTERVAL_OPTIONS.first().second
    val serverConfigured = syncConfigured(storedUrl, storedToken)
    val fbParse = parseFirebaseConfig(storedConfig)
    val fbConfigOk = fbParse is FbConfigParse.Ok
    val fbConfig = (fbParse as? FbConfigParse.Ok)?.config
    val fbSignedIn = fbConfigOk && storedRefresh.isNotEmpty()
    val statusText = syncStatus ?: when (provider) {
        SYNC_PROVIDER_OFF -> "Sync is off"
        SYNC_PROVIDER_FIREBASE -> when {
            !fbConfigOk -> "Not configured — paste your Firebase project config below"
            !fbSignedIn -> "Not signed in — create your sync account or sign in below"
            else -> "Signed in as $storedEmail"
        }
        else -> if (serverConfigured) "Configured"
        else "Not configured — enter your sync server address and token"
    }

    SettingsCard(palette) {
        SettingsCardHeader(
            palette, Icons.Filled.CloudUpload, "Sync Across Devices",
            "Mirror this device's data to your own sync server or a free Firebase project"
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Provider", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = palette.main, modifier = Modifier.weight(1f)
            )
            SettingsSegmented(
                palette,
                options = listOf("Off", "Server", "Firebase"),
                selected = when (provider) {
                    SYNC_PROVIDER_OFF -> "Off"
                    SYNC_PROVIDER_FIREBASE -> "Firebase"
                    else -> "Server"
                },
                onSelect = { label ->
                    val p = when (label) {
                        "Off" -> SYNC_PROVIDER_OFF
                        "Firebase" -> SYNC_PROVIDER_FIREBASE
                        else -> SYNC_PROVIDER_SERVER
                    }
                    scope.launch { toast(context, sync.setProvider(p)) }
                }
            )
        }

        if (provider == SYNC_PROVIDER_SERVER) {
            SettingsField(
                palette,
                value = urlInput,
                onValueChange = { urlInput = it },
                placeholder = "Sync Server URL (e.g. http://192.168.1.10:9000)",
                modifier = Modifier.fillMaxWidth()
            )
            SettingsField(
                palette,
                value = tokenInput,
                onValueChange = { tokenInput = it },
                placeholder = "Secret token (matches toolbox-sync-server.py)",
                password = true, showToggle = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsPillButton(palette, "Save", accent = true) {
                    scope.launch { sync.saveSettings(urlInput, tokenInput) }
                }
                Text(statusText, fontSize = 10.sp, color = palette.subtle)
            }
        }

        if (provider == SYNC_PROVIDER_FIREBASE) {
            Column {
                SettingsFieldLabel(palette, "Firebase config")
                SettingsField(
                    palette,
                    value = configInput,
                    onValueChange = { configInput = it },
                    placeholder = "Paste your Firebase config — the JSON object, or the whole firebaseConfig = { … } snippet",
                    singleLine = false, minLines = 3, mono = true,
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    fbConfigOk -> SettingsHint(
                        palette,
                        "Project: ${fbConfig?.projectId ?: "—"} · Database: ${
                            fbConfig?.databaseURL?.removePrefix("https://") ?: "—"
                        }"
                    )
                    configInput.isNotBlank() -> Text(
                        "Missing: ${(fbParse as FbConfigParse.Invalid).missing.joinToString(", ")}",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.error
                    )
                    else -> SettingsHint(palette, "From your Firebase project settings — setup steps are in FIREBASE_SYNC.md in the repo.")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsPillButton(palette, "Save config", accent = true, enabled = !busy) {
                    busy = true
                    scope.launch {
                        toast(context, sync.firebaseSaveConfig(configInput))
                        busy = false
                    }
                }
            }
            if (fbSignedIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Signed in as $storedEmail", fontSize = 11.sp,
                        color = palette.main, modifier = Modifier.weight(1f)
                    )
                    SettingsPillButton(palette, "Sign out") {
                        scope.launch {
                            sync.firebaseSignOut()
                            toast(context, "Signed out of Firebase.")
                        }
                    }
                }
            } else {
                Column {
                    SettingsFieldLabel(palette, "Sync account")
                    SettingsField(
                        palette,
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        placeholder = "Email",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SettingsField(
                    palette,
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    placeholder = "Password",
                    password = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsPillButton(palette, "Sign up", accent = true, enabled = !busy) {
                        busy = true
                        scope.launch {
                            toast(context, sync.firebaseSignUp(emailInput, passwordInput))
                            passwordInput = ""
                            busy = false
                        }
                    }
                    SettingsPillButton(palette, "Sign in", enabled = !busy) {
                        busy = true
                        scope.launch {
                            toast(context, sync.firebaseSignIn(emailInput, passwordInput))
                            passwordInput = ""
                            busy = false
                        }
                    }
                }
                SettingsHint(palette, "One free account per person — it keeps your data separate from everyone else's (see FIREBASE_SYNC.md).")
            }
            Text(statusText, fontSize = 10.sp, color = palette.subtle)
        }

        if (provider == SYNC_PROVIDER_OFF) {
            Text(statusText, fontSize = 10.sp, color = palette.subtle)
        }

        if (provider != SYNC_PROVIDER_OFF) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsPillButton(palette, if (busy) "Syncing…" else "Sync Now", accent = true, enabled = !busy) {
                    busy = true
                    scope.launch {
                        val msg = sync.syncNow()
                        toast(context, msg)
                        busy = false
                    }
                }
            }
        }

        // ---- Auto-sync (shared by both providers) ----------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Auto-sync", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = palette.main, modifier = Modifier.weight(1f)
            )
            SettingsPicker(
                palette,
                options = SYNC_INTERVAL_OPTIONS.map { it.second },
                selected = intervalLabel,
                onSelect = { label ->
                    val value = SYNC_INTERVAL_OPTIONS.find { it.second == label }?.first ?: "0"
                    scope.launch { sync.saveInterval(value) }
                },
                modifier = Modifier.weight(1f)
            )
        }
        SettingsHint(
            palette,
            "While the app is open it syncs on this interval, like the web page. " +
                "In the background Android enforces a 15-minute minimum, so shorter " +
                "intervals fully apply only while the app is open."
        )
    }
}

/**
 * "Reset Everything" card (source resetSettings()): a danger row in
 * the page's card style, the source's confirmation text, then a wipe of
 * exactly the keys the source clears. If sync is configured (either
 * provider), the wipe is pushed first (best effort, 4-second cap) so
 * other devices do not resurrect the data; sync credentials/interval/
 * bookkeeping are cleared after the push, again as in the source.
 */
@Composable
private fun ResetEverythingCard(store: AppStore, palette: SettingsPalette) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync = remember { SyncManager.get(context.applicationContext, store) }
    var confirmOpen by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val rose = Color(0xFFF43F5E)

    SettingsCard(palette) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(rose.copy(alpha = 0.10f))
            ) {
                Icon(Icons.Filled.RotateLeft, contentDescription = null, tint = rose, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Reset Everything", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.main)
                Text(
                    "Erases ALL saved data: theme, UI scale, tool order, PTO logs, tally items, tracked times, saved comparisons, Flight Tracker history, PDF scale, saved API keys",
                    fontSize = 10.sp, color = palette.subtle
                )
            }
            SettingsPillButton(
                palette,
                if (resetting) "Resetting…" else "Reset Everything",
                container = Color(0xFFE11D48),
                enabled = !resetting
            ) { confirmOpen = true }
        }
    }

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
                androidx.compose.material3.TextButton(onClick = {
                    confirmOpen = false
                    resetting = true
                    scope.launch {
                        // Best-effort push of the wipe first (4s cap)
                        // through whichever provider is active — pushNow
                        // is a no-op when sync is unconfigured.
                        try {
                            kotlinx.coroutines.withTimeoutOrNull(4000) { sync.pushNow() }
                        } catch (e: Exception) { }
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
                androidx.compose.material3.TextButton(onClick = { confirmOpen = false }) { Text("Cancel") }
            }
        )
    }
}
