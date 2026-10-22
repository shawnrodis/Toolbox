package com.shawnrodis.toolbox.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.data.UPDATE_DISMISSED_KEY
import com.shawnrodis.toolbox.data.UpdateManager
import com.shawnrodis.toolbox.data.updateBannerVisible
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Parity pass — the home screen is now the source's homescreen grid, with
 * all three of its renderings (Settings "Home Screen Style",
 * toolbox_home_view):
 *  - "list"    -> renderHomescreenGrid's list branch: white/black cards
 *                 (rounded-2xl, border, p-4) with an accent-icon-box, the
 *                 tool name and its TOOL_DESCRIPTIONS text. Desktop Mode
 *                 forces this rendering for its icons too.
 *  - "iphone"  -> renderIphoneHome(): pages of 16 tiles (4x4) in a
 *                 swipeable pager with page dots.
 *  - "android" -> renderAndroidHome(): one 4-column tile drawer.
 * Tiles are __homeTileHTML(): 56dp rounded-[18px] accent-icon-box, label
 * underneath (10sp, max 2 lines), and a pin badge when pinned.
 * Display order everywhere is sortedToolsForDisplay(): pinned tools first
 * (in pin order), then alphabetical by name. filterHomescreenTools()
 * matches name OR description. Long-press on a tile/card toggles the pin
 * (source togglePin on mobile), which also feeds the Desktop taskbar pins
 * (same toolbox_taskbar_pins key).
 */

data class ToolEntry(
    val route: String,
    val name: String,
    val subtitle: String,
    val tabId: String,
    val faIcon: String,
    val comingPhase: String? = null
) {
    val description: String get() = subtitle
}

/** Source defaultTools() registry (id, name, FA icon) + TOOL_DESCRIPTIONS. */
val ALL_TOOLS: List<ToolEntry> = listOf(
    ToolEntry("currency", "Currency Converter", "Convert any world currency with live & offline rates.", "tab-curr", "fa-globe"),
    ToolEntry("time", "Time Converter", "Convert 12/24-hour time across world time zones.", "tab-time", "fa-clock"),
    ToolEntry("shopping", "Shopping Assistant", "Compare unit costs across mismatched product options.", "tab-unit", "fa-cart-shopping"),
    ToolEntry("unitconv", "Unit Converter", "Convert weight, temperature, length, volume & area units.", "tab-unitconv", "fa-ruler-combined"),
    ToolEntry("pto", "PTO Tracker", "Track paid leave balances, carryover, & logs.", "tab-pto", "fa-calendar-check"),
    ToolEntry("split", "Split Gas & Parking", "Share trip gas and parking costs evenly.", "tab-split", "fa-gas-pump"),
    ToolEntry("tip", "Tip Calculator", "Calculate tips and split restaurant bills fast.", "tab-tip", "fa-receipt"),
    ToolEntry("tally", "Tally Tool", "Keep tally counts, manage lists, and export PDF.", "tab-tally", "fa-list-ol"),
    ToolEntry("skytrack", "Flight Tracker", "Track live flights with Aviationstack telemetry.", "tab-skytrack", "fa-plane-departure"),
    ToolEntry("memegen", "SpongeBob Meme Generator", "Convert text to aLtErNaTiNg SpOnGeBoB mEmE case.", "tab-memegen", "sponge"),
    ToolEntry("notes", "Notes", "Bullet-point notes, newest modified first.", "tab-notes", "fa-note-sticky"),
    ToolEntry("pdftools", "PDF Tools", "Merge, split, rotate, convert and OCR PDF documents.", "tab-pdftools", "fa-file-pdf"),
    ToolEntry("squarepic", "Square Pic", "Square any photo with blur, color, or gradient fill. Lossless PNG export.", "tab-squarepic", "fa-images"),
    ToolEntry("settings", "Settings", "Customize theme, UI scale, and desktop layout.", "tab-settings", "fa-gear")
)

