package com.shawnrodis.toolbox.ui

import android.app.Activity
import android.util.DisplayMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnrodis.toolbox.data.AppSettings
import com.shawnrodis.toolbox.data.AppStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Parity pass — DESKTOP MODE, the native port of the source's wm* window
 * manager (27 functions) + desktop chrome:
 *
 *  - toggleDesktopMode(): a Desktop shell replaces the app shell when the
 *    toolbox_desktop setting is on (Settings toggle or auto-orientation).
 *  - Desktop icons ARE the homescreen grid in "list-forced" desktop form:
 *    here they render as the source's __homeTileHTML tiles, in the display
 *    order of sortedToolsForDisplay(). Saved positions live in
 *    toolbox_desktop_icon_pos ({tabId:{x,y}}); options in
 *    toolbox_desktop_icon_opts ({snap:true, drag:true} — the source's
 *    defaults from getDesktopIconOpts()).
 *  - Icon interactions from renderHomescreenGrid(): tap opens/toggles the
 *    tool's window (wmToggle), long-press shows the icon context menu
 *    (icon-ctx-menu: "Pin to taskbar" — toggles toolbox_taskbar_pins),
 *    drag moves the icon when opts.drag (iconDragStart/Move/End), snapped
 *    to the icon grid when opts.snap (iconCellSize 108x84 in source; the
 *    native tile cell is 88x104dp). Long-press on blank desktop shows the
 *    desktop context menu (desktop-ctx-menu: Arrange icons by name /
 *    Align icons to grid / Allow moving icons).
 *  - Windows (createWindow/wmOpen/wmClose/wmMinimize/wmMaximize/wmRestore/
 *    wmFocus/wmToggle/wmShowDesktop/wmCloseAll): titlebar with the tool's
 *    icon + name and Min/Max/Close buttons, double-click titlebar toggles
 *    maximize, drag moves, drag-to-edge snaps (left/right halves, top =
 *    snap 'max', corners = quarters, per wmSnap/snapRect), SE corner +
 *    right/bottom edge resize handles (the source has 8 resize handles;
 *    the native port implements the corner + the two edges that matter on
 *    touch — named simplification), focus z-order, cascade default rect
 *    (defaultRect, adapted to phone-sized areas).
 *  - Taskbar (renderTaskbarPins()/refreshTaskbarState()): left = UI scale
 *    slider (wm-ui-scale-slider, same 25..150 single source of truth) +
 *    reset; centre = Start button (toggleStartMenu), Desktop (house) pin,
 *    user pins in order, then open windows not pinned — focused window
 *    shows an active indicator; snap-layouts button (toggleSnapPanel);
 *    right tray = theme segment (Light/Dark/Black), desktop clock
 *    (updateDesktopClock, click opens Time Converter window), sound
 *    toggle (toggleToolboxSound), fullscreen toggle, Settings, Close-all.
 *  - Start menu (renderStartMenu): "Desktop" entry + all tools with
 *    descriptions, filtered by the search field.
 *  - Snap panel (buildSnapPanel/applySnapLayout): the source's six
 *    SNAP_LAYOUTS (Maximize, Side by side, Stacked, 3 columns, Main + 2,
 *    2x2 grid) applied to the open, non-minimized windows in focus order;
 *    one window + a multi-cell layout maximizes it, as in the source.
 *  - Screen widget (updateScreenWidget/measureRefreshRate): top-right
 *    card with the physical resolution and the display refresh rate
 *    (native equivalent: getRealMetrics + Display.getRefreshRate, snapped
 *    to the source's common-rate list).
 *  - Window open/close animations (wmAnimate*) are simplified to instant
 *    state changes — named simplification; all behaviours are present.
 */

/** Window record (source's windows[tabId] objects). Fields are snapshot
 * state so mutating geometry/flags recomposes the desktop, like the
 * source's applyRect()/style updates. */
private class WinState(
    val route: String,
    x0: Float, y0: Float, w0: Float, h0: Float, z0: Int = 0
) {
    var x by mutableStateOf(x0)
    var y by mutableStateOf(y0)
    var w by mutableStateOf(w0)
    var h by mutableStateOf(h0)
    var minimized by mutableStateOf(false)
    var maximized by mutableStateOf(false)
    var snap by mutableStateOf<String?>(null)
    var prev: FloatArray? = null
    var z by mutableStateOf(z0)
}

private val SNAP_LAYOUTS: List<Pair<String, List<FloatArray>>> = listOf(
    "Maximize" to listOf(floatArrayOf(0f, 0f, 1f, 1f)),
    "Side by side" to listOf(floatArrayOf(0f, 0f, 0.5f, 1f), floatArrayOf(0.5f, 0f, 0.5f, 1f)),
    "Stacked" to listOf(floatArrayOf(0f, 0f, 1f, 0.5f), floatArrayOf(0f, 0.5f, 1f, 0.5f)),
    "3 columns" to listOf(
        floatArrayOf(0f, 0f, 1f / 3f, 1f), floatArrayOf(1f / 3f, 0f, 1f / 3f, 1f),
        floatArrayOf(2f / 3f, 0f, 1f / 3f, 1f)
    ),
    "Main + 2" to listOf(
        floatArrayOf(0f, 0f, 0.5f, 1f), floatArrayOf(0.5f, 0f, 0.5f, 0.5f),
        floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
    ),
    "2 × 2 grid" to listOf(
        floatArrayOf(0f, 0f, 0.5f, 0.5f), floatArrayOf(0.5f, 0f, 0.5f, 0.5f),
        floatArrayOf(0f, 0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
    )
)

/**
 * v1.0.1 — the source's Desktop Mode DISPLAY SCALING (wmApplyScale):
 * `s = clamp(min(innerWidth/1600, innerHeight/900), 0.55, 1.3)`, rounded to
 * 2dp, applied as the CSS var --wm-scale to ALL desktop chrome (taskbar,
 * icon cards, titlebars, menus, widget — ~121 uses in the source CSS).
 * "Window x/y/w/h geometry stays in real CSS px — only chrome uses
 * --wm-scale." Native equivalent: [s] sizes chrome in dp (≈ CSS px);
 * window content is NOT rescaled. This is independent of the Settings
 * UI scale (toolbox_ui_scale), which is an app-wide density factor that
 * everything (including this chrome) already lives under.
 * On Shawn's 2340×1080 phone (~780×360dp landscape) s hits the 0.55 floor,
 * matching the HTML's compact chrome; v1.0.0 shipped unscaled (s = 1)
 * chrome, which rendered far too large.
 */
internal fun wmScaleFor(widthDp: Float, heightDp: Float): Float {
    val raw = minOf(widthDp / 1600f, heightDp / 900f).coerceIn(0.55f, 1.3f)
    return (raw * 100).roundToInt() / 100f
}

/** Source TASKBAR_H / desktopArea(): the taskbar is 54 * s tall. */
internal const val WM_TASKBAR_BASE = 54f

/** Source MIN_W / MIN_H for windows. */
internal const val WM_MIN_W = 320f
internal const val WM_MIN_H = 220f

/**
 * v1.0.2 (defect D) — snap-panel sizing maths (pure, unit-tested).
 * v1.0.1 fixed the panel at 320·s wide, but the six layout tiles' own
 * intrinsic width is ~440·s (a 44·s diagram + label each), so the tile
 * row overflowed the panel at EVERY scale and the rightmost tiles were
 * clipped half off the panel's edge. The panel is now sized FROM its
 * content — fixed-width tiles, scaled gaps/padding — and clamped to
 * the desktop area minus a margin on each side; when the clamped width
 * cannot hold all six tiles, [snapTilesPerRow] drops and the panel
 * wraps them onto a second row instead of clipping.
 */
/** One snap tile's fixed width (unscaled dp, × s): 44 diagram + padding + label room. */
internal const val SNAP_TILE_W = 66f
/** Gap between snap tiles (unscaled dp, × s). */
internal const val SNAP_TILE_GAP = 8f
/** Snap panel inner padding, each side (unscaled dp, × s). */
internal const val SNAP_PANEL_PAD = 12f
/** Min clearance between the snap panel and each desktop-area edge (dp). */
internal const val SNAP_PANEL_MARGIN = 8f

/** Panel width = content width, clamped so the panel fits inside the area. */
internal fun snapPanelWidthDp(s: Float, tileCount: Int, areaW: Float): Float {
    val desired = (2 * SNAP_PANEL_PAD + tileCount * SNAP_TILE_W +
        (tileCount - 1).coerceAtLeast(0) * SNAP_TILE_GAP) * s
    val maxW = (areaW - 2 * SNAP_PANEL_MARGIN).coerceAtLeast(0f)
    return minOf(desired, maxW)
}

/** Tiles per row for a (possibly clamped) panel width; wraps below 6. */
internal fun snapTilesPerRow(s: Float, tileCount: Int, panelW: Float): Int {
    if (tileCount <= 0) return 1
    val inner = panelW - 2 * SNAP_PANEL_PAD * s
    val step = (SNAP_TILE_W + SNAP_TILE_GAP) * s
    if (step <= 0f) return tileCount
    // +epsilon: an exactly-fitting row (e.g. 6 tiles at s = 0.55) can
    // land a hair under the integer in float32 and would floor away.
    val per = ((inner + SNAP_TILE_GAP * s) / step + 0.001f).toInt()
    return per.coerceIn(1, tileCount)
}

/**
 * Source iconCellSize(): cell = icon card + 6*s gap. The desktop icon card
 * itself is 108*s wide × 100*s high (source CSS), so the cell is
 * 114*s × 106*s.
 */
internal fun wmIconCell(s: Float): Pair<Float, Float> = (108f + 6f) * s to (100f + 6f) * s

internal data class WmRect(val x: Float, val y: Float, val w: Float, val h: Float, val cascadeReset: Boolean)

/**
 * Source defaultRect(), verbatim math (dp ≈ CSS px):
 * w = clamp(floor(areaW*0.58), 430, 780), h = clamp(floor(areaH*0.72), 360, 600),
 * then w = min(w, max(MIN_W, areaW−40)), h = min(h, max(MIN_H, areaH−40));
 * centred with a 36px cascade step (y offset −24); if the rect would
 * overflow the area's 10px margin, the cascade resets to the plain centre.
 */
internal fun wmDefaultRect(areaW: Float, areaH: Float, cascadeN: Int): WmRect {
    fun clampF(v: Float, lo: Float, hi: Float) = maxOf(lo, minOf(hi, v))
    var w = clampF(kotlin.math.floor(areaW * 0.58f), 430f, 780f)
    var h = clampF(kotlin.math.floor(areaH * 0.72f), 360f, 600f)
    w = minOf(w, maxOf(WM_MIN_W, areaW - 40f))
    h = minOf(h, maxOf(WM_MIN_H, areaH - 40f))
    var x = maxOf(10f, kotlin.math.floor((areaW - w) / 2f) + cascadeN * 36f)
    var y = maxOf(10f, kotlin.math.floor((areaH - h) / 2f) - 24f + cascadeN * 36f)
    var reset = false
    if (x + w > areaW - 10f || y + h > areaH - 10f) {
        reset = true
        x = maxOf(10f, kotlin.math.floor((areaW - w) / 2f))
        y = maxOf(10f, kotlin.math.floor((areaH - h) / 2f) - 24f)
    }
    return WmRect(x, y, w, h, reset)
}

private fun parseIconPos(json: String): Map<String, Pair<Float, Float>> = try {
    val o = JSONObject(json)
    val out = mutableMapOf<String, Pair<Float, Float>>()
    o.keys().forEach { k ->
        val p = o.getJSONObject(k)
        out[k] = p.optDouble("x", 0.0).toFloat() to p.optDouble("y", 0.0).toFloat()
    }
    out
} catch (e: Exception) {
    emptyMap()
}

private fun iconPosToJson(map: Map<String, Pair<Float, Float>>): String {
    val o = JSONObject()
    map.forEach { (k, v) ->
        o.put(k, JSONObject().put("x", v.first.toDouble()).put("y", v.second.toDouble()))
    }
    return o.toString()
}

private fun parseIconOpts(json: String): Pair<Boolean, Boolean> = try {
    val o = JSONObject(json)
    (if (o.has("snap")) o.optBoolean("snap", true) else true) to
        (if (o.has("drag")) o.optBoolean("drag", true) else true)
} catch (e: Exception) {
    true to true
}

@Composable
fun DesktopShell(
    store: AppStore,
    settings: AppSettings,
    toolContent: @Composable (route: String, onCloseWindow: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // v1.0.1 (defect #3): the source's wmApplyScale() — desktop chrome
    // scales with resolution (1600x900 = 1.0, clamped [0.55, 1.3]); window
    // geometry stays in real dp. Shawn's 2340x1080 phone is ~780x360dp
    // landscape, so s = 0.55 (the floor) — the compact HTML chrome.
    val config = LocalConfiguration.current
    val s = wmScaleFor(config.screenWidthDp.toFloat(), config.screenHeightDp.toFloat())
    val (cellW, cellH) = wmIconCell(s)

    val pinsJson by store.stringState("toolbox_taskbar_pins", "[]").collectAsState(initial = "[]")
    val pins = parsePins(pinsJson)
    val iconPosJson by store.stringState("toolbox_desktop_icon_pos", "{}").collectAsState(initial = "{}")
    val iconPos = parseIconPos(iconPosJson)
    val iconOptsJson by store.stringState("toolbox_desktop_icon_opts", "{}").collectAsState(initial = "{}")
    val (optSnap, optDrag) = parseIconOpts(iconOptsJson)

    val windows = remember { mutableStateListOf<WinState>() }
    val focusOrder = remember { mutableStateListOf<String>() }
    var zCounter by remember { mutableStateOf(10) }
    var cascade by remember { mutableStateOf(0) }
    var startOpen by remember { mutableStateOf(false) }
    var snapOpen by remember { mutableStateOf(false) }
    var iconCtxRoute by remember { mutableStateOf<String?>(null) }
    var desktopCtxOpen by remember { mutableStateOf(false) }

    var clockText by remember { mutableStateOf("--:--") }
    LaunchedEffect(Unit) {
        // Source updateDesktopClock(): "h:mm AM/PM", ticking.
        while (true) {
            clockText = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            delay(1000)
        }
    }

    fun savePins(newPins: List<String>) {
        scope.launch { store.setStringState("toolbox_taskbar_pins", pinsToJson(newPins)) }
    }
    fun saveIconPos(map: Map<String, Pair<Float, Float>>) {
        scope.launch { store.setStringState("toolbox_desktop_icon_pos", iconPosToJson(map)) }
    }
    fun saveIconOpts(snap: Boolean, drag: Boolean) {
        scope.launch {
            store.setStringState(
                "toolbox_desktop_icon_opts",
                JSONObject().put("snap", snap).put("drag", drag).toString()
            )
        }
    }

    // Latest desktop-area size, kept current by the layout pass so taskbar /
    // Start-menu handlers (composed outside the area Box) can open windows
    // with correct default rects.
    var lastAreaW by remember { mutableStateOf(360f) }
    var lastAreaH by remember { mutableStateOf(600f) }

    fun focusedRoute(): String? = focusOrder.lastOrNull { r ->
        windows.any { it.route == r && !it.minimized }
    }

    fun wmFocus(route: String) {
        val w = windows.find { it.route == route } ?: return
        w.z = ++zCounter
        focusOrder.remove(route); focusOrder.add(route)
    }

    fun wmOpen(route: String, areaW: Float, areaH: Float) {
        val existing = windows.find { it.route == route }
        if (existing != null) {
            if (existing.minimized) {
                existing.minimized = false
            }
            wmFocus(route)
            return
        }
        // Source defaultRect(): exact source math (see wmDefaultRect) —
        // v1.0.1 replaced the phone-adapted approximation (92%/78%).
        val r = wmDefaultRect(areaW, areaH, cascade)
        cascade = if (r.cascadeReset) 1 else cascade + 1
        windows.add(WinState(route, r.x, r.y, r.w, r.h, z0 = ++zCounter))
        focusOrder.remove(route); focusOrder.add(route)
    }

    // Source wmToggle(): home -> show desktop; focused -> minimize;
    // minimized/closed -> open; unfocused -> focus.
    fun wmToggle(route: String, areaW: Float, areaH: Float) {
        if (route == "home") {
            windows.forEach { if (!it.minimized) it.minimized = true }
            focusOrder.clear()
            return
        }
        val w = windows.find { it.route == route }
        when {
            w == null || w.minimized -> wmOpen(route, areaW, areaH)
            focusedRoute() == route -> w.minimized = true
            else -> wmFocus(route)
        }
    }

    fun wmClose(route: String) {
        windows.removeAll { it.route == route }
        focusOrder.remove(route)
    }

    fun wmToggleMax(w: WinState, areaW: Float, areaH: Float) {
        if (w.maximized) {
            w.prev?.let { p ->
                w.x = p[0]; w.y = p[1]; w.w = p[2]; w.h = p[3]
            }
            w.maximized = false; w.snap = null
        } else {
            w.prev = floatArrayOf(w.x, w.y, w.w, w.h)
            // Source snapRect('max'): area inset by 8.
            w.x = 8f; w.y = 8f; w.w = areaW - 16f; w.h = areaH - 16f
            w.maximized = true; w.snap = "max"
        }
    }

    fun wmSnapTo(w: WinState, zone: String, areaW: Float, areaH: Float) {
        if (zone == "max") { wmToggleMaxForce(w, areaW, areaH); return }
        if (!w.maximized && w.snap == null) w.prev = floatArrayOf(w.x, w.y, w.w, w.h)
        w.maximized = false
        val hw = areaW / 2f; val hh = areaH / 2f
        when (zone) {
            "left" -> { w.x = 0f; w.y = 0f; w.w = hw; w.h = areaH }
            "right" -> { w.x = hw; w.y = 0f; w.w = areaW - hw; w.h = areaH }
            "tl" -> { w.x = 0f; w.y = 0f; w.w = hw; w.h = hh }
            "tr" -> { w.x = hw; w.y = 0f; w.w = areaW - hw; w.h = hh }
            "bl" -> { w.x = 0f; w.y = hh; w.w = hw; w.h = areaH - hh }
            "br" -> { w.x = hw; w.y = hh; w.w = areaW - hw; w.h = areaH - hh }
        }
        w.snap = zone
    }

    fun applySnapLayout(layoutIdx: Int, areaW: Float, areaH: Float) {
        val (_, rects) = SNAP_LAYOUTS[layoutIdx]
        val cands = focusOrder.mapNotNull { r -> windows.find { it.route == r && !it.minimized } }
        if (cands.isEmpty()) { snapOpen = false; return }
        if ((cands.size == 1 && rects.size > 1) || rects.size == 1) {
            val w = cands.first()
            if (!w.maximized) wmToggleMaxForce(w, areaW, areaH)
        } else {
            cands.take(rects.size).forEachIndexed { i, w ->
                val r = rects[i]
                if (!w.maximized && w.snap == null) w.prev = floatArrayOf(w.x, w.y, w.w, w.h)
                w.maximized = false; w.snap = SNAP_LAYOUTS[layoutIdx].first
                w.x = r[0] * areaW; w.y = r[1] * areaH
                w.w = r[2] * areaW; w.h = r[3] * areaH
            }
        }
        snapOpen = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // v1.0.2 (defect C): the whole desktop lives inside the
            // safe-drawing area (background stays edge-to-edge): the
            // taskbar is no longer crowded under the Android navigation
            // bar, desktop icons never start under the landscape camera
            // cutout, and the screen widget clears the status bar /
            // cutout. In fullscreen (immersive) mode the insets collapse
            // to zero and the desktop expands, like the source.
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val areaW = maxWidth.value
            val areaH = maxHeight.value
            lastAreaW = areaW; lastAreaH = areaH
            val tools = sortedToolsForDisplay(pins)

            // ---- Desktop icons layer (blank-space long-press = ctx menu) --
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { desktopCtxOpen = true })
                    }
            ) {
                // v1.0.1 (defect #3): ONE absolute layout — the source's
                // layoutDesktopIcons(): column-major flow, A-Z by name
                // (desktopIconNameOf), rowsPerCol from the SCALED cell
                // height; a saved position overrides the flow spot.
                val flowTools = tools.sortedBy { it.name.lowercase() }
                val rowsPerCol = maxOf(1, (areaH / cellH).toInt())
                flowTools.forEachIndexed { idx, tool ->
                    val saved = iconPos[tool.tabId]
                    val defX = (idx / rowsPerCol) * cellW
                    val defY = (idx % rowsPerCol) * cellH
                    var dragX by remember(tool.tabId) { mutableStateOf(saved?.first ?: defX) }
                    var dragY by remember(tool.tabId) { mutableStateOf(saved?.second ?: defY) }
                    LaunchedEffect(saved) {
                        if (saved != null) { dragX = saved.first; dragY = saved.second }
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = dragX.dp, y = dragY.dp)
                            .width((108f * s).dp)
                            .then(
                                if (optDrag) Modifier.pointerInput(tool.tabId, areaW, areaH) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            var nx = dragX; var ny = dragY
                                            if (optSnap) {
                                                nx = (nx / cellW).roundToInt() * cellW
                                                ny = (ny / cellH).roundToInt() * cellH
                                            }
                                            dragX = nx; dragY = ny
                                            val newMap = iconPos.toMutableMap()
                                            flowTools.forEachIndexed { j, t2 ->
                                                if (newMap[t2.tabId] == null) {
                                                    newMap[t2.tabId] =
                                                        (j / rowsPerCol) * cellW to (j % rowsPerCol) * cellH
                                                }
                                            }
                                            newMap[tool.tabId] = nx to ny
                                            saveIconPos(newMap)
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragX = (dragX + dragAmount.x / density)
                                            .coerceIn(0f, areaW - 108f * s)
                                        dragY = (dragY + dragAmount.y / density)
                                            .coerceIn(0f, areaH - 100f * s)
                                    }
                                } else Modifier
                            )
                    ) {
                        DesktopTile(
                            tool = tool,
                            s = s,
                            onTap = { wmToggle(tool.route, areaW, areaH) },
                            onLongPress = { iconCtxRoute = tool.route }
                        )
                    }
                }
            }

            // ---- Screen widget (top-right) -------------------------------
            Box(modifier = Modifier.align(Alignment.TopEnd).padding((12f * s).dp)) {
                ScreenWidget(s)
            }

            // ---- Windows ---------------------------------------------------
            windows.filter { !it.minimized }
                .sortedBy { it.z }
                .forEach { win ->
                    val tool = toolByRoute(win.route)
                    DesktopWindow(
                        s = s,
                        win = win,
                        title = tool?.name ?: win.route,
                        faIcon = tool?.faIcon ?: "fa-gear",
                        focused = focusedRoute() == win.route,
                        areaW = areaW, areaH = areaH,
                        onFocus = { wmFocus(win.route) },
                        onClose = { wmClose(win.route) },
                        onMinimize = { win.minimized = true },
                        onToggleMax = { wmToggleMax(win, areaW, areaH) },
                        onSnap = { zone -> wmSnapTo(win, zone, areaW, areaH) },
                        content = { toolContent(win.route) { wmClose(win.route) } }
                    )
                }

            // ---- Start menu ------------------------------------------------
            if (startOpen) {
                StartMenu(
                    s = s,
                    pins = pins,
                    onPick = { route ->
                        startOpen = false
                        wmToggle(route, areaW, areaH)
                    },
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = (8f * s).dp, bottom = (8f * s).dp)
                )
            }

            // ---- Snap layouts panel ---------------------------------------
            if (snapOpen) {
                SnapPanel(
                    s = s,
                    areaW = areaW,
                    onPick = { idx -> applySnapLayout(idx, areaW, areaH) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (8f * s).dp)
                )
            }

            // ---- Icon context menu (Pin to taskbar) ------------------------
            iconCtxRoute?.let { route ->
                val tool = toolByRoute(route)
                if (tool != null) {
                    val isPinned = tool.tabId in pins
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    savePins(
                                        if (isPinned) pins - tool.tabId else pins + tool.tabId
                                    )
                                    iconCtxRoute = null
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size((18f * s).dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isPinned) "Unpin from taskbar" else "Pin to taskbar")
                        }
                    }
                }
            }

            // ---- Desktop context menu --------------------------------------
            if (desktopCtxOpen) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.width((250f * s).dp).padding(vertical = (6f * s).dp)) {
                        CtxItem(s, "Arrange icons by name") {
                            // Default flow IS A-Z; arranging clears saved spots.
                            saveIconPos(emptyMap())
                            desktopCtxOpen = false
                            toast(context, "Icons arranged by name.")
                        }
                        CtxItem(s, "Align icons to grid" + if (optSnap) " ✓" else "") {
                            val newSnap = !optSnap
                            saveIconOpts(newSnap, optDrag)
                            if (newSnap && iconPos.isNotEmpty()) {
                                saveIconPos(iconPos.mapValues { (_, v) ->
                                    (v.first / cellW).roundToInt() * cellW to
                                        (v.second / cellH).roundToInt() * cellH
                                })
                            }
                            desktopCtxOpen = false
                            toast(context, "Align icons to grid: ${if (newSnap) "on." else "off."}")
                        }
                        CtxItem(s, "Allow moving icons" + if (optDrag) " ✓" else "") {
                            saveIconOpts(optSnap, !optDrag)
                            desktopCtxOpen = false
                            toast(context, "Moving icons: ${if (!optDrag) "enabled." else "disabled."}")
                        }
                        CtxItem(s, "Cancel") { desktopCtxOpen = false }
                    }
                }
            }
        }

        // ---- Taskbar --------------------------------------------------------
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height((WM_TASKBAR_BASE * s).dp).padding(horizontal = (10f * s).dp)
            ) {
                // Left: UI scale (source taskbar-left, wm-ui-scale-slider).
                Icon(Icons.Filled.Search, contentDescription = "UI scale", modifier = Modifier.size((14f * s).dp))
                Slider(
                    value = settings.uiScale.toFloat(),
                    onValueChange = { v ->
                        val stepped = (v / 5f).roundToInt() * 5
                        scope.launch { store.setSetting("toolbox_ui_scale", stepped.toString()) }
                    },
                    valueRange = 25f..150f,
                    steps = 24,
                    modifier = Modifier.width((76f * s).dp)
                )
                Text("${settings.uiScale}%", fontSize = (11f * s).sp)
                IconButton(onClick = {
                    scope.launch { store.setSetting("toolbox_ui_scale", "100") }
                    toast(context, "UI scale reset to 100%.")
                }, modifier = Modifier.size((42f * s).dp)) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Reset UI scale", modifier = Modifier.size((18f * s).dp))
                }

                // Centre: Start + pins + open windows + snap button.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    TaskbarButton(
                        s = s,
                        faIcon = null,
                        label = "Start",
                        active = startOpen,
                        onClick = { startOpen = !startOpen; snapOpen = false }
                    ) {
                        Icon(
                            Icons.Filled.Construction,
                            contentDescription = "Start",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size((20f * s).dp)
                        )
                    }
                    // Source renderTaskbarPins(): Desktop (house) first...
                    TaskbarButton(s = s, faIcon = "fa-house", label = "Desktop", active = false,
                        onClick = { wmToggle("home", 0f, 0f) }) {}
                    // ...then user pins in pin order...
                    pins.mapNotNull { toolByTabId(it) }.forEach { tool ->
                        TaskbarButton(
                            s = s,
                            faIcon = tool.faIcon, label = tool.name,
                            active = focusedRoute() == tool.route &&
                                windows.any { it.route == tool.route && !it.minimized },
                            open = windows.any { it.route == tool.route },
                            onClick = { wmToggle(tool.route, lastAreaW, lastAreaH) }
                        ) {}
                    }
                    // ...then open windows not pinned (transient).
                    windows.filter { w -> pins.none { toolByTabId(it)?.route == w.route } }
                        .forEach { w ->
                            val tool = toolByRoute(w.route)
                            if (tool != null) {
                                TaskbarButton(
                                    s = s,
                                    faIcon = tool.faIcon, label = tool.name,
                                    active = focusedRoute() == w.route && !w.minimized,
                                    open = true,
                                    onClick = { wmToggle(w.route, lastAreaW, lastAreaH) }
                                ) {}
                            }
                        }
                    IconButton(onClick = { snapOpen = !snapOpen; startOpen = false },
                        modifier = Modifier.size((42f * s).dp)) {
                        Icon(Icons.Filled.GridView, contentDescription = "Snap layouts",
                            modifier = Modifier.size((20f * s).dp))
                    }
                }

                // Right tray: theme segment, clock, sound, fullscreen,
                // settings, show desktop, close all.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { store.setSetting("toolbox_dark", "light") } },
                        modifier = Modifier.size((42f * s).dp)) {
                        Icon(Icons.Filled.WbSunny, contentDescription = "Light theme", modifier = Modifier.size((18f * s).dp))
                    }
                    IconButton(onClick = { scope.launch { store.setSetting("toolbox_dark", "dark") } },
                        modifier = Modifier.size((42f * s).dp)) {
                        Icon(Icons.Filled.DarkMode, contentDescription = "Dark theme", modifier = Modifier.size((18f * s).dp))
                    }
                    IconButton(onClick = { scope.launch { store.setSetting("toolbox_dark", "black") } },
                        modifier = Modifier.size((42f * s).dp)) {
                        Icon(Icons.Filled.Circle, contentDescription = "Black theme", modifier = Modifier.size((18f * s).dp))
                    }
                    Text(
                        clockText,
                        fontSize = (12f * s).sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { wmToggle("time", lastAreaW, lastAreaH) }
                            .padding(horizontal = 4.dp)
                    )
                    IconButton(onClick = { scope.launch { store.setSound(!settings.sound) } },
                        modifier = Modifier.size((42f * s).dp)) {
                        Icon(
                            if (settings.sound) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = "Toggle sound",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { FullscreenState.toggle() }, modifier = Modifier.size((42f * s).dp)) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "Toggle fullscreen", modifier = Modifier.size((18f * s).dp))
                    }
                    IconButton(onClick = { wmToggle("settings", lastAreaW, lastAreaH) }, modifier = Modifier.size((42f * s).dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size((18f * s).dp))
                    }
                    IconButton(onClick = {
                        windows.forEach { if (!it.minimized) it.minimized = true }
                        focusOrder.clear()
                    }, modifier = Modifier.size((42f * s).dp)) {
                        Icon(Icons.Filled.Minimize, contentDescription = "Show desktop", modifier = Modifier.size((18f * s).dp))
                    }
                    IconButton(onClick = {
                        windows.clear(); focusOrder.clear()
                    }, modifier = Modifier.size((42f * s).dp)) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Close all apps", modifier = Modifier.size((18f * s).dp))
                    }
                }
            }
        }
    }
}

