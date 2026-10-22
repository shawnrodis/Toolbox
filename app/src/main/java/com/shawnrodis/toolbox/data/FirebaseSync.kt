package com.shawnrodis.toolbox.data

import org.json.JSONObject

/**
 * Toolbox Firebase Sync — pure logic for the v1.1.0 Firebase provider.
 *
 * The Firebase provider is a SECOND transport for the exact same sync
 * protocol as SyncLogic.kt: same 26 SYNC_KEYS, same state-map shape
 * ({key: {"value": ..., "updated_at": ...}}), same tombstone deletions,
 * same merge engine (computePull / assemblePush in SyncLogic.kt). Only
 * the wire differs:
 *  - Backend: the user's own Firebase Realtime Database, over its REST
 *    API only — no Firebase SDK, no google-services.json, no new app
 *    dependencies. State lives at {databaseURL}/users/{uid}/state.json.
 *  - Auth: Firebase Authentication email/password via the Identity
 *    Toolkit REST endpoints. The long-lived refresh token + account
 *    email are device-local settings (toolbox_sync_fb_*), in the same
 *    spirit as toolbox_sync_url / toolbox_sync_token: NEVER synced and
 *    NOT part of Reset Everything's key lists. Signing out clears them.
 *  - The pasted Firebase web config's apiKey is a project identifier,
 *    not a secret (security comes from the account sign-in + the
 *    database rules in FIREBASE_SYNC.md).
 *
 * This file has no Android imports so parsing/mapping can be verified by
 * JVM unit tests.
 */
const val SYNC_PROVIDER_KEY = "toolbox_sync_provider"
const val SYNC_PROVIDER_OFF = "off"
const val SYNC_PROVIDER_SERVER = "server"
const val SYNC_PROVIDER_FIREBASE = "firebase"

const val FB_CONFIG_KEY = "toolbox_sync_fb_config"
const val FB_EMAIL_KEY = "toolbox_sync_fb_email"
const val FB_REFRESH_KEY = "toolbox_sync_fb_refresh"
const val FB_UID_KEY = "toolbox_sync_fb_uid"

/** Stored provider value -> effective provider (absent/unknown = server). */
fun syncProviderOf(stored: String?): String = when (stored) {
    SYNC_PROVIDER_FIREBASE -> SYNC_PROVIDER_FIREBASE
    SYNC_PROVIDER_OFF -> SYNC_PROVIDER_OFF
    else -> SYNC_PROVIDER_SERVER
}

/** The two config fields Toolbox actually needs, plus display extras. */
data class FirebaseConfig(
    val apiKey: String,
    val databaseURL: String,
    val projectId: String?,
    val authDomain: String?
)

sealed interface FbConfigParse {
    data class Ok(val config: FirebaseConfig) : FbConfigParse
    data class Invalid(val missing: List<String>) : FbConfigParse
}

/**
 * Lenient parse of the Firebase web config the user pastes into
 * Settings: accepts the raw JSON object from the console, the
 * `const firebaseConfig = { ... };` JavaScript snippet (unquoted keys,
 * single- or double-quoted values), or anything in between. Only apiKey
 * and databaseURL are required; projectId/authDomain are display extras.
 */
fun parseFirebaseConfig(raw: String?): FbConfigParse {
    if (raw.isNullOrBlank()) return FbConfigParse.Invalid(listOf("apiKey", "databaseURL"))
    fun field(name: String): String? {
        val re = Regex("""["']?""" + Regex.escape(name) + """["']?\s*:\s*["']([^"']*)["']""")
        return re.find(raw)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
    }
    val apiKey = field("apiKey")
    val databaseURL = field("databaseURL")?.trimEnd('/')
    val missing = mutableListOf<String>()
    if (apiKey == null) missing.add("apiKey")
    if (databaseURL.isNullOrEmpty()) missing.add("databaseURL")
    if (missing.isNotEmpty()) return FbConfigParse.Invalid(missing)
    return FbConfigParse.Ok(
        FirebaseConfig(apiKey!!, databaseURL!!, field("projectId"), field("authDomain"))
    )
}