fun toolByRoute(route: String): ToolEntry? = ALL_TOOLS.find { it.route == route }
fun toolByTabId(tabId: String): ToolEntry? = ALL_TOOLS.find { it.tabId == tabId }

/** Source getPinnedTabs(): toolbox_taskbar_pins is a JSON array of tab ids. */
fun parsePins(json: String): List<String> = try {
    val a = JSONArray(json)
    List(a.length()) { a.optString(it) }.filter { it.isNotEmpty() }
} catch (e: Exception) {
    emptyList()
}

fun pinsToJson(pins: List<String>): String {
    val a = JSONArray()
    pins.forEach { a.put(it) }
    return a.toString()
}

/**
 * Source sortedToolsForDisplay(): THE display list for every surface that
 * shows tools — the home grid in all views (renderHomescreenGrid /
 * renderIphoneHome / renderAndroidHome), the Quick Switcher
 * (openQuickSwitcher), the Start menu (renderStartMenu) and the desktop
 * icons. The source filters the registry with
 * `t.enabled && t.id !== 'tab-home' && t.id !== 'tab-settings'`:
 * **Settings is not an app** — it has no tile anywhere; it is reached only
 * via the global header gear and the Desktop taskbar's dedicated gear
 * button. Ordering: pinned first (in pin order), then A–Z by name.
 * (v1.0.1 fix: the native port previously included the Settings entry in
 * this list, so Settings leaked onto every display surface.)
 */
fun sortedToolsForDisplay(pins: List<String>): List<ToolEntry> {
    val visible = ALL_TOOLS.filter {
        it.tabId != "tab-settings" && it.tabId != "tab-home" &&
            it.route != "settings" && it.route != "home"
    }
    val byTab = visible.associateBy { it.tabId }
    val pinned = pins.mapNotNull { byTab[it] }
    val rest = visible.filter { it.tabId !in pins }.sortedBy { it.name.lowercase() }
    return pinned + rest
}

/** Alias for [sortedToolsForDisplay] — the one shared display list. */
fun displayTools(pins: List<String>): List<ToolEntry> = sortedToolsForDisplay(pins)

/** Source filterHomescreenTools(): match name or description. */
fun filterTools(tools: List<ToolEntry>, term: String): List<ToolEntry> {
    val q = term.trim().lowercase()
    if (q.isEmpty()) return tools
    return tools.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
}

