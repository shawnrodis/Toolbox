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

    // ---- v1.1.0: sync provider (Off / Own server / Firebase) -----------

    private suspend fun provider(): String =
        syncProviderOf(store.stringState(SYNC_PROVIDER_KEY, "").first())

    private suspend fun fbConfig(): FirebaseConfig? =
        (parseFirebaseConfig(store.stringState(FB_CONFIG_KEY, "").first())
            as? FbConfigParse.Ok)?.config

    private suspend fun fbSignedIn(): Boolean =
        fbConfig() != null && store.stringState(FB_REFRESH_KEY, "").first().isNotEmpty()

    /** Provider-aware: server needs URL+token; Firebase needs a valid
     *  pasted config + a signed-in account (stored refresh token). */
    private suspend fun configured(): Boolean = when (provider()) {
        SYNC_PROVIDER_OFF -> false
        SYNC_PROVIDER_FIREBASE -> fbSignedIn()
        else -> syncConfigured(url(), token())
    }

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
                setStatus(
                    if (provider() == SYNC_PROVIDER_OFF) "Sync is off"
                    else "Not configured"
                )
            }
        }
        startInAppLoop()
    }

    // ---- dirty tracking (source: localStorage wrapper -> syncMarkDirty) --

    /**
     * Source syncMarkDirty(): record pending + schedule a debounced push.
     *
     * v1.1.1: the pending mark is recorded whether or not sync is
     * currently configured. Until now a change made while unconfigured
     * (keys typed before enabling sync / signing in, or a deletion made
     * while signed out) was silently dropped here, and — if the key had
     * a meta entry — the device then believed itself in sync while the
     * backend kept the old value forever. Pending simply means "not yet
     * confirmed by the backend"; the debounced push below still only
     * fires when configured.
     */
    fun markDirty(key: String) {
        scope.launch {
            val pending = parseSyncPending(store.stringState(SYNC_PENDING_KEY, "").first())
            pending[key] = 1
            store.setStringState(SYNC_PENDING_KEY, syncPendingToJson(pending))
            schedulePush()
        }
    }

    /**
     * v1.1.1: the pending map for a push/pull, topped up by
     * [seedPending] so values that predate sync bookkeeping (entered
     * before sync was enabled / signed in, or stranded by a settings
     * save wiping meta + pending) are treated as unsynced local changes.
     * Persisted when seeding added anything.
     */
    private suspend fun seededPending(): MutableMap<String, Int> {
        val pending = parseSyncPending(store.stringState(SYNC_PENDING_KEY, "").first())
        val meta = parseSyncMeta(store.stringState(SYNC_META_KEY, "").first())
        val seeded = seedPending(meta, pending, store.snapshot(SYNC_KEYS))
        if (seeded != pending) {
            store.setStringState(SYNC_PENDING_KEY, syncPendingToJson(seeded))
        }
        return seeded.toMutableMap()
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

    /** Push via the active provider's transport (merge maths shared). */
    suspend fun pushNow(): OpResult =
        if (provider() == SYNC_PROVIDER_FIREBASE) fbPushNow() else serverPushNow()

    /** Pull via the active provider's transport (merge maths shared). */
    suspend fun pullNow(): OpResult =
        if (provider() == SYNC_PROVIDER_FIREBASE) fbPullNow() else serverPullNow()

    /** Source syncPushNow(). Never throws; errors land in the status line. */
    private suspend fun serverPushNow(): OpResult = withContext(Dispatchers.IO) {
        val tok = token()
        val baseUrl = url()
        if (!syncConfigured(baseUrl, tok)) return@withContext OpResult(false, error = "not-configured")
        val pending = seededPending()
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
    private suspend fun serverPullNow(): OpResult = withContext(Dispatchers.IO) {
        val tok = token()
        val baseUrl = url()
        if (!syncConfigured(baseUrl, tok)) return@withContext OpResult(false, error = "not-configured")
        val pending = seededPending()
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
        if (!configured()) return when (provider()) {
            SYNC_PROVIDER_OFF -> "Sync is off — choose a provider in Settings."
            SYNC_PROVIDER_FIREBASE ->
                "Sign in to Firebase first (Settings → Sync Across Devices)."
            else -> "Enter your sync server URL and token first."
        }
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

    // ---- v1.1.0: Firebase transport (Realtime Database REST) ----------
    //
    // Same protocol, different wire (see FirebaseSync.kt): state lives
    // at {databaseURL}/users/{uid}/state.json, guarded by the account's
    // ID token. The idToken (~1h) is cached in memory and refreshed
    // silently from the stored refresh token; a 401/403 from the
    // database triggers ONE forced refresh + ONE retry, then the user
    // is told to sign in again. The refresh token itself is a secret:
    // it is only ever sent to securetoken.googleapis.com, never logged,
    // never placed in status strings or URLs.

    private class FbAuthExpiredException : IOException("firebase-auth-expired")

    private class FbHttpException(val code: Int) : IOException("HTTP $code")

    @Volatile private var fbIdTokenCache: String? = null
    @Volatile private var fbIdTokenExpiryMs: Long = 0L
    @Volatile private var fbUidCache: String? = null

    private suspend fun fbUid(): String? =
        fbUidCache ?: store.stringState(FB_UID_KEY, "").first().ifEmpty { null }

    /** Silent refresh-token exchange; null = no usable session. */
    private suspend fun fbIdToken(cfg: FirebaseConfig, force: Boolean): String? {
        val now = System.currentTimeMillis()
        if (!force) {
            val cached = fbIdTokenCache
            if (cached != null && now < fbIdTokenExpiryMs - 30_000L) return cached
        }
        val refresh = store.stringState(FB_REFRESH_KEY, "").first()
        if (refresh.isEmpty()) return null
        return try {
            val form = "grant_type=refresh_token&refresh_token=" +
                java.net.URLEncoder.encode(refresh, "UTF-8")
            val conn = URL("https://securetoken.googleapis.com/v1/token?key=${cfg.apiKey}")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val session = parseRefreshResponse(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            if (session == null) return null
            fbIdTokenCache = session.idToken
            fbIdTokenExpiryMs = now + session.expiresInSec * 1000L
            fbUidCache = session.uid
            // Refresh tokens rotate: persist the new one (raw writes —
            // device-local keys, never dirty-tracked, never synced).
            store.setStringStateRaw(FB_REFRESH_KEY, session.refreshToken)
            store.setStringStateRaw(FB_UID_KEY, session.uid)
            session.idToken
        } catch (e: Exception) {
            null
        }
    }

    /** One raw RTDB REST call; throws FbHttpException on non-2xx. */
    private fun fbDataRequestOnce(
        cfg: FirebaseConfig, uid: String, method: String, body: String?, idToken: String
    ): String {
        val conn = URL(fbStateUrl(cfg, uid) + "?auth=" + idToken)
            .openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw FbHttpException(code)
        }
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return text
    }

    /** RTDB REST call with the agreed 401 policy: refresh once, retry once. */
    private suspend fun fbDataRequest(
        cfg: FirebaseConfig, method: String, body: String?
    ): String {
        val uid = fbUid() ?: throw FbAuthExpiredException()
        var idToken = fbIdToken(cfg, force = false) ?: throw FbAuthExpiredException()
        try {
            return fbDataRequestOnce(cfg, uid, method, body, idToken)
        } catch (e: FbHttpException) {
            if (e.code != 401 && e.code != 403) throw e
        }
        idToken = fbIdToken(cfg, force = true) ?: throw FbAuthExpiredException()
        try {
            return fbDataRequestOnce(cfg, uid, method, body, idToken)
        } catch (e: FbHttpException) {
            if (e.code == 401 || e.code == 403) throw FbAuthExpiredException()
            throw e
        }
    }

    /** Firebase counterpart of the push: PATCH the state map. */
    private suspend fun fbPushNow(): OpResult = withContext(Dispatchers.IO) {
        val cfg = fbConfig() ?: return@withContext OpResult(false, error = "not-configured")
        if (store.stringState(FB_REFRESH_KEY, "").first().isEmpty()) {
            return@withContext OpResult(false, error = "not-configured")
        }
        val pending = seededPending()
        val payload = assemblePush("", pending, store.snapshot(SYNC_KEYS))
        if (payload.keys.isEmpty()) return@withContext OpResult(true, applied = 0)
        try {
            val stamp = isoNow()
            val body = buildFirebasePushBody(payload.updates, payload.deleted, stamp)
            fbDataRequest(cfg, "PATCH", body.toString())
            // Same bookkeeping as the server POST: clear pushed keys,
            // stamp meta with the write time, drop deleted from meta.
            val p2 = parseSyncPending(store.stringState(SYNC_PENDING_KEY, "").first())
            payload.keys.forEach { p2.remove(it) }
            store.setStringState(SYNC_PENDING_KEY, syncPendingToJson(p2))
            val meta = parseSyncMeta(store.stringState(SYNC_META_KEY, "").first())
            payload.keys.forEach { k ->
                if (k in payload.deleted) meta.remove(k) else meta[k] = stamp
            }
            store.setStringState(SYNC_META_KEY, syncMetaToJson(meta))
            setStatus("Last synced ${timeNow()}")
            OpResult(true, applied = payload.keys.size)
        } catch (e: FbAuthExpiredException) {
            setStatus("Firebase session expired — sign in again in Settings.")
            OpResult(false, error = "auth")
        } catch (e: Exception) {
            setStatus("Sync failed: ${e.message}")
            OpResult(false, error = e.message)
        }
    }

    /** Firebase counterpart of the pull: GET the state map, same merge. */
    private suspend fun fbPullNow(): OpResult = withContext(Dispatchers.IO) {
        val cfg = fbConfig() ?: return@withContext OpResult(false, error = "not-configured")
        if (store.stringState(FB_REFRESH_KEY, "").first().isEmpty()) {
            return@withContext OpResult(false, error = "not-configured")
        }
        val pending = seededPending()
        try {
            val text = fbDataRequest(cfg, "GET", null)
            val serverState =
                if (text.isBlank() || text == "null") JSONObject()
                else JSONObject(text)
            val meta = parseSyncMeta(store.stringState(SYNC_META_KEY, "").first())
            val plan = computePull(meta, pending, serverState)
            for ((k, v) in plan.changes) {
                if (v == null) store.removeStringStateRaw(k) else store.setStringStateRaw(k, v)
            }
            store.setStringState(SYNC_META_KEY, syncMetaToJson(plan.newMeta))
            OpResult(true, applied = plan.applied)
        } catch (e: FbAuthExpiredException) {
            setStatus("Firebase session expired — sign in again in Settings.")
            OpResult(false, error = "auth")
        } catch (e: Exception) {
            setStatus("Sync failed: ${e.message}")
            OpResult(false, error = e.message)
        }
    }

    // ---- v1.1.0: Firebase Settings actions ------------------------------

    /**
     * Switch providers. Like changing the server in the source, a switch
     * wipes meta + pending (fresh bookkeeping — timestamps from one
     * backend must not suppress pulls from the other).
     */
    suspend fun setProvider(newProvider: String): String {
        store.setStringState(SYNC_PROVIDER_KEY, newProvider)
        store.setStringState(SYNC_META_KEY, "{}")
        store.setStringState(SYNC_PENDING_KEY, "{}")
        val msg = when (newProvider) {
            SYNC_PROVIDER_OFF -> "Sync is off"
            SYNC_PROVIDER_FIREBASE ->
                if (fbSignedIn()) "Firebase sync on — syncing…"
                else "Firebase chosen — paste your project config and sign in below"
            else ->
                if (syncConfigured(url(), token())) "Own-server sync on — syncing…"
                else "Not configured — enter your sync server address and token"
        }
        setStatus(msg)
        applySchedule()
        if (configured()) tick()
        return msg
    }

    /** Save the pasted Firebase config (validated by parseFirebaseConfig). */
    suspend fun firebaseSaveConfig(raw: String): String {
        store.setStringState(FB_CONFIG_KEY, raw.trim())
        // A different project = different backend: fresh bookkeeping.
        store.setStringState(SYNC_META_KEY, "{}")
        store.setStringState(SYNC_PENDING_KEY, "{}")
        return when (val p = parseFirebaseConfig(raw)) {
            is FbConfigParse.Ok -> {
                val where = p.config.projectId ?: "your project"
                setStatus("Firebase project saved ($where) — sign in below")
                "Firebase config saved — now create your sync account or sign in."
            }
            is FbConfigParse.Invalid -> {
                val msg = "That config is missing: ${p.missing.joinToString(", ")}"
                setStatus(msg)
                "$msg."
            }
        }
    }

    suspend fun firebaseSignUp(email: String, password: String): String =
        fbAuthCall("signUp", email.trim(), password)

    suspend fun firebaseSignIn(email: String, password: String): String =
        fbAuthCall("signInWithPassword", email.trim(), password)

    /** accounts:signUp / accounts:signInWithPassword via Identity Toolkit. */
    private suspend fun fbAuthCall(kind: String, email: String, password: String): String =
        withContext(Dispatchers.IO) {
            val cfg = fbConfig()
                ?: return@withContext "Save your Firebase config first."
            if (email.isEmpty() || password.isEmpty()) {
                return@withContext "Enter your email and password first."
            }
            try {
                val conn = URL(
                    "https://identitytoolkit.googleapis.com/v1/accounts:$kind?key=${cfg.apiKey}"
                ).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Content-Type", "application/json")
                val reqBody = JSONObject()
                    .put("email", email)
                    .put("password", password)
                    .put("returnSecureToken", true)
                conn.outputStream.use { it.write(reqBody.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val errText = try {
                        conn.errorStream?.bufferedReader()?.readText() ?: ""
                    } catch (e: Exception) { "" }
                    conn.disconnect()
                    val msg = fbAuthErrorMessage(parseIdentityErrorCode(errText))
                    setStatus(msg)
                    return@withContext msg
                }
                val session = parseAuthResponse(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                if (session == null) {
                    val msg = "Firebase returned an unexpected response — try again."
                    setStatus(msg)
                    return@withContext msg
                }
                fbIdTokenCache = session.idToken
                fbIdTokenExpiryMs = System.currentTimeMillis() + session.expiresInSec * 1000L
                fbUidCache = session.uid
                // Persist the session (raw: device-local, never synced).
                store.setStringStateRaw(FB_REFRESH_KEY, session.refreshToken)
                store.setStringStateRaw(FB_UID_KEY, session.uid)
                store.setStringStateRaw(FB_EMAIL_KEY, email)
                setStatus("Signed in as $email")
                applySchedule()
                tick()
                "Signed in as $email."
            } catch (e: Exception) {
                val msg = "Firebase sign-in failed: ${e.message}"
                setStatus(msg)
                msg
            }
        }

    /** Sign out: clears the stored session (config is kept for next time). */
    suspend fun firebaseSignOut() {
        store.removeStringStateRaw(FB_REFRESH_KEY)
        store.removeStringStateRaw(FB_UID_KEY)
        store.removeStringStateRaw(FB_EMAIL_KEY)
        fbIdTokenCache = null
        fbIdTokenExpiryMs = 0L
        fbUidCache = null
        setStatus("Signed out of Firebase")
        applySchedule()
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
