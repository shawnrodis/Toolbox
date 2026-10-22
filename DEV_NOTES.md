# Toolbox Native — True-Native Android Rebuild

A true-native Android rebuild of Shawn's "Toolbox (Shawn Rodis)" HTML app.

- **Package (placeholder, can change):** `com.shawnrodis.toolbox`
- **Stack:** Kotlin + Jetpack Compose + Material 3 — **no WebView anywhere**.
  Every screen is a native composable; there is no HTML/CSS/JS in this project.
- **Status (2026-10-22):** **v1.2.1** — versionCode 16, versionName `1.2.1`.
  Fix/polish build (GitHub editions only):
  - **Long-press haptics (both editions):** the v1.1.0 haptics
    (device-local `toolbox_haptics`, default ON) previously fired
    only from the beep hook. Now every long press answers back
    (Shawn: "it should occur when anything is long pressed").
    Android: `ToolboxHaptics.longPressTick()` (same toggle gate as
    `tick()`; the platform vibrate call sits behind an internal
    `performer` seam for tests) fires before the action at EVERY
    long-press trigger — home tiles in all three home styles (pin
    toggle, via the List card + the shared HomeTile), desktop icons
    (context menu), the desktop blank-space menu, and the Home FAB
    (quick switcher). No new long-press behaviours were invented;
    the tools have none. Web: ONE global pointer detector ("Toolbox
    Long-Press Haptics" block) — 550 ms hold (the file's own
    long-press threshold), 12 px move slop, primary button only —
    firing one guarded `navigator.vibrate(10)` gated by
    `toolboxHapticsOn()`; `toolboxHaptic()` now stamps
    `window.__tbLastVibrateAt` so a long-press tick within 150 ms
    of a beep buzz is dropped instead of double-buzzing.
  - **"Shawn Rodis" moved to the Settings footer (both editions):**
    the home header no longer shows the name (web: switchTab hides
    `#header-subtitle-text` on `tab-home`; Android: home passes an
    empty subtitle and GlobalHeader renders no line). Tool views
    keep "Toolbox Utility". The Settings footers now read
    "Toolbox · Web Edition · Version 1.2.1 · by Shawn Rodis" /
    "Toolbox · Android Edition · Version 1.2.1 · by Shawn Rodis".
    Other occurrences (web <title>, the Settings-page brand header
    in both editions) deliberately unchanged.
  - **Web footer relocation (done by the preceding build, verified
    here):** the web version footer now sits as the last element
    inside `#tab-settings` instead of leaking under every tab —
    exactly one occurrence, Settings only.
  - **Desktop taskbar UI-scale slider touch-up (Android):** the
    stock Material slider (fat pill, oversized thumb, unscaled)
    crammed into the ~30dp scaled taskbar is replaced by a custom
    slim slider in the source's proportions — everything × the wm
    scale s: 110·s dp wide (64·s and the % label hidden when the
    screen is < 700 dp wide, the source's media rule), 3.5·s track,
    12·s accent thumb, % label 11·s bold accent min-width 38·s —
    writing `toolbox_ui_scale` through the same store path as the
    Settings slider (step 5, 25–150), so they stay in sync. The
    shared `SlimSlider` composable also replaces the Settings
    page's stock slider (8 dp track, 20 dp thumb — the source's
    `h-2` + 20px thumb), which Workstream D had only recoloured.
    Reset button/behaviour (100% + toast) unchanged.
  - **Tests:** `V121LongPressHapticsTest` (3 JVM tests: the helper
    fires per call when enabled, stays silent when the toggle is
    off, and shares its gate with `tick()`), plus
    `web/test/long-press-haptics.test.mjs` (16 node checks against
    the real extracted detector: threshold, toggle gating, cancel
    paths, slop, non-primary buttons, de-dupe). Existing web
    harnesses still pass; all 9 inline web scripts parse.
  - **Version alignment:** versionCode 16 / versionName 1.2.1;
    Settings footers and the web edition's 3 stamps at 1.2.1.