/** REST URL of the signed-in user's state map in their Realtime Database. */
fun fbStateUrl(config: FirebaseConfig, uid: String): String =
    "${config.databaseURL}/users/$uid/state.json"

/** A Firebase Auth session (sign-in/sign-up or a refresh exchange). */
data class FbAuthSession(
    val idToken: String,
    val refreshToken: String,
    val uid: String,
    val expiresInSec: Long
)

/** Parse an accounts:signUp / accounts:signInWithPassword response. */
fun parseAuthResponse(body: String): FbAuthSession? = try {
    val o = JSONObject(body)
    val id = o.optString("idToken")
    val ref = o.optString("refreshToken")
    val uid = o.optString("localId")
    if (id.isEmpty() || ref.isEmpty() || uid.isEmpty()) null
    else FbAuthSession(id, ref, uid, o.optString("expiresIn").toLongOrNull() ?: 3600L)
} catch (e: Exception) {
    null
}

/** Parse a securetoken.googleapis.com refresh exchange response. */
fun parseRefreshResponse(body: String): FbAuthSession? = try {
    val o = JSONObject(body)
    val id = o.optString("id_token")
    val ref = o.optString("refresh_token")
    val uid = o.optString("user_id")
    if (id.isEmpty() || ref.isEmpty() || uid.isEmpty()) null
    else FbAuthSession(id, ref, uid, o.optString("expires_in").toLongOrNull() ?: 3600L)
} catch (e: Exception) {
    null
}

/**
 * Extract the Identity Toolkit error code from an error body
 * ({"error": {"message": "EMAIL_EXISTS"}}; some codes carry a
 * " : detail" suffix, which is stripped).
 */
fun parseIdentityErrorCode(body: String?): String? = try {
    if (body.isNullOrEmpty()) null
    else JSONObject(body).optJSONObject("error")?.optString("message")
        ?.substringBefore(" : ")?.trim()?.ifEmpty { null }
} catch (e: Exception) {
    null
}

/** Identity Toolkit error code -> friendly Settings status text. */
fun fbAuthErrorMessage(code: String?): String = when (code) {
    "EMAIL_EXISTS" ->
        "That email already has a sync account — try Sign in instead."
    "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" ->
        "Email or password didn't match — check them and try again."
    "INVALID_EMAIL" ->
        "That email address doesn't look valid."
    "WEAK_PASSWORD" ->
        "That password is too weak — Firebase asks for at least 6 characters."
    "TOO_MANY_ATTEMPTS_TRY_LATER" ->
        "Too many attempts — wait a little and try again."
    "USER_DISABLED" ->
        "This sync account has been disabled."
    "OPERATION_NOT_ALLOWED" ->
        "Email/password sign-in isn't enabled in that Firebase project yet (see FIREBASE_SYNC.md)."
    else ->
        "Firebase sign-in failed" + (code?.let { " ($it)" } ?: "") + "."
}

/**
 * The Realtime Database PATCH body for a push: each updated key maps to
 * {value, updated_at} — the same state-map shape the self-hosted server
 * stores — and each deleted key maps to JSON null (RTDB delete). The
 * stamp is generated client-side (the server protocol's POST returns
 * its own `at`; RTDB has no server stamp, and all Toolbox timestamps
 * are ISO strings compared lexicographically, so a client ISO stamp
 * merges identically).
 */
fun buildFirebasePushBody(
    updates: Map<String, String>,
    deleted: List<String>,
    stamp: String
): JSONObject {
    val body = JSONObject()
    updates.forEach { (k, v) ->
        body.put(k, JSONObject().put("value", v).put("updated_at", stamp))
    }
    deleted.forEach { body.put(it, JSONObject.NULL) }
    return body
}
