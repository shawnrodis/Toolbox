package com.shawnrodis.toolbox.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Toolbox Self-Hosted Sync — engine (HTTP + scheduling), the native
 * counterpart of the source's sync* runtime in the HTML app. All payload
 * and pull/push maths live in SyncLogic.kt and mirror the source exactly.
 *
 * Differences forced by the Android platform (documented, not silent):
 *  - The web app polls with setInterval while the page is open. Natively
 *    the same intervals run in an in-app loop while the app process is
 *    alive; when the app is closed, WorkManager runs a periodic tick with
 *    a 15-minute floor (Android's minimum periodic interval), so a chosen
 *    interval below 15 minutes only takes full effect while the app is
 *    open. Auto-sync OFF (0) disables both, as in the source.
 *  - The web app location.reload()s after a pull applied changes; native
 *    DataStore flows update every screen live, so no reload is needed.
 *  - syncSaveSettings in the source fires from the settings fields'
 *    onchange; natively the Settings Sync section has an explicit Save
 *    button with the identical effects (store URL + token, wipe meta +
 *    pending, apply schedule, startup tick when sync is configured).
 *
 * SECURITY: the token is read from DataStore at call time and placed only
 * in the X-Toolbox-Token header and the JSON body (exactly as the source
 * does). It is never logged, never put in status strings, never in URLs.
 */
class SyncManager(private val appContext: Context, private val store: AppStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tickMutex = Mutex()
    private var pushJob: Job? = null
    private var loopJob: Job? = null
    @Volatile private var tickRunning = false

    private val _status = MutableStateFlow<String?>(null)
    /** Mirrors the source's #sync-status line ("Not configured" until set). */
    val status: StateFlow<String?> = _status

    private suspend fun token(): String =
        store.stringState(SYNC_TOKEN_KEY, "").first()

    private suspend fun url(): String =
        store.stringState(SYNC_URL_KEY, "").first()

    private suspend fun configured(): Boolean = syncConfigured(url(), token())

    fun setStatus(text: String) {
        _status.value = text
    }

    /** App-start wiring: dirty hook + schedule + source's syncStartup(). */
    fun start() {
        store.syncedKeyChangedHook = { key -> markDirty(key) }
        scope.launch {
            if (configured()) {
                applySchedule()
                tick() // syncStartup() == one tick at startup
            } else {
                setStatus("Not configured")
            }
        }
        startInAppLoop()
    }

    // ---- dirty tracking (source: localStorage wrapper -> syncMarkDirty) --

    /** Source syncMarkDirty(): record pending + schedule a debounced push. */
    fun markDirty(key: String) {
        scope.launch {
            if (!configured()) return@launch
            val pending = parseSyncPending(store.stringState(SYNC_PENDING_KEY, "").first())
            pending[key] = 1
            store.setStringState(SYNC_PENDING_KEY, syncPendingToJson(pending))
            schedulePush()
        }
    }

    /** Source syncSchedulePush(): push after 2.5s of quiet. */
    private fun schedulePush() {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(SYNC_PUSH_DEBOUNCE_MS)
            if (configured()) pushNow()
        }
    }

    // ---- HTTP plumbing -------------------------------------------------

    private fun openConnection(method: String, baseUrl: String, token: String): HttpURLConnection {
        val conn = URL(syncEndpoint(baseUrl)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        // Source syncAuthHeaders()
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Toolbox-Token", token)
        return conn
    }

    data class OpResult(val ok: Boolean, val applied: Int = 0, val error: String? = null)

    /** Source syncPushNow(). Never throws; errors land in the status line. */
    suspend fun pushNow(): OpResult = withContext(Dispatchers.IO) {
        val tok = token()
        val baseUrl = url()
        if (!syncConfigured(baseUrl, tok)) return@withContext OpResult(false, error = "not-configured")
        val pending = parseSyncPending(store.stringState(SYNC_PENDING_KEY, "").first())
        val payload = assemblePush(tok, pending, store.snapshot(SYNC_KEYS))
        if (payload.keys.isEmpty()) return@withContext OpResult(true, applied = 0)
        try {
            val conn = openConnection("POST", baseUrl, tok)
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val data = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            // Clear exactly the keys we pushed, then stamp meta with data.at.
            val p2 = parseSyncPending(store.stringState(SYNC_PENDING_KEY, "").first())
            payload.keys.forEach { p2.remove(it) }
            store.setStringState(SYNC_PENDING_KEY, syncPendingToJson(p2))
            val meta = parseSyncMeta(store.stringState(SYNC_META_KEY, "").first())
            val at = if (data.isNull("at")) "" else data.optString("at", "")
            val stamp = at.ifEmpty { isoNow() }
            payload.keys.forEach { k ->
                if (k in payload.deleted) meta.remove(k) else meta[k] = stamp
            }
            store.setStringState(SYNC_META_KEY, syncMetaToJson(meta))
            setStatus("Last synced ${timeNow()}")
            OpResult(true, applied = payload.keys.size)
        } catch (e: Exception) {
            setStatus("Sync failed: ${e.message}")
            OpResult(false, error = e.message)
        }
    }

    /** Source syncPullNow(). Applies server state into DataStore. */
    suspend fun pullNow(): OpResult = withContext(Dispatchers.IO) {
        val tok = token()
        val baseUrl = url()
        if (!syncConfigured(baseUrl, tok)) return@withContext OpResult(false, error = "not-configured")
        val pending = parseSyncPending(store.stringState(SYNC_PENDING_KEY, "").first())
        try {
            val conn = openConnection("GET", baseUrl, tok)
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val data = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            val serverState = if (data.isNull("state")) JSONObject() else data.optJSONObject("state") ?: JSONObject()
            val meta = parseSyncMeta(store.stringState(SYNC_META_KEY, "").first())
            val plan = computePull(meta, pending, serverState)
            for ((k, v) in plan.changes) {
                // Raw writes (source: _lsSetItem/_lsRemoveItem) so pulled
                // values are not re-marked dirty by the tracking hook.
                if (v == null) store.removeStringStateRaw(k) else store.setStringStateRaw(k, v)
            }
            store.setStringState(SYNC_META_KEY, syncMetaToJson(plan.newMeta))
            OpResult(true, applied = plan.applied)
        } catch (e: Exception) {
            setStatus("Sync failed: ${e.message}")
            OpResult(false, error = e.message)
        }
    }

    /**
     * Source syncNow() (Sync Now button): pull, then push; status/toast
     * wording mirrors the source's three outcomes. Returns the toast text.
     */
    suspend fun syncNow(): String {
        if (!configured()) return "Enter your sync server URL and token first."
        setStatus("Syncing…")
        val pull = pullNow()
        val push = pushNow()
        return if (pull.ok && push.ok) {
            setStatus("Last synced ${timeNow()}")
            when {
                pull.applied > 0 ->
                    "Sync complete — downloaded ${pull.applied} update(s)."
                push.applied > 0 ->
                    "Sync complete — uploaded ${push.applied} change(s)."
                else -> "Sync complete — already up to date."
            }
        } else {
            "Sync failed: ${pull.error ?: push.error ?: "unknown error"}"
        }
    }

    /** Source syncTick(): pull then push, guarded against overlap. */
    suspend fun tick() {
        if (tickRunning) return
        tickMutex.withLock {
            if (tickRunning) return
            tickRunning = true
            try {
                if (configured()) {
                    pullNow()
                    pushNow()
                }
            } finally {
                tickRunning = false
            }
        }
    }

    // ---- settings effects ----------------------------------------------

    /**
     * Source syncSaveSettings(): store the server URL + token, wipe
     * meta + pending (fresh bookkeeping), set status, apply schedule,
     * startup tick when both are present.
     */
    suspend fun saveSettings(newUrl: String, newToken: String) {
        store.setStringState(SYNC_URL_KEY, syncGetUrl(newUrl))
        store.setStringState(SYNC_TOKEN_KEY, newToken)
        store.setStringState(SYNC_META_KEY, "{}")
        store.setStringState(SYNC_PENDING_KEY, "{}")
        val isConfigured = syncConfigured(newUrl, newToken)
        setStatus(if (isConfigured) "Settings saved — syncing…" else "Not configured")
        applySchedule()
        if (isConfigured) tick()
    }

    /** Source syncSaveInterval(): persist + apply schedule. */
    suspend fun saveInterval(rawMs: String) {
        store.setStringState(SYNC_INTERVAL_KEY, rawMs)
        applySchedule()
        if (!configured()) setStatus("Not configured")
    }

    /**
     * Source syncApplySchedule() + its Android counterpart:
     * in-app loop restarts on the chosen interval (handled by the loop's
     * per-iteration read), and WorkManager periodic work is (re)enqueued
     * at max(interval, 15 min) — the platform floor — or cancelled when
     * sync is unconfigured / interval is Off.
     */
    suspend fun applySchedule() {
        val ms = syncGetInterval(store.stringState(SYNC_INTERVAL_KEY, "").first())
        val wm = WorkManager.getInstance(appContext)
        if (configured() && ms > 0) {
            setStatus("Auto-sync every ${syncIntervalLabel(ms)}")
            val req = PeriodicWorkRequestBuilder<SyncWorker>(
                maxOf(ms, 15L * 60L * 1000L), TimeUnit.MILLISECONDS
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
            wm.enqueueUniquePeriodicWork(
                SyncWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        } else {
            wm.cancelUniqueWork(SyncWorker.UNIQUE_NAME)
        }
    }

    /** The "page open" poller: ticks every chosen interval while alive. */
    private fun startInAppLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (true) {
                val ms = syncGetInterval(store.stringState(SYNC_INTERVAL_KEY, "").first())
                if (ms <= 0L) {
                    delay(5000)
                } else {
                    delay(ms)
                    if (configured()) tick()
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SyncManager? = null

        /** One shared manager per process (Settings + MainActivity + loop). */
        fun get(appContext: Context, store: AppStore): SyncManager =
            instance ?: synchronized(this) {
                instance ?: SyncManager(appContext.applicationContext, store)
                    .also { instance = it }
            }
    }

    private fun timeNow(): String =
        SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())
}

/** WorkManager entry point for the background sync tick. */
class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = AppStore(applicationContext)
        val mgr = SyncManager(applicationContext, store)
        return try {
            mgr.tick()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "toolbox-sync-periodic"
    }
}