- **Status (2026-10-22):** **v1.2.0** — versionCode 15, versionName `1.2.0`.
  Feature build (GitHub editions only): an in-app **update checker**
  for both editions plus one-tap install on Android.
  - **Source of truth:** `GET https://api.github.com/repos/shawnrodis/toolbox/releases/latest`
    (public, unauthenticated). The tag is compared against the app's
    own version (Android versionName; the web file's `<meta
    name="version">` stamp) with a numeric X.Y.Z comparison (strip
    "v", missing parts = 0, -pre/+build suffixes dropped) —
    `data/UpdateChecker.kt` natively, the "Toolbox Update Checker"
    block in web/Toolbox.html; identical rules and `toolbox_update_*`
    key names in both editions.
  - **Cadence/surfaces:** automatic check on launch, throttled to
    one ATTEMPT per 24 h (attempt time stamped up front), silent on
    failure; manual check any time from the new **Settings → App
    updates** card, which reports failures plainly. A found update
    surfaces in exactly two calm places: a dismissible Home banner
    (dismissal remembered per version) and the Settings card.
    Bookkeeping (`toolbox_update_last_check / _latest_version /
    _apk_url / _html_url / _release_url / _dismissed`) is
    device-local — never synced, untouched by Reset Everything.
  - **Android one-tap install:** the release APK asset is chosen by
    NAME (`Toolbox_v*.apk`, first match; its browser_download_url is
    used verbatim, never constructed) and downloaded to
    `cacheDir/updates/` with progress, then VERIFIED to be this app's
    package (`getPackageArchiveInfo` == `com.shawnrodis.toolbox`)
    before being handed via FileProvider to the system installer
    (ACTION_VIEW, package-archive MIME) — the user confirms the
    install; Android has no silent install for sideloaded apps. If
    "install unknown apps" isn't granted for Toolbox
    (`canRequestPackageInstalls()`), the tap first routes to that
    system settings screen with an explaining toast. Adds the
    `REQUEST_INSTALL_PACKAGES` permission and a `updates/` cache-path
    FileProvider entry. Download failure offers a retry plus an
    "Open releases page" fallback.
  - **Web:** the page cannot replace its own file, so the action is
    **Download the new version** (the release's `Toolbox_v*.html`
    asset URL from the API, releases page as fallback) with honest
    helper text ("open it once it's saved; your saved data stays in
    this browser").
  - **Tests:** `V120UpdateCheckerTest` (14 JVM tests: comparison,
    parsing/asset selection incl. decoys, throttle, dismissal) and
    `web/test/update-checker.test.mjs` (31 node checks driving the
    real extracted checker: units plus a full auto-check round trip,
    manual-failure reporting, dismissal, and the throttled no-fetch
    load). All inline web scripts re-verified with a parse pass.
  - **Not verifiable in this environment:** the actual on-device
    installer prompt and the unknown-sources settings round trip
    (no device/emulator here) — the code path is compile-verified
    and the package-name/permission gates are unit-reasoned, but the
    first real install prompt happens on Shawn's phone.
  - **Version alignment:** versionCode 15 / versionName 1.2.0;
    Settings footers and the web edition's 3 stamps at 1.2.0.
- **Status (2026-10-22):** **v1.1.1** — versionCode 14, versionName `1.1.1`.
  Fix build (GitHub editions only — the personal web edition was
  retired earlier today): guarantee the user-entered API keys
  (`toolbox_aviationstack_key`, `toolbox_gemini_key_1`,
  `toolbox_gemini_key_2` — already in SYNC_KEYS) actually reach every
  synced device, both providers, both editions. Audit findings:
  - **First-sync gap (both editions, both providers) — fixed.**
    Pending only ever recorded changes made WHILE sync was configured
    (`SyncManager.markDirty` / web `syncMarkDirty` both gated on
    configured), so values that existed before sync was enabled or
    signed in never pushed: the first pull didn't skip them (not
    pending) and an empty backend left them stranded. Fixes: the
    configured-gate is gone from dirty tracking (pending now means
    "not yet confirmed by the backend", which also makes changes and
    deletions made while signed out propagate on the next sign-in),
    and every push/pull first runs `seedPending` /
    `syncSeedPending()` — any SYNC_KEYS entry with a local value, no
    meta entry, and no pending mark is seeded as pending (keys WITH
    meta are never seeded, so a locally-absent synced key stays a
    pull deletion). Settings saves that wipe meta + pending (new
    server, provider switch, new Firebase config) re-seed from local
    state on the next tick, which is also the provider-migration
    behaviour wanted.
  - **Android clear/normalize divergence — fixed.** The web (source)
    savers trim the key and REMOVE it when blank (a sync deletion);
    the Android Settings fields stored the raw value per keystroke
    and stored `""` on clear (an update, leaving the editions holding
    different representations of "no key", and untrimmed keys syncing
    verbatim). The three fields now save via `AppStore.setApiKey` /
    `normalizeApiKeyValue`: trimmed, blank removes the key — chosen
    semantics: **cleared = removed (deletion)**, matching the source.
  - **Verified sound, unchanged:** push payload assembly and the
    pull merge already covered the API keys identically in both
    transports (server `deleted` list; Firebase PATCH nulls);
    receiving devices use pulled keys without a reinstall (Android
    tools read the reactive settings flow over the same DataStore
    keys; the web pre-fills Settings fields from localStorage on load
    and its resolvers read localStorage at call time, with a reload
    after an applying tick); the Android dirty hook is installed once
    at app start (`MainActivity` → `SyncManager.start()`).
  - **Tests:** new `V111ApiKeySyncTest` (10 JVM tests: key list
    membership, push payload + Firebase body carry entered keys, pull
    applies keys on a fresh device, trim/blank-removal semantics,
    clear → deletion → pull-delete round trip, seeding rules) and a
    new node harness `web/test/sync-api-keys.test.mjs` (14 checks
    driving the web edition's real sync functions through first-sync
    seeding over BOTH transports, trim/clear/delete propagation).
    FIREBASE_SYNC.md now states that the entered API keys sync into
    the user's own project (SYNC_SERVER.md already said so).
  - **Version alignment:** versionCode 14 / versionName 1.1.1;
    Settings footers and the web edition's 3 stamps at 1.1.1.
- **Status (2026-10-22):** **v1.1.0** — versionCode 13, versionName `1.1.0`.
  Feature release, four workstreams, all editions (Android + public
  web + Shawn's personal web copy):
  - **Firebase sync provider (A).** Settings → Sync Across Devices
    gains a provider choice: Off / Own server (unchanged) / Firebase.
    Firebase transport is Realtime Database REST only (no SDK, no
    google-services): state at `{databaseURL}/users/{uid}/state.json`
    in the same state-map shape, merged by the SAME engine as the
    server protocol (`SyncLogic.computePull` natively; an extracted
    `syncApplyState` in the web editions) — all 26 SYNC_KEYS. Writes
    use PATCH; deletions become JSON nulls (RTDB deletes the key),
    with the client ISO stamp for `updated_at` (RTDB has no server
    timestamps without extra calls). Auth is Identity Toolkit REST
    (signUp / signInWithPassword / securetoken refresh); config,
    email, refresh token and uid live under device-local
    `toolbox_sync_fb_*` keys — never synced, untouched by Reset
    Everything; sign-out clears them. Config is pasted as one blob
    (JSON or the console's `firebaseConfig = {...}` snippet) and
    parsed leniently (`parseFirebaseConfig`). 401/403 → one silent
    refresh + one retry → "Firebase session expired — sign in again
    in Settings." Setup doc: `FIREBASE_SYNC.md`. **Not live-verified**
    — no real Firebase project exists yet; config fields ship empty.
  - **Desktop Mode taskbar audit (B).** Against the source `wm*`
    behaviour: the house pin now closes the snap panel before
    show-desktop, shows `active` when no window is focused,
    transient (open, unpinned) pins list in tools order, and the
    source's document-click rule is mirrored — tapping any pin, tray
    button, desktop icon, blank desktop, or window closes the Start
    menu. Start-menu search already reset on open (conditional
    composition); Start↔Snap mutual closing and focus fall-through
    already matched.
  - **Haptics (C).** Device-local `toolbox_haptics` (absent = on,
    never synced, not reset). Attached at the central beep hook in
    both editions: web `navigator.vibrate(10)` guarded (no-op on
    desktop browsers / iOS Safari); Android a 10 ms one-shot through
    the shared `toolboxBeep()` funnel (`VIBRATE` permission added).
    Toggle sits with Sound in a Sounds & Haptics settings card.
  - **Settings visual parity (D).** The native Settings screen no
    longer uses stock Material controls — it replicates the HTML
    Settings card design (page card, section cards, icon boxes,
    source label/description type sizes, source palette per theme
    mode, pill buttons, segmented rows, source-styled fields and
    slider, HSV custom-accent picker kept). Section order matches
    the source; the pre-existing Fullscreen row and the new Sound /
    Haptics rows are styled as native citizens of the same design.
    The Reset confirm dialog stays a native Material dialog
    (functional parity).
  - Tests: 54/54 unit tests green (13 new in `V110FirebaseTest`:
    config parsing, provider defaults, fb keys never synced, state
    URL + push-body mapping, pulled RTDB state through the existing
    merge, auth response/error mapping, haptics default-on).
    `assembleDebug` clean; badging versionCode 13 / 1.1.0.
- **Status (2026-10-22):** **v1.0.7** — versionCode 12, versionName `1.0.7`.
  One on-device fix (Shawn): **SpongeBob Meme Generator now regenerates
  live while typing.** The source textarea carries
  `oninput="window.sbmHandleInput()"`, and `sbmHandleInput()` on EVERY
  keystroke updates the "N chars" counter, re-runs `sbmGenerateMeme()`,
  and persists the text to `toolbox_sbm_state`. The native port only
  regenerated on the Generate button, the mode toggle, and once at load —
  typing alone changed nothing on screen. Fix (`NotesSpongeScreens.kt`):
  a new `sbmGenerateOutput()` helper mirrors `sbmGenerateMeme()` exactly
  (blank/whitespace-only input → the `gEnErAtEd tExT HeRe` placeholder via
  the source's `!input.trim()` check; otherwise `sbmSpongeTransform`),
  and the text field's `onValueChange` now runs it on every change
  alongside the existing persist — so output, counter, and saved state
  all track each keystroke. The restore path (saved text on open) uses
  the same helper, matching the source calling `sbmHandleInput()` after
  restoring; the Generate button and mode toggle also route through it,
  exactly as the source's button and `sbmToggleMode()` call
  `sbmGenerateMeme()`. Clear is untouched. No caching: alternating mode
  still re-randomises the first letter's case per transform, per keystroke,
  as in the source. The "N chars" counter already existed under the
  field; it is now right-aligned to mirror the source's `#sbm-char-count`
  at the field's bottom-right (it derives from the input state, so it
  updates on every keystroke with no extra wiring). New unit tests
  (`V107SpongeLiveTest`) pin the generate semantics property-wise
  (blank → placeholder, strict alternation, non-letters untouched,
  per-prefix typing simulation).
  Version alignment: native Settings footer "Version 1.0.7"; both HTML
  editions restamped 1.0.6 → 1.0.7 (same 3 lines each, diff-verified).
