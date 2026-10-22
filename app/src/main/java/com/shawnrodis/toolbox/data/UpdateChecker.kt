package com.shawnrodis.toolbox.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * v1.2.0 — in-app update checker (both editions share the design; the
 * web counterpart lives in web/Toolbox.html's "Toolbox Update Checker"
 * block and uses the same keys, intervals and comparison rules).
 *
 * Source of truth for "latest": the public GitHub Releases API of the
 * app's own repo — one unauthenticated GET per check, no identifiers
 * sent beyond the ordinary HTTP request. Checks run automatically on
 * launch, throttled to one attempt per 24 h, and manually any time
 * from Settings → App updates. Auto-check failures are silent; manual
 * failures are reported on the Settings card.
 *
 * Bookkeeping is device-local DataStore (the toolbox_update_* keys
 * below) — never in SYNC_KEYS, never touched by Reset Everything,
 * mirroring the toolbox_sync_fb_* pattern.
 */
const val UPDATE_API_URL =
    "https://api.github.com/repos/shawnrodis/toolbox/releases/latest"
const val UPDATE_RELEASES_PAGE_URL =
    "https://github.com/shawnrodis/toolbox/releases/latest"

const val UPDATE_LAST_CHECK_KEY = "toolbox_update_last_check"       // epoch ms of last attempt
const val UPDATE_LATEST_VERSION_KEY = "toolbox_update_latest_version"
const val UPDATE_APK_URL_KEY = "toolbox_update_apk_url"
const val UPDATE_HTML_URL_KEY = "toolbox_update_html_url"
const val UPDATE_RELEASE_URL_KEY = "toolbox_update_release_url"
const val UPDATE_DISMISSED_KEY = "toolbox_update_dismissed"         // version dismissed on Home

const val UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

private val UPDATE_APK_ASSET = Regex("^Toolbox_v.+\\.apk$")
private val UPDATE_HTML_ASSET = Regex("^Toolbox_v.+\\.html$")

/**
 * Numeric parts of a version string: a leading "v"/"V" is stripped,
 * any pre-release/build suffix ("-beta", "+5") is dropped, and each
 * dot-separated part is parsed as an int (non-numeric → 0), so
 * "v1.10.0" → [1, 10, 0] and "1.2" → [1, 2]. Never throws.
 */
fun versionParts(version: String): List<Int> {
    var v = version.trim()
    if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1)
    v = v.split('-', '+').firstOrNull()?.trim().orEmpty()
    if (v.isEmpty()) return listOf(0)
    return v.split('.').map { it.trim().toIntOrNull() ?: 0 }
}

/** -1 / 0 / 1 as [a] is older than / equal to / newer than [b]. Missing parts count as 0. */
fun compareVersions(a: String, b: String): Int {
    val pa = versionParts(a)
    val pb = versionParts(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return if (x < y) -1 else 1
    }
    return 0
}

fun isUpdateAvailable(current: String, latest: String?): Boolean =
    !latest.isNullOrBlank() && compareVersions(latest, current) > 0

/** Auto-check throttle: at most one attempt per [intervalMs]; never checked (≤ 0) always checks. */
fun shouldAutoCheck(
    lastCheckMs: Long,
    nowMs: Long,
    intervalMs: Long = UPDATE_CHECK_INTERVAL_MS
): Boolean = lastCheckMs <= 0L || nowMs - lastCheckMs >= intervalMs

/** Home banner shows only for an undismissed, actually-newer version. */
fun updateBannerVisible(current: String, latest: String?, dismissed: String?): Boolean =
    isUpdateAvailable(current, latest) && dismissed?.trim() != latest?.trim()

/** The pieces of a GitHub release the updater needs. */
data class LatestRelease(
    val version: String,          // tag_name without the leading "v"
    val apkUrl: String?,
    val htmlUrl: String?,
    val releasePageUrl: String?
)

/**
 * Parses a GitHub "latest release" response. Assets are picked by
 * NAME (Toolbox_v*.apk / Toolbox_v*.html, first match wins) and their
 * browser_download_url is used verbatim — the download URL pattern is
 * never constructed locally. Returns null when there is no usable tag.
 */
fun parseLatestRelease(json: JSONObject): LatestRelease? {
    val tag = json.optString("tag_name").trim()
    if (tag.isEmpty()) return null
    val version = tag.removePrefix("v").removePrefix("V")
    var apk: String? = null
    var html: String? = null
    val assets = json.optJSONArray("assets")
    if (assets != null) {
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (url.isEmpty()) continue
            if (apk == null && UPDATE_APK_ASSET.matches(name)) apk = url
            if (html == null && UPDATE_HTML_ASSET.matches(name)) html = url
        }
    }
    return LatestRelease(
        version = version,
        apkUrl = apk,
        htmlUrl = html,
        releasePageUrl = json.optString("html_url").ifEmpty { null }
    )
}

/** UI state shared by the Home banner and the Settings App-updates card. */
data class UpdateUiState(
    val checking: Boolean = false,
    val latestVersion: String? = null,
    val apkUrl: String? = null,
    val htmlUrl: String? = null,
    val releasePageUrl: String? = null,
    val error: String? = null,           // manual-check failures only
    val downloading: Boolean = false,
    val downloadPercent: Int = -1,       // -1 = indeterminate
    val downloadError: String? = null,
    val readyFile: File? = null
)

