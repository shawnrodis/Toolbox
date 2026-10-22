package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.data.FB_CONFIG_KEY
import com.shawnrodis.toolbox.data.FB_EMAIL_KEY
import com.shawnrodis.toolbox.data.FB_REFRESH_KEY
import com.shawnrodis.toolbox.data.FB_UID_KEY
import com.shawnrodis.toolbox.data.FbConfigParse
import com.shawnrodis.toolbox.data.SYNC_KEYS
import com.shawnrodis.toolbox.data.SYNC_PROVIDER_FIREBASE
import com.shawnrodis.toolbox.data.SYNC_PROVIDER_KEY
import com.shawnrodis.toolbox.data.SYNC_PROVIDER_OFF
import com.shawnrodis.toolbox.data.SYNC_PROVIDER_SERVER
import com.shawnrodis.toolbox.data.buildFirebasePushBody
import com.shawnrodis.toolbox.data.computePull
import com.shawnrodis.toolbox.data.fbAuthErrorMessage
import com.shawnrodis.toolbox.data.fbStateUrl
import com.shawnrodis.toolbox.data.hapticsOn
import com.shawnrodis.toolbox.data.parseAuthResponse
import com.shawnrodis.toolbox.data.parseFirebaseConfig
import com.shawnrodis.toolbox.data.parseIdentityErrorCode
import com.shawnrodis.toolbox.data.parseRefreshResponse
import com.shawnrodis.toolbox.data.syncProviderOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.0: Firebase sync provider (Workstream A) + haptics setting
 * (Workstream C) — pure-logic coverage. The live REST flow itself is
 * NOT exercised here (no real Firebase project in the build env); what
 * is pinned is everything deterministic: config parsing (both paste
 * forms), the RTDB request/response mapping onto the existing merge
 * engine, auth response/error mapping, and the device-local key rules.
 */
class V110FirebaseTest {

    // ---- config parsing -------------------------------------------------

    private val consoleJson = """
        {
          "apiKey": "AIzaSy-PLACEHOLDER",
          "authDomain": "toolbox-sync-123.firebaseapp.com",
          "databaseURL": "https://toolbox-sync-123-default-rtdb.firebaseio.com",
          "projectId": "toolbox-sync-123",
          "storageBucket": "toolbox-sync-123.appspot.com",
          "messagingSenderId": "1234567890",
          "appId": "1:1234567890:web:abcdef"
        }
    """.trimIndent()

    private val consoleSnippet = """
        // Your web app's Firebase configuration
        const firebaseConfig = {
          apiKey: "AIzaSy-PLACEHOLDER",
          authDomain: "toolbox-sync-123.firebaseapp.com",
          databaseURL: "https://toolbox-sync-123-default-rtdb.firebaseio.com",
          projectId: "toolbox-sync-123",
          storageBucket: "toolbox-sync-123.appspot.com",
          messagingSenderId: "1234567890",
          appId: "1:1234567890:web:abcdef"
        };
    """.trimIndent()

    @Test
    fun config_jsonForm_parses() {
        val r = parseFirebaseConfig(consoleJson)
        assertTrue(r is FbConfigParse.Ok)
        val cfg = (r as FbConfigParse.Ok).config
        assertEquals("AIzaSy-PLACEHOLDER", cfg.apiKey)
        assertEquals(
            "https://toolbox-sync-123-default-rtdb.firebaseio.com", cfg.databaseURL
        )
        assertEquals("toolbox-sync-123", cfg.projectId)
        assertEquals("toolbox-sync-123.firebaseapp.com", cfg.authDomain)
    }

    @Test
    fun config_snippetForm_parses() {
        val r = parseFirebaseConfig(consoleSnippet)
        assertTrue(r is FbConfigParse.Ok)
        val cfg = (r as FbConfigParse.Ok).config
        assertEquals("AIzaSy-PLACEHOLDER", cfg.apiKey)
        assertEquals("toolbox-sync-123", cfg.projectId)
        assertEquals(
            "https://toolbox-sync-123-default-rtdb.firebaseio.com", cfg.databaseURL
        )
    }

