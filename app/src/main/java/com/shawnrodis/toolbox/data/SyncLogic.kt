package com.shawnrodis.toolbox.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Toolbox Self-Hosted Sync — pure logic, ported 1:1 from the source's
 * sync* functions in the HTML app. This file has no Android imports so the
 * payload/pull maths can be verified by JVM unit tests.
 *
 * Source facts replicated exactly:
 *  - SYNC_KEYS: the 26 localStorage keys mirrored to the server, in the
 *    source's order. NOTE: 'toolbox_shop_items' was NOT in the HTML
 *    source's list (it is only in its Reset list); the owner directed on
 *    2026-10-22 that shopping items MUST sync, so it is appended here
 *    (v1.0.4) — a deliberate deviation from the HTML original.
 *  - Per-device settings (URL + token) are NEVER synced.
 *  - The server URL is a user-entered per-device setting (stored under
 *    SYNC_URL_KEY, default empty — there is no built-in endpoint).
 *    syncGetUrl() normalises the stored value; syncEndpoint(url) =
 *    url + "/state". Sync counts as configured only when BOTH a URL and
 *    a non-empty token are present.
 *  - Auth headers: Content-Type: application/json + X-Toolbox-Token.
 *  - Push body: {token, updates:{key:value}, deleted:[key]}.
 *  - Pull body: {state: {key: {value, updated_at}}}; a key is applied when
 *    we have no meta for it or the server's updated_at is newer; a key the
 *    server no longer has (but we have meta for) is deleted locally; keys
 *    with a pending local change are skipped.
 *  - SECURITY: the token is a secret. It is only ever placed in the request
 *    header/body (as the source does) and is never logged or included in
 *    any diagnostic string built here.
 */
val SYNC_KEYS: List<String> = listOf(
    "toolbox_accent", "toolbox_comparison_options", "toolbox_dark",
    "toolbox_desktop", "toolbox_desktop_icon_opts", "toolbox_desktop_icon_pos",
    "toolbox_pto_logs", "toolbox_pto_settings", "toolbox_tally_items",
    "toolbox_taskbar_pins", "toolbox_tools_config", "toolbox_tracked_times",
    "toolbox_ui_scale", "pdf_suite_preview_scale", "skytrack_mobile_state",
    "toolbox_currency_state", "toolbox_time_state", "toolbox_sbm_state",
    "toolbox_split_state", "toolbox_tip_state", "toolbox_aviationstack_key",
    "toolbox_gemini_key_1", "toolbox_gemini_key_2", "toolbox_custom_accent",
    "toolbox_notes", "toolbox_shop_items"
)

const val SYNC_URL_KEY = "toolbox_sync_url"
const val SYNC_TOKEN_KEY = "toolbox_sync_token"
const val SYNC_META_KEY = "toolbox_sync_meta"       // key -> server updated_at
const val SYNC_PENDING_KEY = "toolbox_sync_pending" // key -> 1 (unsynced local change)
const val SYNC_INTERVAL_KEY = "toolbox_sync_interval"
const val SYNC_PUSH_DEBOUNCE_MS = 2500L

/**
 * The user-entered sync server URL, normalised: whitespace trimmed and a
 * trailing slash dropped so the endpoint join below is exact. Empty when
 * the user has not entered a server yet (sync unconfigured).
 */
fun syncGetUrl(stored: String?): String =
    stored?.trim()?.trimEnd('/') ?: ""

/** Sync endpoint for a stored URL: base URL + "/state". */
fun syncEndpoint(storedUrl: String?): String = syncGetUrl(storedUrl) + "/state"

/** Sync is configured only with BOTH a server URL and a non-empty token. */
fun syncConfigured(url: String?, token: String?): Boolean =
    syncGetUrl(url).isNotEmpty() && !token.isNullOrEmpty()

/** Source syncGetInterval(): parse int, NaN/negative -> 0 (Off). */
fun syncGetInterval(raw: String?): Long {
    val v = raw?.toLongOrNull() ?: 0L
    return if (v < 0) 0L else v
}

/** Source syncIntervalLabel(ms), verbatim. */
fun syncIntervalLabel(ms: Long): String {
    if (ms < 1000) return "${ms}ms"
    if (ms < 60000) return "${ms / 1000} second" + (if (ms == 1000L) "" else "s")
    return "${ms / 60000} minute" + (if (ms == 60000L) "" else "s")
}

/** The Auto-sync options from the source Settings <select>, in order. */
val SYNC_INTERVAL_OPTIONS: List<Pair<String, String>> = listOf(
    "0" to "Off",
    "1000" to "Every second",
    "5000" to "Every 5 seconds",
    "15000" to "Every 15 seconds",
    "30000" to "Every 30 seconds",
    "60000" to "Every minute",
    "300000" to "Every 5 minutes"
)

