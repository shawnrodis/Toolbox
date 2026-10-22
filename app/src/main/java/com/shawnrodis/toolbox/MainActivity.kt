package com.shawnrodis.toolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shawnrodis.toolbox.data.AppSettings
import com.shawnrodis.toolbox.data.AppStore
import com.shawnrodis.toolbox.data.accentColor
import com.shawnrodis.toolbox.data.SyncManager
import com.shawnrodis.toolbox.data.UpdateManager
import com.shawnrodis.toolbox.tools.CurrencyScreen
import com.shawnrodis.toolbox.tools.FlightTrackerContent
import com.shawnrodis.toolbox.tools.FlightTrackerScreen
import com.shawnrodis.toolbox.tools.NotesScreen
import com.shawnrodis.toolbox.tools.PdfToolsContent
import com.shawnrodis.toolbox.tools.PdfToolsScreen
import com.shawnrodis.toolbox.tools.PtoScreen
import com.shawnrodis.toolbox.tools.ShoppingScreen
import com.shawnrodis.toolbox.tools.SpongeScreen
import com.shawnrodis.toolbox.tools.SquarePicContent
import com.shawnrodis.toolbox.tools.SquarePicScreen
import com.shawnrodis.toolbox.tools.SplitScreen
import com.shawnrodis.toolbox.tools.TallyScreen
import com.shawnrodis.toolbox.tools.TimeScreen
import com.shawnrodis.toolbox.tools.TipScreen
import com.shawnrodis.toolbox.tools.UnitConverterScreen
import com.shawnrodis.toolbox.ui.DesktopShell
import com.shawnrodis.toolbox.ui.GlobalHeader
import com.shawnrodis.toolbox.ui.parsePins
import com.shawnrodis.toolbox.ui.toolByRoute
import com.shawnrodis.toolbox.ui.toast
import com.shawnrodis.toolbox.ui.ToolboxHaptics
import com.shawnrodis.toolbox.ui.toolboxBeep
import kotlinx.coroutines.launch
import com.shawnrodis.toolbox.ui.FullscreenState
import com.shawnrodis.toolbox.ui.HomeScreen
import com.shawnrodis.toolbox.ui.InAppToastHost
import com.shawnrodis.toolbox.ui.LocalInDesktopWindow
import com.shawnrodis.toolbox.ui.QuickSwitcherSheet
import com.shawnrodis.toolbox.ui.SettingsScreen
import com.shawnrodis.toolbox.ui.theme.ToolboxNativeTheme