- **Status (2026-10-22):** **v1.0.6** — versionCode 11, versionName `1.0.6`.
  Three on-device fixes, all in tool previews/exports (Shawn, v1.0.5
  screenshots):
  (1) **Square Pic export goes straight to Downloads** — the Export button
  previously saved the PNG to Pictures via MediaStore and then opened the
  Android share sheet (cacheSharedFile + shareUri). It now saves directly
  to the **Downloads** folder via the existing `saveFileToDownloads`
  helper (MediaStore.Downloads + RELATIVE_PATH=DIRECTORY_DOWNLOADS on API
  29+, legacy public Downloads dir under the existing WRITE_EXTERNAL_STORAGE
  maxSdk-28 permission below that), keeps the `<base>_square.png` filename
  and the "Exported <name>" toast, and fires NO chooser. This matches the
  web build, whose export is a plain browser download.
  (2) **Square Pic preview is a true square again** — v1.0.5's preview
  Canvas had a square layout box (fillMaxWidth + aspectRatio(1f)) but a
  Compose Canvas does not clip its drawing to its bounds, and the source
  geometry deliberately overflows the square (cover/fill rects), relying on
  the HTML canvas element to clip. The blurred backdrop and zoomed photo
  painted over the hint texts and the Background/Photo controls, and the
  blur branch double-applied the cover rect to the pre-composed square
  blurred bitmap, so preview ≠ export. Fix: centred outer box, source's
  520 max width, the one square Canvas clipped to rounded corners plus an
  explicit clipRect in the draw scope, blurred background drawn 1:1 into
  the square like the export renderer; drag-to-reposition unchanged
  (same SquareGeometry maths for preview and export).
  (3) **PDF Tools live preview is big and readable** — Rotate/Generic
  previews rendered the page at the source's literal pdf.js factor
  (0.5 × previewScale → a ~306px-wide Letter bitmap) and displayed it at
  intrinsic size (~65–100dp on a high-density phone, centred in an empty
  card); the preview-scale slider only changed render resolution, never
  display size. New `BigPagePreview`: display width = the preview card's
  inner width at scale 1.0 × previewScale (centred when narrower,
  horizontally scrollable when wider — the source wrapper is
  overflow-auto), render factor raised to 1.5 × previewScale (capped 3.0).
  Rotate shows the rotation live by rotating the rendered page bitmap
  (the source's CSS-transform equivalent, with a correct layout
  footprint). Split grid thumbs now fill their cell width (source:
  max-w-full); Images-to-PDF thumbs take the source's 120 × scale height
  cap; Merge rows keep their small thumbs, as in the source.
  Version alignment: native Settings footer "Version 1.0.6"; both HTML
  editions restamped 1.0.5 → 1.0.6 (same 3 lines each, diff-verified).