class UpdateManager private constructor(
    private val appContext: Context,
    private val store: AppStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state

    @Volatile
    private var hydrated = false

    fun currentVersion(): String = try {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** Loads the persisted last-known release info once (instant UI). */
    private suspend fun hydrate() {
        if (hydrated) return
        val snap = store.snapshot(
            listOf(
                UPDATE_LATEST_VERSION_KEY, UPDATE_APK_URL_KEY,
                UPDATE_HTML_URL_KEY, UPDATE_RELEASE_URL_KEY
            )
        )
        _state.update {
            it.copy(
                latestVersion = snap[UPDATE_LATEST_VERSION_KEY]?.ifEmpty { null },
                apkUrl = snap[UPDATE_APK_URL_KEY]?.ifEmpty { null },
                htmlUrl = snap[UPDATE_HTML_URL_KEY]?.ifEmpty { null },
                releasePageUrl = snap[UPDATE_RELEASE_URL_KEY]?.ifEmpty { null }
            )
        }
        hydrated = true
    }

    /** Launch check: throttled to one attempt per 24 h; failures silent. */
    fun autoCheck() {
        scope.launch {
            runCatching {
                hydrate()
                val last = store.snapshot(listOf(UPDATE_LAST_CHECK_KEY))[UPDATE_LAST_CHECK_KEY]
                    ?.toLongOrNull() ?: 0L
                if (shouldAutoCheck(last, System.currentTimeMillis())) check(manual = false)
            }
        }
    }

    /** Manual check from Settings: ignores the throttle, reports errors. */
    fun checkNow() {
        scope.launch {
            hydrate()
            check(manual = true)
        }
    }

    private suspend fun check(manual: Boolean) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, error = if (manual) null else it.error) }
        // The attempt time is stamped up front: the throttle counts
        // attempts, so a launch on a dead network doesn't re-check
        // (and re-fail) on every subsequent launch that day.
        store.setStringStateRaw(UPDATE_LAST_CHECK_KEY, System.currentTimeMillis().toString())
        try {
            val rel = parseLatestRelease(JSONObject(httpGet(UPDATE_API_URL)))
                ?: throw IllegalStateException("Unexpected response from GitHub")
            store.setStringStateRaw(UPDATE_LATEST_VERSION_KEY, rel.version)
            store.setStringStateRaw(UPDATE_APK_URL_KEY, rel.apkUrl ?: "")
            store.setStringStateRaw(UPDATE_HTML_URL_KEY, rel.htmlUrl ?: "")
            store.setStringStateRaw(UPDATE_RELEASE_URL_KEY, rel.releasePageUrl ?: "")
            _state.update {
                it.copy(
                    checking = false,
                    latestVersion = rel.version,
                    apkUrl = rel.apkUrl,
                    htmlUrl = rel.htmlUrl,
                    releasePageUrl = rel.releasePageUrl,
                    error = null
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    checking = false,
                    error = if (manual) {
                        "Couldn't check for updates — check your connection and try again."
                    } else {
                        it.error
                    }
                )
            }
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Toolbox-Android/${currentVersion()}")
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** True when Android allows Toolbox to hand an APK to the installer. */
    fun canInstall(): Boolean =
        appContext.packageManager.canRequestPackageInstalls()

    /**
     * Downloads the release APK (the URL chosen by [parseLatestRelease])
     * into cacheDir/updates/, tracking progress in [state]. The result
     * is verified to really be this app's package before it is offered
     * to the installer. Returns the verified file, or null (with
     * [UpdateUiState.downloadError] set) on any failure.
     */
    suspend fun downloadUpdate(): File? {
        val url = _state.value.apkUrl
        if (url.isNullOrEmpty()) {
            _state.update { it.copy(downloadError = "No APK was attached to that release.") }
            return null
        }
        _state.update {
            it.copy(downloading = true, downloadPercent = -1, downloadError = null, readyFile = null)
        }
        return try {
            val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "Toolbox-update.apk")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Toolbox-Android/${currentVersion()}")
            try {
                if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt().coerceIn(0, 100)
                                _state.update { it.copy(downloadPercent = pct) }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            val pkg = appContext.packageManager.getPackageArchiveInfo(
                out.absolutePath, PackageManager.GET_META_DATA
            )?.packageName
            if (pkg != appContext.packageName) {
                out.delete()
                _state.update {
                    it.copy(
                        downloading = false,
                        downloadError = "That download wasn't a Toolbox package — install cancelled."
                    )
                }
                return null
            }
            _state.update { it.copy(downloading = false, downloadPercent = 100, readyFile = out) }
            out
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    downloading = false,
                    downloadError = "Download failed — check your connection and try again."
                )
            }
            null
        }
    }

    /** System package-installer intent for a verified downloaded APK. */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            appContext, appContext.packageName + ".fileprovider", file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** The system screen where the user allows installs from Toolbox. */
    fun unknownSourcesIntent(): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + appContext.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        @Volatile
        private var instance: UpdateManager? = null

        /** One shared manager per process (Home + Settings + MainActivity). */
        fun get(appContext: Context, store: AppStore): UpdateManager =
            instance ?: synchronized(this) {
                instance ?: UpdateManager(appContext.applicationContext, store)
                    .also { instance = it }
            }
    }
}
