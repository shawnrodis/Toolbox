package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.data.SYNC_KEYS
import com.shawnrodis.toolbox.data.assemblePush
import com.shawnrodis.toolbox.data.buildFirebasePushBody
import com.shawnrodis.toolbox.data.computePull
import com.shawnrodis.toolbox.data.normalizeApiKeyValue
import com.shawnrodis.toolbox.data.seedPending
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.1 — API keys must reach every synced device. These tests pin the
 * pure-logic links of the round trip that SyncManager runs for BOTH
 * providers (the transports share assemblePush / computePull; Firebase
 * additionally shares buildFirebasePushBody):
 *
 *   Settings write -> pending -> push payload -> backend state
 *   -> receiving device's computePull -> applied value / deletion
 *
 * plus the two v1.1.1 fixes: first-sync seeding (pre-existing local
 * values push on the first sync after enabling) and the source's
 * API-key save semantics (trimmed; blank removes the key, so a clear
 * propagates as a deletion, exactly as the web edition's
 * saveAviationstackKey / saveGeminiKeys behave).
 */
class V111ApiKeySyncTest {

    private val av = "toolbox_aviationstack_key"
    private val g1 = "toolbox_gemini_key_1"
    private val g2 = "toolbox_gemini_key_2"
    private val apiKeys = listOf(av, g1, g2)

    @Test
    fun syncKeys_includeAllThreeApiKeys() {
        apiKeys.forEach { assertTrue("SYNC_KEYS must contain $it", SYNC_KEYS.contains(it)) }
    }

    // ---- write -> pending -> push payload -------------------------------

    @Test
    fun enteredApiKeys_appearInPushPayload() {
        // A key write marks the key pending (Store hook); the push then
        // carries the CURRENT value for every pending key.
        val pending = mapOf(av to 1, g1 to 1, g2 to 1)
        val values = mapOf<String, String?>(
            av to "AV-KEY-1", g1 to "GEM-KEY-1", g2 to "GEM-KEY-2"
        )
        val payload = assemblePush("TOK", pending, values)
        assertEquals("AV-KEY-1", payload.updates[av])
        assertEquals("GEM-KEY-1", payload.updates[g1])
        assertEquals("GEM-KEY-2", payload.updates[g2])
        assertTrue(payload.deleted.isEmpty())
        // And on the wire (server POST body).
        val wire = payload.body.getJSONObject("updates")
        assertEquals("AV-KEY-1", wire.getString(av))
        assertEquals("GEM-KEY-1", wire.getString(g1))
        assertEquals("GEM-KEY-2", wire.getString(g2))
    }

    @Test
    fun enteredApiKeys_appearInFirebasePushBody() {
        val pending = mapOf(av to 1, g1 to 1)
        val values = mapOf<String, String?>(av to "AV-KEY-1", g1 to "GEM-KEY-1")
        val payload = assemblePush("", pending, values)
        val body = buildFirebasePushBody(payload.updates, payload.deleted, "2026-10-22T00:00:00.000Z")
        assertEquals("AV-KEY-1", body.getJSONObject(av).getString("value"))
        assertEquals("2026-10-22T00:00:00.000Z", body.getJSONObject(av).getString("updated_at"))
        assertEquals("GEM-KEY-1", body.getJSONObject(g1).getString("value"))
    }

    // ---- pull applies on the receiving device ---------------------------

    @Test
    fun pulledState_appliesApiKeysOnReceivingDevice() {
        val server = JSONObject()
            .put(av, JSONObject().put("value", "AV-KEY-1").put("updated_at", "2026-10-22T10:00:00.000Z"))
            .put(g1, JSONObject().put("value", "GEM-KEY-1").put("updated_at", "2026-10-22T10:00:00.000Z"))
            .put(g2, JSONObject().put("value", "GEM-KEY-2").put("updated_at", "2026-10-22T10:00:00.000Z"))
        // A fresh device: no meta, nothing pending.
        val plan = computePull(emptyMap(), emptyMap(), server)
        assertEquals("AV-KEY-1", plan.changes[av])
        assertEquals("GEM-KEY-1", plan.changes[g1])
        assertEquals("GEM-KEY-2", plan.changes[g2])
        assertEquals("2026-10-22T10:00:00.000Z", plan.newMeta[g1])
    }

    // ---- clear propagates as a deletion ---------------------------------

    @Test
    fun normalizeApiKeyValue_trimsAndBlankMeansRemoval() {
        assertEquals("KEY", normalizeApiKeyValue("  KEY  "))
        assertEquals("KEY", normalizeApiKeyValue("KEY"))
        assertNull(normalizeApiKeyValue(""))
        assertNull(normalizeApiKeyValue("   "))
    }