- **Status (2026-10-22):** **v1.0.5** — versionCode 10, versionName `1.0.5`,
  clean `gradlew clean assembleDebug` + `testDebugUnitTest` verified
  (37 unit tests, 0 failures). Desktop Mode tray parity fix (Shawn
  on-device find): the HTML taskbar tray ends with an Exit button
  (`#taskbar-exit-btn`, fa-right-from-bracket, `toggleDesktopMode()`),
  which the native tray lacked entirely — on a landscape-locked screen
  there was no visible way out of Desktop Mode except the Settings
  toggle. The native tray now matches the source order exactly — theme
  segment (Light/Dark/Black) → clock → sound → fullscreen → Settings →
  Close all → **Exit desktop mode** (LAST; Icons.Filled.ExitToApp,
  42·s dp button / 18·s icon). Exit mirrors the header toggle's manual
  off-path: persisted `toolbox_desktop=false` when set, ToolboxApp's
  suppress-auto latch raised via a new `DesktopShell.onExitDesktop`
  callback (no bounce-back into landscape auto-desktop), and the
  source's "Desktop layout disabled." toast. The native-only tray
  "Show desktop" button was removed: the source exposes `wmShowDesktop()`
  only via the Start menu's first entry ("Desktop", "Return to the
  desktop") and the first taskbar pin (Desktop/house), both routing
  through `wmToggle('tab-home')` — the native port already had both
  (Start-menu "Desktop" entry and Desktop house pin → `wmToggle("home")`),
  so Show Desktop stays reachable in exactly the source's places.
  Settings version-footer parity: native Settings now ends with
  "Toolbox · Android Edition · Version 1.0.5" (small, muted, centred),
  matching the web editions' footer; both HTML editions were restamped
  1.0.4 → 1.0.5 in the same step (header comment, version meta tag,
  Settings footer — 3 lines each, diff-verified), per the shared-version
  alignment rule.
