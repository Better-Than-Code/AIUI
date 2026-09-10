package com.cellular.rpc.ui.chat.theme

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cellular.rpc.engine.JsonPatchEngine
import com.cellular.rpc.engine.WatchdogSafeBootManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Chat UI Theme Engine & Manager.
 * 
 * Manages reactive theme state, applies RFC 6902 delta patches instantly to Compose flows,
 * coordinates staged application with WatchdogSafeBootManager, and restores stable state
 * if an anomaly is detected.
 */
object ChatThemeManager {
    private const val TAG = "ChatThemeManager"
    private const val PREFS_NAME = "cellular_chat_theme_prefs"
    private const val KEY_THEME_JSON = "current_chat_theme_json"

    private var prefs: SharedPreferences? = null
    private val _themeFlow = MutableStateFlow(ChatThemeConfig.DEFAULT)
    val themeFlow: StateFlow<ChatThemeConfig> = _themeFlow.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs?.getString(KEY_THEME_JSON, null)
        if (!savedJson.isNullOrBlank()) {
            _themeFlow.value = ChatThemeConfig.fromJson(savedJson)
        }

        // Hook Watchdog rollback listener to restore theme
        WatchdogSafeBootManager.initialize(context) {
            val stableJson = WatchdogSafeBootManager.getStableThemeJson()
            if (!stableJson.isNullOrBlank()) {
                _themeFlow.value = ChatThemeConfig.fromJson(stableJson)
                prefs?.edit()?.putString(KEY_THEME_JSON, stableJson)?.apply()
            } else {
                _themeFlow.value = ChatThemeConfig.DEFAULT
                prefs?.edit()?.remove(KEY_THEME_JSON)?.apply()
            }
        }
    }

    /**
     * Applies a JSON patch or full JSON config to the active theme.
     * Stages the modification with WatchdogSafeBootManager.
     */
    fun updateTheme(context: Context, newThemeJson: String, isStaged: Boolean = true) {
        try {
            val currentJson = JSONObject(_themeFlow.value.toJson())
            val updatedJson = if (newThemeJson.trim().startsWith("[")) {
                // RFC 6902 Patch array
                val operations = JsonPatchEngine.parsePatch(newThemeJson)
                JsonPatchEngine.applyPatch(currentJson, operations)
                currentJson
            } else {
                // Merge or direct object replacement
                val patchObj = JSONObject(newThemeJson)
                val keys = patchObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    currentJson.put(k, patchObj.get(k))
                }
                currentJson
            }

            val updatedConfig = ChatThemeConfig.fromJson(updatedJson.toString())
            _themeFlow.value = updatedConfig

            // Persist
            prefs?.edit()?.putString(KEY_THEME_JSON, updatedConfig.toJson())?.apply()

            if (isStaged) {
                WatchdogSafeBootManager.stageConfiguration(context, updatedConfig.toJson())
            }

            Log.i(TAG, "Chat theme updated successfully: ${updatedConfig.toJson()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply chat theme update: ${e.message}", e)
        }
    }

    /**
     * Resets theme to default values.
     */
    fun resetTheme() {
        _themeFlow.value = ChatThemeConfig.DEFAULT
        prefs?.edit()?.remove(KEY_THEME_JSON)?.apply()
        WatchdogSafeBootManager.resetEmergencyFallback()
    }
}