fun parseSyncMeta(json: String?): MutableMap<String, String> {
    val out = mutableMapOf<String, String>()
    if (json.isNullOrEmpty()) return out
    try {
        val o = JSONObject(json)
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = o.optString(k)
        }
    } catch (e: Exception) { /* source: catch -> {} */ }
    return out
}

fun parseSyncPending(json: String?): MutableMap<String, Int> {
    val out = mutableMapOf<String, Int>()
    if (json.isNullOrEmpty()) return out
    try {
        val o = JSONObject(json)
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = o.optInt(k, 1)
        }
    } catch (e: Exception) { /* source syncReadPending catch -> {} */ }
    return out
}

fun syncMetaToJson(meta: Map<String, String>): String {
    val o = JSONObject()
    meta.forEach { (k, v) -> o.put(k, v) }
    return o.toString()
}

fun syncPendingToJson(pending: Map<String, Int>): String {
    val o = JSONObject()
    pending.forEach { (k, v) -> o.put(k, v) }
    return o.toString()
}

/** Result of assembling a push, mirroring syncPushNow's `updates`/`deleted`. */
data class PushPayload(
    val keys: List<String>,
    val updates: Map<String, String>,
    val deleted: List<String>,
    val body: JSONObject
)

/**
 * Source syncPushNow() payload assembly: pending keys filtered to
 * SYNC_KEYS; a key whose current value is null goes to `deleted`, anything
 * else to `updates`. The token rides in the body exactly as the source
 * sends it (and in the X-Toolbox-Token header at call time).
 */
fun assemblePush(
    token: String,
    pending: Map<String, Int>,
    values: Map<String, String?>
): PushPayload {
    val keys = pending.keys.filter { it in SYNC_KEYS }
    val updates = linkedMapOf<String, String>()
    val deleted = mutableListOf<String>()
    for (k in keys) {
        val v = values[k]
        if (v == null) deleted.add(k) else updates[k] = v
    }
    val body = JSONObject()
        .put("token", token)
        .put("updates", JSONObject().apply { updates.forEach { (k, v) -> put(k, v) } })
        .put("deleted", JSONArray().apply { deleted.forEach { put(it) } })
    return PushPayload(keys, updates, deleted, body)
}

/** One planned pull change: newValue null means "delete locally". */
data class PullPlan(
    val changes: Map<String, String?>,
    val newMeta: Map<String, String>,
    val applied: Int
)

/**
 * v1.1.1 — first-sync seeding. The pending map only ever records changes
 * made while the dirty-tracking hook was running, so values that already
 * existed locally BEFORE sync bookkeeping began (keys typed before sync
 * was enabled, before sign-in, or before a settings save wiped meta +
 * pending for a fresh backend) would otherwise never be pushed: the
 * first pull skips nothing (they are not pending) and either overwrites
 * them with backend state or, on an empty backend, leaves them stranded
 * on the device forever.
 *
 * Seeding treats every never-synced local value as what it is — an
 * unsynced local change: any SYNC_KEYS entry that (a) has a local value,
 * (b) has NO meta entry (this device has never confirmed it with the
 * backend), and (c) is not already pending joins the pending map. Keys
 * WITH a meta entry are never seeded (their divergence, if any, is a
 * tracked change's job, and a locally-absent key with meta is a pull
 * deletion waiting to happen — not a push). Runs at the top of every
 * push/pull, so it is a no-op in steady state.
 */
fun seedPending(
    meta: Map<String, String>,
    pending: Map<String, Int>,
    values: Map<String, String?>
): Map<String, Int> {
    if (SYNC_KEYS.all { it in meta || it in pending || values[it] == null }) return pending
    val out = pending.toMutableMap()
    for (k in SYNC_KEYS) {
        if (k !in out && k !in meta && values[k] != null) out[k] = 1
    }
    return out
}

/**
 * Source syncPullNow() application maths (minus the fetch):
 * for each SYNC_KEYS entry — skip if locally pending; apply server value
 * when entry exists with a string value and (no meta || newer); delete
 * locally when the server has no entry but we have meta for the key.
 */
fun computePull(
    meta: Map<String, String>,
    pending: Map<String, Int>,
    serverState: JSONObject?
): PullPlan {
    val newMeta = meta.toMutableMap()
    val changes = linkedMapOf<String, String?>()
    var applied = 0
    for (key in SYNC_KEYS) {
        if (pending.containsKey(key)) continue
        val entry = serverState?.optJSONObject(key)
        val value = entry?.let { if (it.isNull("value")) null else it.optString("value", "") }
        val hasStringValue = entry != null && !entry.isNull("value") && entry.opt("value") is String
        if (hasStringValue) {
            val updatedAt = entry.optString("updated_at", "")
            if (!newMeta.containsKey(key) || updatedAt > newMeta.getValue(key)) {
                changes[key] = value
                newMeta[key] = updatedAt
                applied++
            }
        } else if (newMeta.containsKey(key)) {
            changes[key] = null
            newMeta.remove(key)
            applied++
        }
    }
    return PullPlan(changes, newMeta, applied)
}