    @Test
    fun config_singleQuotesAndTrailingSlash_normalised() {
        val raw = "{ apiKey: 'K', databaseURL: 'https://x.firebaseio.com/' }"
        val r = parseFirebaseConfig(raw)
        assertTrue(r is FbConfigParse.Ok)
        assertEquals("https://x.firebaseio.com", (r as FbConfigParse.Ok).config.databaseURL)
    }

    @Test
    fun config_missingFields_reportedByName() {
        val onlyKey = parseFirebaseConfig("""{"apiKey": "K"}""")
        assertTrue(onlyKey is FbConfigParse.Invalid)
        assertEquals(listOf("databaseURL"), (onlyKey as FbConfigParse.Invalid).missing)

        val onlyUrl = parseFirebaseConfig("""{"databaseURL": "https://x.firebaseio.com"}""")
        assertTrue(onlyUrl is FbConfigParse.Invalid)
        assertEquals(listOf("apiKey"), (onlyUrl as FbConfigParse.Invalid).missing)

        val blank = parseFirebaseConfig("   ")
        assertTrue(blank is FbConfigParse.Invalid)
        assertEquals(listOf("apiKey", "databaseURL"), (blank as FbConfigParse.Invalid).missing)

        val garbage = parseFirebaseConfig("not a config at all")
        assertTrue(garbage is FbConfigParse.Invalid)
        assertEquals(listOf("apiKey", "databaseURL"), (garbage as FbConfigParse.Invalid).missing)

        val emptyValues = parseFirebaseConfig("""{"apiKey": "", "databaseURL": ""}""")
        assertTrue(emptyValues is FbConfigParse.Invalid)
        assertEquals(listOf("apiKey", "databaseURL"), (emptyValues as FbConfigParse.Invalid).missing)
    }

    // ---- provider + device-local keys ------------------------------------

    @Test
    fun provider_defaultsToServer_andParsesKnownValues() {
        assertEquals(SYNC_PROVIDER_SERVER, syncProviderOf(null))
        assertEquals(SYNC_PROVIDER_SERVER, syncProviderOf(""))
        assertEquals(SYNC_PROVIDER_SERVER, syncProviderOf("server"))
        assertEquals(SYNC_PROVIDER_SERVER, syncProviderOf("nonsense"))
        assertEquals(SYNC_PROVIDER_OFF, syncProviderOf("off"))
        assertEquals(SYNC_PROVIDER_FIREBASE, syncProviderOf("firebase"))
    }

    @Test
    fun firebaseKeys_areDeviceLocal_neverSynced() {
        assertEquals(26, SYNC_KEYS.size)
        for (k in listOf(
            SYNC_PROVIDER_KEY, FB_CONFIG_KEY, FB_EMAIL_KEY, FB_REFRESH_KEY, FB_UID_KEY,
            "toolbox_haptics"
        )) {
            assertFalse("$k must never sync", SYNC_KEYS.contains(k))
        }
    }

    @Test
    fun stateUrl_targetsTheUsersOwnSubtree() {
        val cfg = (parseFirebaseConfig(consoleJson) as FbConfigParse.Ok).config
        assertEquals(
            "https://toolbox-sync-123-default-rtdb.firebaseio.com/users/UID42/state.json",
            fbStateUrl(cfg, "UID42")
        )
    }

    // ---- push body mapping ------------------------------------------------

    @Test
    fun pushBody_updatesCarryValueAndStamp_deletesAreNull() {
        val body = buildFirebasePushBody(
            updates = mapOf("toolbox_notes" to "hello", "toolbox_dark" to "dark"),
            deleted = listOf("toolbox_tip_state"),
            stamp = "2026-10-22T17:00:00.000Z"
        )
        val notes = body.getJSONObject("toolbox_notes")
        assertEquals("hello", notes.getString("value"))
        assertEquals("2026-10-22T17:00:00.000Z", notes.getString("updated_at"))
        assertTrue(body.isNull("toolbox_tip_state"))
        assertEquals(3, body.length())
    }

    // ---- pull mapping onto the existing merge engine ----------------------