private fun wmToggleMaxForce(w: WinState, areaW: Float, areaH: Float) {
    if (!w.maximized) {
        w.prev = floatArrayOf(w.x, w.y, w.w, w.h)
        w.x = 8f; w.y = 8f; w.w = areaW - 16f; w.h = areaH - 16f
        w.maximized = true; w.snap = "max"
    }
}

@Composable
private fun CtxItem(s: Float, label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = (14f * s).sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = (16f * s).dp, vertical = (10f * s).dp)
    )
}

/**
 * Desktop icon tile — v1.0.1 (defect #3): the SOURCE desktop card, not
 * the mobile HomeTile. Source CSS: card calc(108px * s) x calc(100px * s),
 * glyph box 52*s, label 12*s single-line ellipsis.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DesktopTile(tool: ToolEntry, s: Float, onTap: () -> Unit, onLongPress: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width((108f * s).dp)
            .height((100f * s).dp)
            .clip(RoundedCornerShape((10f * s).dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(top = (6f * s).dp)
    ) {
        ToolIconBox(
            tool.faIcon,
            boxDp = (52f * s).dp,
            glyphDp = (26f * s).dp,
            cornerDp = (14f * s).dp
        )
        Text(
            tool.name,
            fontSize = (12f * s).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = (4f * s).dp, start = (2f * s).dp, end = (2f * s).dp)
        )
    }
}

/** Source #screen-widget: physical resolution + refresh rate, top-right. */
@Composable
private fun ScreenWidget(s: Float) {
    val context = LocalContext.current
    var hzText by remember { mutableStateOf("– Hz") }
    var resText by remember { mutableStateOf("– × –") }
    LaunchedEffect(Unit) {
        try {
            val activity = context as? Activity
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity?.windowManager?.defaultDisplay?.getRealMetrics(metrics)
            resText = "${metrics.widthPixels} × ${metrics.heightPixels}"
            // measureRefreshRate(): the source measures rAF over 2s and
            // snaps to [30,60,90,120,144,165,240] within 8. The native
            // Display.getRefreshRate already reports the panel rate; snap
            // to the same common list for identical presentation.
            @Suppress("DEPRECATION")
            val raw = activity?.windowManager?.defaultDisplay?.refreshRate ?: 60f
            val common = listOf(30, 60, 90, 120, 144, 165, 240)
            val best = common.minByOrNull { kotlin.math.abs(it - raw) } ?: 60
            hzText = if (kotlin.math.abs(best - raw) <= 8f) "$best Hz"
            else "${raw.roundToInt()} Hz"
        } catch (e: Exception) {
            // Widget is informational only.
        }
    }
    Surface(
        shape = RoundedCornerShape((12f * s).dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(horizontal = (12f * s).dp, vertical = (8f * s).dp)
        ) {
            Text(resText, fontSize = (14f * s).sp, fontWeight = FontWeight.ExtraBold)
            Text(hzText, fontSize = (11f * s).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskbarButton(
    s: Float,
    faIcon: String?,
    label: String,
    active: Boolean,
    open: Boolean = false,
    onClick: () -> Unit,
    custom: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape((8f * s).dp))
            .clickable { onClick() }
            .padding(horizontal = (6f * s).dp, vertical = (3f * s).dp)
    ) {
        if (faIcon != null) {
            ToolGlyph(faIcon, modifier = Modifier.size((20f * s).dp), tint = MaterialTheme.colorScheme.onSurface)
        } else {
            custom()
        }
        // Active/open indicator (source refreshTaskbarState underline).
        Box(
            modifier = Modifier
                .padding(top = (2f * s).dp)
                .size(width = (16f * s).dp, height = (3f * s).dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    when {
                        active -> MaterialTheme.colorScheme.primary
                        open -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> Color.Transparent
                    }
                )
        )
    }
}

@Composable
private fun StartMenu(
    s: Float,
    pins: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf("") }
    val items = listOf(
        ToolEntry("home", "Desktop", "Return to the desktop", "tab-home", "fa-house")
    ) + sortedToolsForDisplay(pins)
    val shown = filterTools(items, filter)
    Surface(
        shape = RoundedCornerShape((14f * s).dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.width((350f * s).dp).shadow(16.dp, RoundedCornerShape((14f * s).dp))
    ) {
        Column(modifier = Modifier.padding((12f * s).dp)) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Search tools...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) {
                Text(
                    "No tools match your search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().height((340f * s).dp)) {
                items(shown) { tool ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(tool.route) }
                            .padding(vertical = 8.dp)
                    ) {
                        ToolIconBox(tool.faIcon, boxDp = (38f * s).dp, glyphDp = (19f * s).dp, cornerDp = (10f * s).dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(tool.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapPanel(s: Float, areaW: Float, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    // v1.0.2 (defect D): width comes from snapPanelWidthDp (content-sized,
    // clamped to the desktop area), and tiles wrap via snapTilesPerRow —
    // all six layouts are always fully visible inside the panel.
    val panelW = snapPanelWidthDp(s, SNAP_LAYOUTS.size, areaW)
    val perRow = snapTilesPerRow(s, SNAP_LAYOUTS.size, panelW)
    Surface(
        shape = RoundedCornerShape((14f * s).dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.width(panelW.dp).shadow(16.dp, RoundedCornerShape((14f * s).dp))
    ) {
        Column(modifier = Modifier.padding((SNAP_PANEL_PAD * s).dp)) {
            Text("Snap open windows", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy((SNAP_TILE_GAP * s).dp)) {
                SNAP_LAYOUTS.withIndex().chunked(perRow).forEach { rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy((SNAP_TILE_GAP * s).dp)) {
                        rowTiles.forEach { (idx, layoutDef) ->
                            val (name, rects) = layoutDef
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width((SNAP_TILE_W * s).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPick(idx) }
                                    .padding(horizontal = (6f * s).dp, vertical = (6f * s).dp)
                            ) {
                                // Mini layout diagram (source buildSnapPanel cells).
                                Box(modifier = Modifier.size((44f * s).dp, (30f * s).dp)) {
                                    rects.forEach { r ->
                                        Box(
                                            modifier = Modifier
                                                .offset(x = (r[0] * 44f * s).dp, y = (r[1] * 30f * s).dp)
                                                .size((r[2] * 44f * s).dp, (r[3] * 30f * s).dp)
                                                .padding(1.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                                    RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                }
                                Text(
                                    name,
                                    fontSize = (9f * s).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopWindow(
    s: Float,
    win: WinState,
    title: String,
    faIcon: String,
    focused: Boolean,
    areaW: Float,
    areaH: Float,
    onFocus: () -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMax: () -> Unit,
    onSnap: (String) -> Unit,
    content: @Composable () -> Unit
) {
    var dragX by remember(win.route) { mutableStateOf(win.x) }
    var dragY by remember(win.route) { mutableStateOf(win.y) }
    var dragW by remember(win.route) { mutableStateOf(win.w) }
    var dragH by remember(win.route) { mutableStateOf(win.h) }
    LaunchedEffect(win.x, win.y, win.w, win.h) {
        dragX = win.x; dragY = win.y; dragW = win.w; dragH = win.h
    }
    val x = if (win.maximized) 8f else dragX
    val y = if (win.maximized) 8f else dragY
    val w = if (win.maximized) areaW - 16f else dragW
    val h = if (win.maximized) areaH - 16f else dragH

    Column(
        modifier = Modifier
            .offset(x = x.dp, y = y.dp)
            .size(w.dp, h.dp)
            .shadow(if (focused) 18.dp else 8.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)
            )
    ) {
        // Titlebar (source wm-titlebar): icon, name, min/max/close.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height((40f * s).dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(win.route, win.maximized) {
                    detectTapGestures(onDoubleTap = { onToggleMax() })
                }
                .pointerInput(win.route + "-drag", win.maximized) {
                    if (!win.maximized) {
                        detectDragGestures(
                            onDragEnd = {
                                // wmSnap on drag end: edges -> halves/max,
                                // corners -> quarters (snapRect zones).
                                val zone = when {
                                    dragY <= 2f -> "max"
                                    dragX <= 2f && dragY < areaH / 3f -> "tl"
                                    dragX <= 2f && dragY > areaH * 2 / 3f -> "bl"
                                    dragX <= 2f -> "left"
                                    dragX + dragW >= areaW - 2f && dragY < areaH / 3f -> "tr"
                                    dragX + dragW >= areaW - 2f && dragY > areaH * 2 / 3f -> "br"
                                    dragX + dragW >= areaW - 2f -> "right"
                                    else -> null
                                }
                                if (zone != null) {
                                    onSnap(zone)
                                } else {
                                    win.x = dragX; win.y = dragY
                                    win.snap = null
                                }
                            }
                        ) { change, amount ->
                            change.consume()
                            onFocus()
                            dragX = (dragX + amount.x / density).coerceIn(-dragW + 90f, areaW - 60f)
                            dragY = (dragY + amount.y / density).coerceIn(0f, areaH - 40f * s)
                        }
                    }
                }
                .padding(start = (14f * s).dp)
        ) {
            ToolGlyph(faIcon, modifier = Modifier.size((16f * s).dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width((8f * s).dp))
            Text(
                title,
                fontSize = (13f * s).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onMinimize, modifier = Modifier.size(width = (46f * s).dp, height = (40f * s).dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Minimize", modifier = Modifier.size((13f * s).dp))
            }
            IconButton(onClick = onToggleMax, modifier = Modifier.size(width = (46f * s).dp, height = (40f * s).dp)) {
                Icon(Icons.Filled.CropSquare, contentDescription = "Maximize", modifier = Modifier.size((12f * s).dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(width = (46f * s).dp, height = (40f * s).dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size((13f * s).dp))
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
            if (!win.maximized) {
                // Resize: SE corner handle (source has 8 handles; corner +
                // right/bottom edges implemented — named simplification).
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .pointerInput(win.route + "-resize") {
                            detectDragGestures { change, amount ->
                                change.consume()
                                dragW = (dragW + amount.x / density).coerceIn(WM_MIN_W, areaW)
                                dragH = (dragH + amount.y / density).coerceIn(WM_MIN_H, areaH)
                                win.w = dragW; win.h = dragH
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(8.dp)
                        .fillMaxHeight()
                        .pointerInput(win.route + "-resize-e") {
                            detectDragGestures { change, amount ->
                                change.consume()
                                dragW = (dragW + amount.x / density).coerceIn(WM_MIN_W, areaW)
                                win.w = dragW
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(8.dp)
                        .fillMaxWidth()
                        .pointerInput(win.route + "-resize-s") {
                            detectDragGestures { change, amount ->
                                change.consume()
                                dragH = (dragH + amount.y / density).coerceIn(WM_MIN_H, areaH)
                                win.h = dragH
                            }
                        }
                )
            }
        }
    }
}