/**
 * True-native entry point. No WebView anywhere in this project:
 * every screen is a Jetpack Compose composable.
 *
 * Phase 1: Home + Settings + Currency, Time, Shopping, Unit Converter, PTO,
 * Split, Tip, Tally, Notes, SpongeBob — all persisted via DataStore
 * (the native replacement for the web app's localStorage).
 * Phase 2: Flight Tracker (SkyTrack, Aviationstack, user-entered key only).
 * Phase 3: PDF Tools, Square Pic, Sync, exports.
 * Parity pass (1.0.0): home view styles (List / iPhone / Android), Desktop
 * Mode shell (window manager + taskbar + Start menu), UI scale applied as a
 * real density factor (source updateUIScale root font-size), auto-desktop
 * on landscape for phones/small tablets (autoDesktopOrientation),
 * fullscreen -> immersive mode, in-app toast overlay, bundled Inter/Fredoka.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val store = remember { AppStore(applicationContext) }
            // Phase 3: start the sync engine (dirty hook, schedule, startup
            // tick) — the native syncStartup()/syncApplySchedule() at boot.
            val sync = remember { SyncManager.get(applicationContext, store) }
            LaunchedEffect(Unit) { sync.start() }
            // v1.2.0: update checker — one throttled (24 h) silent
            // check per launch window against the GitHub Releases API.
            val updates = remember { UpdateManager.get(applicationContext, store) }
            LaunchedEffect(Unit) { updates.autoCheck() }
            val settings by store.settings.collectAsState(initial = AppSettings())
            // v1.1.0: haptics hook — init the vibrator once and keep the
            // central beep-hook gate current with the Settings toggle.
            LaunchedEffect(Unit) { ToolboxHaptics.init(applicationContext) }
            LaunchedEffect(settings.haptics) { ToolboxHaptics.enabled = settings.haptics }

            // Fullscreen (source toggleFullscreen): runtime immersive mode.
            val fullscreen = FullscreenState.active.value
            LaunchedEffect(fullscreen) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars())
                else controller.show(WindowInsetsCompat.Type.systemBars())
            }

            ToolboxNativeTheme(
                themeMode = settings.themeMode,
                accent = accentColor(settings)
            ) {
                // UI Size & Scale (source updateUIScale): the web version
                // scales the root font-size; the native equivalent scales
                // the whole density (dp + sp) app-wide.
                val baseDensity = LocalDensity.current
                val scaledDensity = remember(baseDensity, settings.uiScale) {
                    val f = settings.uiScale / 100f
                    Density(baseDensity.density * f, baseDensity.fontScale * f)
                }
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ToolboxApp(store, settings)
                            // Source #toast overlay (top-centre pill).
                            InAppToastHost()
                        }
                    }
                }
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val CURRENCY = "currency"
    const val TIME = "time"
    const val SHOPPING = "shopping"
    const val UNITCONV = "unitconv"
    const val PTO = "pto"
    const val SPLIT = "split"
    const val TIP = "tip"
    const val TALLY = "tally"
    const val NOTES = "notes"
    const val MEMEGEN = "memegen"
    const val SKYTRACK = "skytrack"
    const val PDFTOOLS = "pdftools"
    const val SQUAREPIC = "squarepic"
    const val SETTINGS = "settings"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ToolboxApp(store: AppStore, settings: AppSettings) {
    // Source autoDesktopOrientation(): on phones/small tablets (min screen
    // dimension < 820 in the source's screen px check; here smallest width
    // < 820dp), starting in landscape or rotating INTO landscape auto-
    // enables Desktop Mode; rotating back to portrait turns an AUTO
    // desktop off again. A manual Settings toggle (toolbox_desktop) always
    // wins and is never overridden.
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val eligible = configuration.smallestScreenWidthDp < 820
    var lastLandscape by remember { mutableStateOf<Boolean?>(null) }
    var autoDesktop by remember { mutableStateOf(false) }
    LaunchedEffect(isLandscape) {
        if (!eligible) return@LaunchedEffect
        val prev = lastLandscape
        when {
            prev == null -> if (isLandscape) autoDesktop = true
            prev != isLandscape && isLandscape -> autoDesktop = true
            prev != isLandscape && !isLandscape -> if (autoDesktop) autoDesktop = false
        }
        lastLandscape = isLandscape
    }
    // v1.0.1: manual-wins latch for the header/Settings desktop toggle.
    // Turning desktop OFF by hand while auto-desktop (landscape) is active
    // must not be undone by the auto logic; the latch clears on rotation
    // or when desktop is manually turned back on (the source's
    // toggleDesktopMode manual setting wins the same way).
    var suppressAuto by remember { mutableStateOf(false) }
    LaunchedEffect(isLandscape) { suppressAuto = false }
    LaunchedEffect(settings.desktopMode) { if (settings.desktopMode) suppressAuto = false }
    val desktopActive = settings.desktopMode || (autoDesktop && !suppressAuto)

    if (desktopActive) {
        DesktopShell(
            store = store,
            settings = settings,
            // v1.0.5: the DesktopShell tray Exit button (source
            // #taskbar-exit-btn) raises the same manual-off latch the
            // header toggle sets, so leaving via the tray also wins over
            // landscape auto-desktop until rotation / manual re-enable.
            onExitDesktop = { suppressAuto = true },
            toolContent = { route, onCloseWindow ->
                ToolWindowContent(store, route, onCloseWindow)
            }
        )
        return
    }

    val navController = rememberNavController()
    val backEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route
    var quickSwitcherOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pinsJson by store.stringState("toolbox_taskbar_pins", "[]").collectAsState(initial = "[]")
    // v1.0.1 (defect #2 amendment): the global header title is DYNAMIC —
    // switchTab() sets it per tab: home -> "Toolbox" / "Shawn Rodis";
    // any other tab (Settings included) -> registry name / "Toolbox Utility".
    val onHome = currentRoute == null || currentRoute == Routes.HOME
    val headerTitle = if (onHome) "Toolbox" else toolByRoute(currentRoute!!)?.name ?: "Toolbox"
    val headerSubtitle = if (onHome) "Shawn Rodis" else "Toolbox Utility"

    Column(
        modifier = Modifier
            .fillMaxSize()
            // v1.0.2 (defect C): edge-to-edge is on, so the shell must
            // consume the safe-drawing insets itself — status bar,
            // display cutout (punch-hole: top in portrait, mid-left edge
            // in landscape) and navigation bar. The global header no
            // longer sits under the status bar/cutout and content never
            // runs under the nav bar. With the app's fullscreen
            // (immersive) mode ON the system bars are hidden, the
            // reported insets collapse to zero and the shell expands to
            // the full screen, exactly like the source.
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        GlobalHeader(
            title = headerTitle,
            subtitle = headerSubtitle,
            fullscreen = FullscreenState.active.value,
            soundOn = settings.sound,
            onBrandClick = { navController.popBackStack(Routes.HOME, inclusive = false) },
            onToggleFullscreen = { FullscreenState.toggle() },
            onToggleDesktop = {
                // Source toggleDesktopMode(): persisted toolbox_desktop;
                // the manual choice wins over autoDesktopOrientation.
                if (desktopActive) {
                    if (settings.desktopMode) {
                        scope.launch { store.setSetting("toolbox_desktop", "false") }
                    }
                    suppressAuto = true
                    toast(context, "Desktop layout disabled.")
                } else {
                    suppressAuto = false
                    scope.launch { store.setSetting("toolbox_desktop", "true") }
                    toast(context, "Desktop layout enabled.")
                }
            },
            onOpenSettings = {
                if (currentRoute != Routes.SETTINGS) navController.navigate(Routes.SETTINGS)
            },
            onToggleSound = {
                // Source toggleToolboxSound(): persist, toast, and beep
                // 880Hz/90ms when turning sounds ON.
                val next = !settings.sound
                scope.launch { store.setSound(next) }
                if (next) toolboxBeep(true, 880, 90)
                toast(context, if (next) "Sounds on." else "Sounds off.")
            }
        )
        Scaffold(
        modifier = Modifier.weight(1f),
        // v1.0.2 (defect C): the outer Column already applied the
        // safe-drawing insets; Scaffold's default content insets
        // (system bars) would pad the NavHost a second time.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            // Source #home-fab: floating Home button shown while inside a
            // tool — tap goes home, long-press opens the quick switcher.
            if (currentRoute != null && currentRoute != Routes.HOME) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .combinedClickable(
                            onClick = {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            },
                            onLongClick = { quickSwitcherOpen = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Home,
                        contentDescription = "Home — hold for quick switcher",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(pad)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    store = store,
                    homeView = settings.homeView,
                    onOpen = { route -> if (route.isNotEmpty()) navController.navigate(route) }
                )
            }
            composable(Routes.CURRENCY) { CurrencyScreen(store) { navController.popBackStack() } }
            composable(Routes.TIME) { TimeScreen(store) { navController.popBackStack() } }
            composable(Routes.SHOPPING) { ShoppingScreen(store) { navController.popBackStack() } }
            composable(Routes.UNITCONV) { UnitConverterScreen(store) { navController.popBackStack() } }
            composable(Routes.PTO) { PtoScreen(store) { navController.popBackStack() } }
            composable(Routes.SPLIT) { SplitScreen(store) { navController.popBackStack() } }
            composable(Routes.TIP) { TipScreen(store) { navController.popBackStack() } }
            composable(Routes.TALLY) { TallyScreen(store) { navController.popBackStack() } }
            composable(Routes.NOTES) { NotesScreen(store) { navController.popBackStack() } }
            composable(Routes.MEMEGEN) { SpongeScreen(store) { navController.popBackStack() } }
            composable(Routes.SKYTRACK) { FlightTrackerScreen(store) { navController.popBackStack() } }
            composable(Routes.PDFTOOLS) { PdfToolsScreen(store) { navController.popBackStack() } }
            composable(Routes.SQUAREPIC) { SquarePicScreen(store) { navController.popBackStack() } }
            composable(Routes.SETTINGS) { SettingsScreen(store) { navController.popBackStack() } }
        }
        }
    }
    if (quickSwitcherOpen) {
        QuickSwitcherSheet(
            pins = parsePins(pinsJson),
            currentRoute = currentRoute,
            onOpen = { route ->
                quickSwitcherOpen = false
                if (route != currentRoute) navController.navigate(route)
            },
            onDismiss = { quickSwitcherOpen = false }
        )
    }
}

/**
 * Hosts a tool inside a Desktop Mode window. Tools with a Content variant
 * (Flight Tracker, PDF Tools, Square Pic) use it directly; the rest render
 * through their full-screen wrapper with LocalInDesktopWindow set, which
 * drops the Scaffold/TopAppBar in favour of the window titlebar (the source
 * hides its app header in desktop mode too).
 */