@Composable
fun HomeScreen(store: AppStore, homeView: String, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pinsJson by store.stringState("toolbox_taskbar_pins", "[]").collectAsState(initial = "[]")
    val pins = parsePins(pinsJson)
    var query by remember { mutableStateOf("") }

    val shown = filterTools(sortedToolsForDisplay(pins), query)

    fun togglePin(tool: ToolEntry) {
        val newPins = if (tool.tabId in pins) pins - tool.tabId else pins + tool.tabId
        scope.launch { store.setStringState("toolbox_taskbar_pins", pinsToJson(newPins)) }
        toast(context, if (tool.tabId in pins) "${tool.name} unpinned." else "${tool.name} pinned.")
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Toolbox", style = MaterialTheme.typography.headlineMedium)
        // v1.2.0: calm update notice — shown only when a newer release
        // exists and this version hasn't been dismissed (per-version
        // dismissal in toolbox_update_dismissed). Details live in
        // Settings → App updates.
        val updates = remember { UpdateManager.get(context.applicationContext, store) }
        val updateState by updates.state.collectAsState()
        val dismissed by store.stringState(UPDATE_DISMISSED_KEY, "").collectAsState(initial = "")
        val currentVersion = remember { updates.currentVersion() }
        val latestVersion = updateState.latestVersion
        if (updateBannerVisible(currentVersion, latestVersion, dismissed.ifEmpty { null })) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.SystemUpdateAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Toolbox v$latestVersion is available",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onOpen("settings") }) { Text("Details") }
                    IconButton(
                        onClick = {
                            scope.launch {
                                store.setStringStateRaw(UPDATE_DISMISSED_KEY, latestVersion!!)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss update notice",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search tools...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )
        if (shown.isEmpty()) {
            Text(
                "No tools found matching \"$query\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            )
            return@Column
        }
        when (homeView) {
            "iphone" -> IphoneHome(shown, pins, onOpen = onOpen, onLongPress = { togglePin(it) })
            "android" -> AndroidHome(shown, pins, onOpen = onOpen, onLongPress = { togglePin(it) })
            else -> ListHome(shown, pins, onOpen = onOpen, onLongPress = { togglePin(it) })
        }
    }
}

/** Source list branch of renderHomescreenGrid(). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListHome(
    tools: List<ToolEntry>,
    pins: List<String>,
    onOpen: (String) -> Unit,
    onLongPress: (ToolEntry) -> Unit
) {
    // v1.0.2 (defect D): bottom content padding so the last card can
    // scroll fully clear of the floating Home button zone.
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE_DP.dp)
    ) {
        items(tools) { tool ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpen(tool.route) },
                        onLongClick = {
                            // v1.2.1: long-press haptic (pin toggle).
                            ToolboxHaptics.longPressTick()
                            onLongPress(tool)
                        }
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    ToolIconBox(tool.faIcon, boxDp = 48.dp, glyphDp = 22.dp, cornerDp = 16.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tool.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (tool.tabId in pins) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Source __homeTileHTML(): one icon tile, shared by iPhone/Android views. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTile(
    tool: ToolEntry,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // v1.2.1: long-press haptic (pin toggle; the Quick
                    // Switcher passes a no-op action and just ticks).
                    ToolboxHaptics.longPressTick()
                    onLongClick()
                }
            )
            .padding(8.dp)
    ) {
        Box {
            ToolIconBox(tool.faIcon, boxDp = 56.dp, glyphDp = 26.dp, cornerDp = 18.dp)
            if (pinned) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tool.name,
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Source renderIphoneHome(): pages of 16 (4 cols) + page dots. */
@Composable
private fun IphoneHome(
    tools: List<ToolEntry>,
    pins: List<String>,
    onOpen: (String) -> Unit,
    onLongPress: (ToolEntry) -> Unit
) {
    val pages = tools.chunked(16)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    Column {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) {
                items(pages[page]) { tool ->
                    HomeTile(
                        tool = tool,
                        pinned = tool.tabId in pins,
                        onClick = { onOpen(tool.route) },
                        onLongClick = { onLongPress(tool) }
                    )
                }
            }
        }
        if (pages.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                pages.indices.forEach { i ->
                    val active = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .size(width = if (active) 20.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Source renderAndroidHome(): grid-cols-4 tile drawer. */
@Composable
private fun AndroidHome(
    tools: List<ToolEntry>,
    pins: List<String>,
    onOpen: (String) -> Unit,
    onLongPress: (ToolEntry) -> Unit
) {
    // v1.0.2 (defect D): bottom content padding, same clearance as List.
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE_DP.dp)
    ) {
        items(tools) { tool ->
            HomeTile(
                tool = tool,
                pinned = tool.tabId in pins,
                onClick = { onOpen(tool.route) },
                onLongClick = { onLongPress(tool) }
            )
        }
    }
}

/**
 * Source openQuickSwitcher(): bottom sheet listing the DISPLAY tools
 * (sortedToolsForDisplay — so no Settings tile, exactly like the source)
 * except the active one, in a 4-column tile grid ("Quick Switch" title +
 * close button). Opened by long-pressing the floating Home button
 * (home-fab) inside a tool.
 */
@Composable
fun QuickSwitcherSheet(
    currentRoute: String?,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    pins: List<String> = emptyList()
) {
    val tools = displayTools(pins).filter { it.route != currentRoute }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        "Quick Switch",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "✕",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onDismiss() }.padding(6.dp)
                    )
                }
                if (tools.isEmpty()) {
                    Text(
                        "No other tools available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                        items(tools) { tool ->
                            HomeTile(
                                tool = tool,
                                pinned = false,
                                onClick = { onOpen(tool.route) },
                                onLongClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