    @Test
    fun clearedApiKey_pushesAsDeletion_andPullDeletesOnOtherDevice() {
        // Device A cleared the key: the stored value is gone (removal),
        // the key is pending -> push lists it under deleted, never updates.
        val payload = assemblePush("TOK", mapOf(g1 to 1), mapOf<String, String?>(g1 to null))
        assertTrue(payload.updates.isEmpty())
        assertEquals(listOf(g1), payload.deleted)
        assertEquals(g1, payload.body.getJSONArray("deleted").getString(0))
        // Firebase carries the same deletion as a JSON null (RTDB delete).
        val fbBody = buildFirebasePushBody(payload.updates, payload.deleted, "2026-10-22T11:00:00.000Z")
        assertTrue(fbBody.isNull(g1))

        // Device B had the key synced (meta present); the backend state
        // no longer contains it -> the pull deletes B's local copy.
        val meta = mapOf(g1 to "2026-10-22T10:00:00.000Z")
        val plan = computePull(meta, emptyMap(), JSONObject())
        assertTrue(plan.changes.containsKey(g1))
        assertNull(plan.changes[g1])
        assertFalse(plan.newMeta.containsKey(g1))
    }

    // ---- first sync: pre-existing values push ---------------------------

    @Test
    fun seedPending_firstSyncSeedsPreExistingApiKeys() {
        // Keys typed BEFORE sync was enabled: present locally, no meta,
        // nothing pending. All three must seed so the first push carries
        // them (Shawn's case: enable Firebase, keys must reach the other
        // devices).
        val values = mapOf<String, String?>(
            av to "AV-KEY-1", g1 to "GEM-KEY-1", g2 to "GEM-KEY-2",
            "toolbox_notes" to "[]"
        )
        val seeded = seedPending(emptyMap(), emptyMap(), values)
        apiKeys.forEach { assertEquals("must seed $it", 1, seeded[it]) }
        assertEquals(1, seeded["toolbox_notes"])
        // And the seeded pending produces a push payload with the keys.
        val payload = assemblePush("TOK", seeded, values)
        assertEquals("AV-KEY-1", payload.updates[av])
        assertEquals("GEM-KEY-2", payload.updates[g2])
    }

    @Test
    fun seedPending_neverSeedsSyncedAbsentOrPendingKeys() {
        val meta = mapOf(g1 to "2026-10-22T10:00:00.000Z", av to "2026-10-22T10:00:00.000Z")
        val pending = mapOf(g2 to 1)
        val values = mapOf<String, String?>(
            av to "AV-KEY-1",  // has meta -> already the backend's business
            g1 to null,        // has meta, absent locally -> a pull deletion, not a push
            g2 to "GEM-KEY-2"  // already pending -> untouched
        )
        val seeded = seedPending(meta, pending, values)
        assertFalse(seeded.containsKey(av))
        assertFalse(seeded.containsKey(g1))
        assertEquals(1, seeded[g2])
        assertEquals(pending, seeded)
    }

    @Test
    fun seedPending_noOpWhenEverythingSynced() {
        val meta = SYNC_KEYS.associateWith { "2026-10-22T10:00:00.000Z" }
        val values = SYNC_KEYS.associateWith { "v" }
        assertTrue(seedPending(meta, emptyMap(), values).isEmpty())
    }

    // ---- full logic-level round trip -------------------------------------

    @Test
    fun roundTrip_keyEnteredOnA_landsOnB_thenClearOnB_clearsA() {
        val stamp = "2026-10-22T12:00:00.000Z"
        // A: key entered before sync was enabled (pre-existing value).
        val aPending = seedPending(emptyMap(), emptyMap(), mapOf(g1 to "GEM-KEY-1"))
        val aPush = assemblePush("TOK", aPending, mapOf(g1 to "GEM-KEY-1"))
        // Backend state after A's push (server protocol shape).
        val backend = JSONObject()
        aPush.updates.forEach { (k, v) ->
            backend.put(k, JSONObject().put("value", v).put("updated_at", stamp))
        }
        // B: fresh device pulls -> key applies; B's meta now tracks it.
        val bPlan = computePull(emptyMap(), emptyMap(), backend)
        assertEquals("GEM-KEY-1", bPlan.changes[g1])
        // B: clears the key -> removal -> pending with null value -> deleted.
        val bPush = assemblePush("TOK", mapOf(g1 to 1), mapOf<String, String?>(g1 to null))
        assertEquals(listOf(g1), bPush.deleted)
        // Backend drops the entry; A pulls with its meta -> local delete.
        val aPlan = computePull(mapOf(g1 to stamp), emptyMap(), JSONObject())
        assertTrue(aPlan.changes.containsKey(g1))
        assertNull(aPlan.changes[g1])
    }
}