@Composable
fun ToolWindowContent(store: AppStore, route: String, onCloseWindow: () -> Unit) {
    CompositionLocalProvider(LocalInDesktopWindow provides true) {
        when (route) {
            Routes.SKYTRACK -> FlightTrackerContent(store, Modifier.fillMaxSize())
            Routes.PDFTOOLS -> PdfToolsContent(store, Modifier.fillMaxSize())
            Routes.SQUAREPIC -> SquarePicContent(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            )
            Routes.CURRENCY -> CurrencyScreen(store, onCloseWindow)
            Routes.TIME -> TimeScreen(store, onCloseWindow)
            Routes.SHOPPING -> ShoppingScreen(store, onCloseWindow)
            Routes.UNITCONV -> UnitConverterScreen(store, onCloseWindow)
            Routes.PTO -> PtoScreen(store, onCloseWindow)
            Routes.SPLIT -> SplitScreen(store, onCloseWindow)
            Routes.TIP -> TipScreen(store, onCloseWindow)
            Routes.TALLY -> TallyScreen(store, onCloseWindow)
            Routes.NOTES -> NotesScreen(store, onCloseWindow)
            Routes.MEMEGEN -> SpongeScreen(store, onCloseWindow)
            Routes.SETTINGS -> SettingsScreen(store, onCloseWindow)
            else -> Box(Modifier.fillMaxSize())
        }
    }
}
