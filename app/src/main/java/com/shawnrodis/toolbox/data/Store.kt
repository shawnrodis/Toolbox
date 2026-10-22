package com.shawnrodis.toolbox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Native replacement for the HTML app's localStorage.
 * Keys deliberately keep the original localStorage key names so the mapping
 * web -> native stays 1:1 and auditable.
 */
val Context.toolboxDataStore: DataStore<Preferences> by preferencesDataStore(name = "toolbox_prefs")

/** Settings subset that drives theming and the Settings screen. */
data class AppSettings(
    // Source: setThemeMode() — invalid/absent mode falls back to "black";
    // modes are "light" | "dark" | "black" (toolbox_dark).
    val themeMode: String = "black",
    // Source: toolbox_accent, default accent in CSS is indigo.
    val accent: String = "indigo",
    // Source: toolbox_custom_accent (hex string, only used when accent == "custom").
    val customAccent: String = "#6366f1",
    // Source: toolboxSoundOn() — sound is on unless explicitly turned off.
    val sound: Boolean = true,
    // API keys: USER-ENTERED ONLY, stored on-device. Never hardcoded.
    val aviationstackKey: String = "",
    val geminiKey1: String = "",
    val geminiKey2: String = "",
    // Parity pass — source getHomeView(): toolbox_home_view, default "iphone",
    // invalid values fall back to "iphone" (setHomeView validation).
    val homeView: String = "iphone",
    // Parity pass — source toolbox_desktop: Desktop Mode on/off. The web app
    // stores the boolean via localStorage.setItem (string "true"/"false").
    val desktopMode: Boolean = false,
    // Parity pass — source toolbox_ui_scale / updateUIScale(): root scale
    // percent, slider range 25..150 step 5, default 100.
    val uiScale: Int = 100
)

/** Source resetSettings(): the exact key lists it clears (in order). */
val RESET_KEYS_PRIMARY: List<String> = listOf(
    "toolbox_accent", "toolbox_custom_accent", "toolbox_dark", "toolbox_ui_scale",
    "toolbox_desktop", "toolbox_tools_config", "toolbox_taskbar_pins",
    "toolbox_desktop_icon_pos", "toolbox_desktop_icon_opts", "toolbox_home_view",
    "toolbox_pto_logs", "toolbox_pto_settings", "toolbox_tally_items",
    "toolbox_tally_sound", "toolbox_sound", "toolbox_tracked_times",
    "toolbox_comparison_options", "toolbox_shop_items", "skytrack_mobile_state",
    "pdf_suite_preview_scale", "toolbox_currency_state", "toolbox_time_state",
    "toolbox_uc_state", "toolbox_sbm_state", "toolbox_notes",
    "toolbox_split_state", "toolbox_tip_state", "toolbox_aviationstack_key",
    "toolbox_gemini_key_1", "toolbox_gemini_key_2"
)

/** Source resetSettings(): sync state cleared after the wipe is pushed. */
val RESET_KEYS_SYNC: List<String> = listOf(
    "toolbox_sync_url", "toolbox_sync_token", "toolbox_sync_interval",
    "toolbox_sync_meta", "toolbox_sync_pending"
)

class AppStore(private val context: Context) {
    private val ds: DataStore<Preferences> get() = context.toolboxDataStore

    /**
     * Phase 3 Sync dirty-tracking hook — the native equivalent of the source
     * wrapping localStorage.setItem/removeItem: every write that CHANGES a
     * SYNC_KEYS value reports the key here so SyncManager can mark it pending
     * and schedule a debounced push. Set once by SyncManager at app start.
     * Redundant writes (same value) are ignored, exactly like the source,
     * so init-time restores don't cause push/reload loops.
     */
    @Volatile
    var syncedKeyChangedHook: ((String) -> Unit)? = null

    private fun notifyIfSyncedKeyChanged(key: String, old: String?, new: String?) {
        if (key in SYNC_KEYS && old != new) {
            try {
                syncedKeyChangedHook?.invoke(key)
            } catch (e: Exception) {
                // Sync bookkeeping must never break a tool's own save.
            }
        }
    }