    @Test
    fun pulledFirebaseState_flowsThroughComputePullUnchanged() {
        // RTDB GET returns the state map itself (no {"state": ...} wrap).
        val rtdbBody = """
            {
              "toolbox_dark": {"value": "light", "updated_at": "2026-10-22T17:00:00.000Z"},
              "toolbox_notes": {"value": "from desktop", "updated_at": "2026-10-22T16:00:00.000Z"}
            }
        """.trimIndent()
        val serverState = JSONObject(rtdbBody)
        val plan = computePull(
            meta = mapOf("toolbox_notes" to "2026-10-22T15:00:00.000Z"),
            pending = emptyMap(),
            serverState = serverState
        )
        assertEquals("light", plan.changes["toolbox_dark"])
        assertEquals("from desktop", plan.changes["toolbox_notes"])
        assertEquals(2, plan.applied)
        // Locally pending keys still win over the pulled state.
        val plan2 = computePull(
            meta = emptyMap(),
            pending = mapOf("toolbox_dark" to 1),
            serverState = serverState
        )
        assertFalse(plan2.changes.containsKey("toolbox_dark"))
        assertEquals("from desktop", plan2.changes["toolbox_notes"])
    }

    @Test
    fun emptyDatabase_pullsAsEmptyState() {
        // RTDB returns the literal "null" for a missing node; the engine
        // maps that to an empty JSONObject before computePull.
        val plan = computePull(emptyMap(), emptyMap(), JSONObject())
        assertEquals(0, plan.applied)
        assertTrue(plan.changes.isEmpty())
    }

    // ---- auth response / error mapping -------------------------------------

    @Test
    fun authResponses_parse_bothShapes() {
        val signIn = parseAuthResponse(
            """{"idToken":"ID","refreshToken":"REF","localId":"UID","expiresIn":"3600"}"""
        )
        assertNotNull(signIn)
        assertEquals("ID", signIn!!.idToken)
        assertEquals("UID", signIn.uid)
        assertEquals(3600L, signIn.expiresInSec)

        val refresh = parseRefreshResponse(
            """{"id_token":"ID2","refresh_token":"REF2","user_id":"UID","expires_in":"3600"}"""
        )
        assertNotNull(refresh)
        assertEquals("REF2", refresh!!.refreshToken)

        assertNull(parseAuthResponse("""{"idToken":"","refreshToken":"","localId":""}"""))
        assertNull(parseAuthResponse("not json"))
        assertNull(parseRefreshResponse("""{}"""))
    }

    @Test
    fun identityErrors_mapToFriendlyText() {
        assertEquals(
            "EMAIL_EXISTS",
            parseIdentityErrorCode("""{"error":{"code":400,"message":"EMAIL_EXISTS"}}""")
        )
        assertEquals(
            "WEAK_PASSWORD",
            parseIdentityErrorCode(
                """{"error":{"message":"WEAK_PASSWORD : Password should be at least 6 characters"}}"""
            )
        )
        assertNull(parseIdentityErrorCode(null))
        assertNull(parseIdentityErrorCode(""))
        assertNull(parseIdentityErrorCode("""{"nope":1}"""))

        assertTrue(fbAuthErrorMessage("EMAIL_EXISTS").contains("already has a sync account"))
        assertTrue(fbAuthErrorMessage("EMAIL_NOT_FOUND").contains("didn't match"))
        assertTrue(fbAuthErrorMessage("INVALID_PASSWORD").contains("didn't match"))
        assertTrue(fbAuthErrorMessage("INVALID_LOGIN_CREDENTIALS").contains("didn't match"))
        assertTrue(fbAuthErrorMessage("INVALID_EMAIL").contains("doesn't look valid"))
        assertTrue(fbAuthErrorMessage("WEAK_PASSWORD").contains("at least 6 characters"))
        assertTrue(fbAuthErrorMessage("TOO_MANY_ATTEMPTS_TRY_LATER").contains("Too many attempts"))
        assertTrue(fbAuthErrorMessage("USER_DISABLED").contains("disabled"))
        assertTrue(fbAuthErrorMessage("OPERATION_NOT_ALLOWED").contains("isn't enabled"))
        assertTrue(fbAuthErrorMessage("SOMETHING_NEW").contains("SOMETHING_NEW"))
        assertTrue(fbAuthErrorMessage(null).startsWith("Firebase sign-in failed"))
    }

    // ---- haptics setting ----------------------------------------------------

    @Test
    fun haptics_defaultOn_unlessExplicitlyOff() {
        assertTrue(hapticsOn(null))
        assertTrue(hapticsOn(""))
        assertTrue(hapticsOn("on"))
        assertFalse(hapticsOn("off"))
    }
}