- **Status (2026-10-22):** **v1.0.4** — versionCode 9, versionName `1.0.4`.
  Shopping items now sync: `toolbox_shop_items` was appended to SYNC_KEYS
  (26 keys) at the owner's direction on 2026-10-22, overriding the HTML
  original's exclusion; protocol otherwise unchanged. SYNC_SERVER.md's
  key list and reference-server allowlist updated to match.
- **Status (2026-10-22):** **v1.0.3 public-release prep** — versionCode 8,
  versionName `1.0.3`. The sync server URL is no longer built into the
  app: it is a user-entered setting (Settings → Sync Across Devices →
  "Sync Server URL", stored under `toolbox_sync_url`, default empty),
  exactly like the sync token. With no URL (or no token) saved, sync is
  unconfigured — no network calls, and the status line / Sync Now toast
  prompt the user to enter their sync server address and token. With
  both present, sync behaviour is unchanged (same 25-key SYNC_KEYS list,
  {token, updates, deleted} push body, token in header + body, 2.5s
  debounce, interval options, server-newer-wins pull). The whole
  project tree was grep-scrubbed for the previously built-in endpoint
  (host and port): zero hits. API keys (Gemini/Aviationstack) remain
  user-entered only; no key literals exist in the source.
- **Status (2026-10-22):** **v1.0.2 fix build** — versionCode 7,
  versionName `1.0.2`, clean `gradlew clean assembleDebug` +
  `testDebugUnitTest` verified (32 unit tests, 0 failures; 4 new in
  V102FixTest). Fixes from Shawn's on-device review of 1.0.1:
  (A) **Tool card header text sliver** — the v1.0.1 header Row let the
  unweighted button group claim its full intrinsic width first,
  crushing the weighted title/description column to ~20-60dp on a
  portrait phone (mid-word wrap ribbons). The header now measures via
  a Layout (`ToolHeaderRow` + pure `headerSingleRow`): single row only
  when the text column keeps ≥120dp, otherwise the buttons wrap onto
  their own right-aligned row under the title block; wide/desktop
  stays a single row. (B) **PickerField single-line** — the dropdown
  anchor was a multi-line OutlinedTextField (no singleLine), so long
  values wrapped into 5-line-tall boxes; the anchor is now a fixed
  56dp outlined container (OutlinedTextFieldDefaults.Container, real
  field border/shape/focus highlight) holding a maxLines=1 ellipsized
  Text + chevron, exactly like an HTML <select>. All fields inherit it
  (PickerField is the only dropdown implementation). (C) **Safe
  insets** — both shells now apply `WindowInsets.safeDrawing`
  (status bar + display cutout + navigation bar): the mobile shell's
  root Column (with Scaffold content insets zeroed to avoid double
  padding) and the Desktop shell's root Column (background stays
  edge-to-edge, so the taskbar clears the Android nav bar, icons clear
  the landscape cutout, and the screen widget clears the status bar).
  Fullscreen/immersive ON hides the bars, the insets collapse to zero
  and both shells expand full-screen, as in the source. (D) **Snap
  panel + FAB clearance** — the snap-layouts panel was fixed at 320·s
  wide for ~460·s of tiles (rightmost tiles clipped at every scale);
  it is now content-sized via pure `snapPanelWidthDp`/`snapTilesPerRow`
  (253dp at s=0.55 on a 780dp-wide phone, all six tiles in one row),
  clamped to the desktop area with tiles wrapping to a second row when
  clamped. Tool screens (ToolScreen, Flight/PDF content, Square Pic)
  and the home List/Android grids get 88dp bottom clearance in the
  mobile shell (52dp FAB + 16dp margin + gap; 8dp in windows), and
  Currency's exchange-rate status text now wraps inside its summary
  box. Prior: **v1.0.1 fix build** — versionCode 6,
  versionName `1.0.1`, clean `gradlew clean assembleDebug` +
  `testDebugUnitTest` verified (28 unit tests, 0 failures). Fixes from
  Shawn's on-device review of 1.0.0: (1) **Settings is no longer a
  tile** — one shared display list mirrors the source's
  `sortedToolsForDisplay()` (excludes tab-settings/tab-home) and drives
  the home views, Quick Switcher, Start menu and desktop icons; Settings
  is reached via the global header gear or the Desktop taskbar gear.
  (2) **Global header added** to the mobile shell (the source's
  #app-header, hidden in Desktop Mode): brand block + Fullscreen /
  Desktop / Settings / Sound buttons, DYNAMIC title (home = "Toolbox" /
  "Shawn Rodis"; any tool = registry name / "Toolbox Utility"), manual
  desktop toggle wins over landscape auto-desktop. (3) **Desktop Mode
  display scaling** — the source's `wmApplyScale` is implemented
  (s = clamp(min(w/1600, h/900), 0.55, 1.3); s = 0.55 on a 780×360dp
  landscape phone) and sizes all desktop chrome (taskbar 54·s, icon
  cards 108·s×100·s in the source's column-major A-Z flow, titlebars,
  Start menu, snap panel, widget, menus); window geometry/defaults use
  the source's `defaultRect()` + MIN_W/MIN_H in real dp. (4) **Tool
  card headers** — every tool opens with the source's in-card header
  block (accent icon box, per-tool H2 + 11px description, tool-specific
  header buttons like Currency's Clear/Refresh, and the Share/JPG/PDF
  trio with fa-share-nodes emerald / fa-file-image sky / fa-file-pdf
  rose); the bottom export pill row and all Material TopAppBars are
  gone. (5) **Field-layout audit** — labels above fields app-wide
  (Currency's paired From/To Amount|Country rows + EXCHANGE RATE box
  with the status inside, Time's paired 12h/zone row, Unit Converter's
  source labels + swap icon, PTO's source labels, Tip's slider badge
  row). Prior: **FINAL PARITY PASS complete** — versionCode 5,
  versionName `1.0.0-parity`, clean `gradlew clean assembleDebug` +
  `testDebugUnitTest` verified (22 unit tests, 0 failures). All 14 tools
  are live, and the parity items are in: home view styles (List / iPhone
  pager / Android drawer with the source's tiles, FA-equivalent icons,
  pin-first ordering, name+description search, Home FAB with long-press
  Quick Switch sheet), **Desktop Mode** (ui/Desktop.kt — window manager
  with draggable/resizable/snappable windows hosting the tool contents,
  taskbar with Start menu + search, pins + open windows, all six snap
  layouts, theme segment, live clock, sound/fullscreen/Settings/show-
  desktop/close-all tray, desktop icons with saved positions and the
  source's snap/drag options + both context menus, screen widget with
  physical resolution + refresh rate, landscape auto-desktop), Settings
  1:1 (full HSV custom-colour picker, UI scale applied app-wide, Reset
  Everything with the source's exact key lists), exact PCM-synthesized
  beeps, in-app toast pills, and bundled Inter/Fredoka fonts.
  Named simplifications: window animations are instant; window resize
  handles are the SE corner + right/bottom edges (of the source's 8);
  exports keep the summary-card capture (Compose cannot screenshot
  un-composed off-screen scroll content — see the coverage checklist).
  The acceptance gate was re-run: 333 functions / 100 handlers extracted,
  every group DONE or carrying a documented disposition in
  `~/workspace/native-android/Function_Coverage_Checklist_2026-10-22.md`.
  Build-env note: the sandbox egress CA was re-keyed AGAIN during this
  pass; the fix is the same as before — replace the `hatch-egress-ca`
  alias in the workspace JDK cacerts with the current
  `/usr/local/share/ca-certificates/hatch-egress-ca.crt` and stop the
  Gradle daemon. Phase 3 added: **Square Pic** (full sq* control set —
  blur/solid/gradient+angle backgrounds, fit/fill, zoom, pan, border
  toggle/color/width, swatches, 2048×2048 PNG export via MediaStore +
  share); **PDF Tools** (queue, the source's six tools, PdfBox-Android
  for merge/split/rotate/images→PDF and searchable-OCR text layers,
  PdfRenderer previews, preview scale persisted as
  `pdf_suite_preview_scale`; OCR = ML Kit local + Gemini Vision cloud
  with user-key 1→2 failover, no built-in keys); **Sync** (the source's
  exact 25-key SYNC_KEYS list, push/pull JSON shapes, dirty/pending
  tracking, 2.5s debounce, interval setting + labels, Sync Now + status
  line; WorkManager periodic at the platform's 15-minute floor plus an
  in-app tick at the true interval while the app is open); and
  **exports** (Share/JPG/PDF trio on every tool screen — Split/Tip/Tally
  dedicated file bases and headers, generic exportToolTo*/shareAppToJPG
  + __shareTextFor behaviour for the rest, stamped filenames
  `name_YYYY-MM-DD_HH-mm-ss.ext`). Everything from Phase 1 (Home,
  Settings, Currency, Time, Shopping Assistant, Unit Converter, PTO,
  Split, Tip, Tally, Notes, SpongeBob) and Phase 2 (Flight Tracker,
  `skytrack_mobile_state`) is unchanged and still building.
  A final parity phase (per Shawn's 2026-10-22 decision that the APK must
  match the HTML app) will add the home icon-grid styles, Desktop Mode
  (window manager / taskbar / Start menu) and per-tool UI fidelity; for
  that reason tool screens are window-agnostic content composables
  hosted by thin full-screen wrappers, with all state in the shared store.
  Build-env notes from Phase 3: the proxy relay can go stale (restart it
  if Google Maven hangs), and the sandbox egress CA was re-keyed once —
  if Gradle reports PKIX "signature check failed", re-import the current
  CA (presented via the relay) into the workspace JDK truststore and
  restart the Gradle daemon. Live Gemini OCR and live sync are NOT
  verified — no user keys / sync token exist in the build env; Shawn's
  on-device test is the first live check for both.
  Live Aviationstack behaviour is NOT yet verified — the build env has no
  key; Shawn's on-device test with his own key is the first live check.
  Parser behaviour is verified by unit tests against a sample response
  built from the fields the source JS reads.
  Known simplifications vs the web version: beeps use ToneGenerator presets
  (no arbitrary Web Audio frequencies); SpongeBob "Enter to copy" is an
  explicit Copy button; the web Desktop/window-manager home is a native list
  until the parity phase.

## Source app and audit

- Source HTML (uploaded by Shawn 2026-10-22, 461 KB, 7,772 lines, ~335 JS functions):
  `~/workspace/user/files/Toolbox__Shawn_Rodis__0_c2n6.html`
- Full audit: `~/workspace/native-android/Toolbox_Native_Audit_2026-10-22.md`
- Tools identified (14 + Settings + Desktop/Home):
  Currency Converter, Time Converter, Shopping Assistant, Unit Converter,
  PTO Tracker, Split Gas & Parking, Tip & Bill Split, Tally Counter, Notes,
  Flight Tracker / SkyTrack (Aviationstack), SpongeBob Meme Generator,
  PDF Tools (Gemini OCR + Tesseract), Square Pic, and Settings
  (accent themes / API keys / Sync). The web Desktop/window-manager home
  becomes a native home grid + navigation on Android.

## ⚠️ Security — API keys

The HTML contains built-in/hardcoded Gemini and Aviationstack key variables.

- **Do NOT copy any key values into this Android project.**
- **Do NOT hardcode keys in the APK** — an APK can be unpacked and keys extracted.
- The native Settings screen will store **user-entered keys on-device only**
  (entered once by Shawn, like the HTML Settings override fields), and network
  calls read the keys from that on-device store at runtime.

## Project structure

```
toolbox-native/
├── settings.gradle.kts          # google() + mavenCentral(), :app module
├── build.gradle.kts             # plugin versions: AGP 8.5.2, Kotlin 2.0.20, Compose plugin 2.0.20
├── gradle.properties
├── gradle/wrapper/              # Gradle wrapper (8.7) — generated, see Build below
├── AUDIT_TEMPLATE.md            # per-feature checklist for the port
├── README.md                    # this file
└── app/
    ├── build.gradle.kts         # compileSdk/targetSdk 35, minSdk 26, Java 17,
    │                            # Compose + Material3 + Navigation Compose + DataStore
    └── src/main/
        ├── AndroidManifest.xml  # no permissions in the scaffold
        ├── java/com/shawnrodis/toolbox/
        │   ├── MainActivity.kt  # Compose Navigation shell, Home placeholder:
        │   │                    # "Tools will appear here after audit"
        │   └── ui/theme/Theme.kt # Material 3 light/dark color schemes
        └── res/values/          # strings.xml, themes.xml
```

Dependencies already declared in `app/build.gradle.kts`: Compose BOM 2024.09.00,
Material3, `navigation-compose` 2.7.7, `datastore-preferences` 1.1.1,
core-ktx, lifecycle-runtime-ktx, activity-compose.

## Toolchain (workspace-local, verified 2026-10-22)

Everything lives under `~/workspace/tools/` (persistent workspace — nothing was
installed to `/usr` or `/opt`):

| Component | Version verified | Location |
|---|---|---|
| OpenJDK (Temurin) | `17.0.20.1+1` — `java -version` OK | `~/workspace/tools/jdk17/` |
| Gradle | `8.7` — `gradle -v` OK | `~/workspace/tools/gradle/gradle-8.7/` |
| Android cmdline-tools | `Pkg.Revision=12.0` (11076708 zip) | `~/workspace/tools/android-sdk/cmdline-tools/latest/` |
| Platform | API 35 (`AndroidVersion.ApiLevel=35`, Pkg.Revision 2) | `~/workspace/tools/android-sdk/platforms/android-35/` |
| Build-tools | `35.0.0` | `~/workspace/tools/android-sdk/build-tools/35.0.0/` |
| Platform-tools / adb | adb 1.0.41, version `37.0.1-15733141` | `~/workspace/tools/android-sdk/platform-tools/` |

Set up your shell before building:

```sh
source ~/workspace/tools/env/android-env.sh
# sets JAVA_HOME, ANDROID_HOME / ANDROID_SDK_ROOT, and PATH
```

**Known toolchain caveats in this VM (all diagnosed 2026-10-22, fixes applied):**

1. `sdkmanager` could not install packages — its HTTPS fetch fails through the
   egress proxy with `java.util.NoSuchElementException … HttpURLConnection.doTunneling0`.
   Workaround used, which succeeded: download the official package zips from
   `dl.google.com` with `curl` (platform-tools, `platform-35_r02.zip`,
   `build-tools_r35_linux.zip`) and unpack them into the SDK layout above, plus
   the standard public SDK license hashes under `android-sdk/licenses/`.
2. The egress proxy is TLS-intercepting (issuer "Hatch Sandbox Egress CA"), so
   Java initially rejected every HTTPS download (`PKIX path building failed`).
   Fix applied: that CA was imported into the workspace JDK's truststore
   (`~/workspace/tools/jdk17/lib/security/cacerts`, alias `hatch-egress-ca`).
3. Java in this VM uses IPv4-mapped IPv6 sockets by default, which the sandbox
   network intercepts/resets (Gradle daemon IPC and HTTP downloads failed).
   Fix applied: `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"` — set by
   `android-env.sh`, so every JVM (client, daemon, workers) gets it at launch.
4. Gradle's HTTP client still could not download through the egress proxy
   directly (`Connection reset`), so dependency downloads go through a tiny
   local relay: `~/workspace/tools/proxy-relay/relay.js` (Node, listens on
   `127.0.0.1:18080`, forwards CONNECT tunnels to the egress proxy over IPv4,
   reads upstream credentials from the environment at runtime — none are stored
   in any project file). Start it before building in this VM:
   `nohup node ~/workspace/tools/proxy-relay/relay.js &`
   Gradle is pointed at it from `~/workspace/tools/gradle-home/gradle.properties`
   (the `GRADLE_USER_HOME` set by `android-env.sh`) — **not** from this project,
   so the project stays portable to a normal network / Android Studio.

## How to build

```sh
source ~/workspace/tools/env/android-env.sh
cd ~/workspace/native-android/toolbox-native
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

- The Gradle wrapper (`gradlew`, `gradle/wrapper/`) was generated with the
  workspace Gradle 8.7 (`gradle wrapper --gradle-version 8.7`).
  If it is ever missing, regenerate it with that command — do not download a
  random wrapper jar.
- `local.properties` (git-ignored) must point at the SDK on this machine:
  `sdk.dir=/home/hatch/workspace/tools/android-sdk`
- On this VM, Gradle dependency downloads must go through the egress proxy
  already present in the environment (`http_proxy` / `https_proxy`); pass the
  proxy as JVM system properties at build time if a plain build cannot reach
  `google()` / `mavenCentral()`. Do not commit proxy credentials to this repo.
- Release / Play Store builds are out of scope for the scaffold: they will need
  Shawn's own signing keystore, which must never be committed either.

Build verification status is reported in the handoff that delivered this
scaffold (whether `assembleDebug` was run, and its exact outcome) — the README
intentionally does not claim a successful build on its own.

VM note: `./gradlew` also works here — its Gradle 8.7 distribution was
pre-seeded into `~/workspace/tools/gradle-home/wrapper/dists/` from the
official zip in `~/workspace/tools/downloads/`, because downloading it from
`services.gradle.org` fails certificate validation through this VM's proxy.
On a normal network the wrapper downloads it itself, as usual.

## What is still blocked / not done

- ✅ HTML upload — **received** 2026-10-22 (`Toolbox__Shawn_Rodis__0_c2n6.html`).
- ✅ Audit — **done**, see the audit file linked above.
- ✅ **Porting — Phases 1–3 done** (see Status above). Only the final parity
  phase (home styles + Desktop Mode) remains.
- ⏳ No API keys are present in this project (by design, see Security above) —
  Flight Tracker and Gemini OCR will not work until Shawn enters his keys in
  the native Settings screen, and Sync stays "Not configured" until he saves
  his sync token in Settings → Sync Across Devices.
- ⏳ Desktop Mode / home view styles: in scope for the final parity phase
  per Shawn's 2026-10-22 decision (the APK must match the HTML app, which
  stays the desktop/laptop version of the same product).

## Post-upload steps (the port phase)

1. **Audit** — done; use `AUDIT_TEMPLATE.md` per feature for the detailed pass.
2. **Feature mapping** — per tool: UI → Compose screen/route, JS logic → Kotlin,
   `localStorage` → DataStore (simple state) or Room (lists: Notes, PTO logs,
   tallies, shopping, tracked times, saved flights).
3. **Port storage & APIs** — open.er-api.com (Currency) → Ktor/Retrofit with
   cached rates; Aviationstack (Flight Tracker) → user-entered key; Gemini
   (PDF OCR, Key1 → Key2 failover) → user-entered keys; Tesseract.js →
   ML Kit on-device text recognition; pdf-lib/pdf.js → PdfRenderer + a Kotlin
   PDF library; device Sync endpoint → ported exactly from the JS sync code, last.
4. **Phased APKs** (per the audit):
   - Phase 1: Home + Settings + Currency, Time, Unit Converter, Shopping
     Assistant, PTO, Split, Tip, Tally, Notes, SpongeBob.
   - Phase 2: Flight Tracker.
   - Phase 3: Square Pic, PDF Tools incl. OCR, Sync, JPG/PDF exports.
   Each phase: `./gradlew assembleDebug`, verify the APK, then deliver.