    val settings: Flow<AppSettings> = ds.data.map { p ->
        AppSettings(
            themeMode = p[stringPreferencesKey("toolbox_dark")] ?: "black",
            accent = p[stringPreferencesKey("toolbox_accent")] ?: "indigo",
            customAccent = p[stringPreferencesKey("toolbox_custom_accent")] ?: "#6366f1",
            sound = p[booleanPreferencesKey("toolbox_sound")] ?: true,
            aviationstackKey = p[stringPreferencesKey("toolbox_aviationstack_key")] ?: "",
            geminiKey1 = p[stringPreferencesKey("toolbox_gemini_key_1")] ?: "",
            geminiKey2 = p[stringPreferencesKey("toolbox_gemini_key_2")] ?: "",
            homeView = p[stringPreferencesKey("toolbox_home_view")]?.takeIf {
                it == "list" || it == "iphone" || it == "android"
            } ?: "iphone",
            desktopMode = p[stringPreferencesKey("toolbox_desktop")] == "true",
            uiScale = p[stringPreferencesKey("toolbox_ui_scale")]?.toIntOrNull()
                ?.coerceIn(25, 150) ?: 100
        )
    }

    suspend fun setSetting(key: String, value: String) {
        val k = stringPreferencesKey(key)
        var old: String? = null
        ds.edit { p -> old = p[k]; p[k] = value }
        notifyIfSyncedKeyChanged(key, old, value)
    }

    suspend fun setSound(value: Boolean) {
        ds.edit { it[booleanPreferencesKey("toolbox_sound")] = value }
    }

    /** Raw persisted state (usually JSON), keyed by the original localStorage key name. */
    fun stringState(key: String, default: String = ""): Flow<String> =
        ds.data.map { it[stringPreferencesKey(key)] ?: default }

    suspend fun setStringState(key: String, value: String) {
        val k = stringPreferencesKey(key)
        var old: String? = null
        ds.edit { p -> old = p[k]; p[k] = value }
        notifyIfSyncedKeyChanged(key, old, value)
    }

    /**
     * Raw write that bypasses dirty tracking — the native equivalent of the
     * source's _lsSetItem. Sync pull uses this when applying server values so
     * downloaded state is not immediately re-marked as a local change.
     */
    suspend fun setStringStateRaw(key: String, value: String) {
        ds.edit { it[stringPreferencesKey(key)] = value }
    }

    /** Raw remove, bypassing dirty tracking (source's _lsRemoveItem). */
    suspend fun removeStringStateRaw(key: String) {
        ds.edit { it.remove(stringPreferencesKey(key)) }
    }

    /** Removes a key. A removal of a SYNC_KEYS entry is a sync deletion. */
    suspend fun removeStringState(key: String) {
        val k = stringPreferencesKey(key)
        var old: String? = null
        ds.edit { p -> old = p[k]; p.remove(k) }
        if (old != null) notifyIfSyncedKeyChanged(key, old, null)
    }

    /**
     * Parity pass — resetSettings(): clears exactly the keys the source
     * clears. toolbox_sound / toolbox_tally_sound live as booleans in the
     * web app's localStorage (strings there); here the sound flag is a
     * boolean pref, so it is removed by its boolean key as well. Removals go
     * through the sync-aware path so a configured sync sees the deletions,
     * exactly like the source's hooked localStorage.removeItem.
     */
    suspend fun clearKeys(keys: List<String>) {
        keys.forEach { removeStringState(it) }
        ds.edit { p ->
            p.remove(booleanPreferencesKey("toolbox_sound"))
            p.remove(stringPreferencesKey("toolbox_tally_sound"))
        }
    }

    /** One-shot snapshot of the given keys (null = absent), for Sync push. */
    suspend fun snapshot(keys: List<String>): Map<String, String?> {
        val p = ds.data.first()
        return keys.associateWith { p[stringPreferencesKey(it)] }
    }
}
