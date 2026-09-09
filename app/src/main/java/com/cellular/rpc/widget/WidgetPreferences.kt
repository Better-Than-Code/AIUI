package com.cellular.rpc.widget

import android.content.Context
import android.content.SharedPreferences

object WidgetPreferences {
    private const val PREFS_NAME = "cellular_widget_prefs"

    private const val KEY_WEATHER_CITY = "weather_city_"
    private const val KEY_WEATHER_ZIP = "weather_zip_"
    private const val KEY_WEATHER_UNIT = "weather_unit_"
    private const val KEY_NEWS_TOPIC = "news_topic_"
    private const val KEY_TRANSPARENCY = "transparency_"
    private const val KEY_INTERVAL_MIN = "interval_min_"

    // Global App Preferences
    private const val KEY_PALLY_PHONE = "global_pally_phone"
    private const val KEY_LOOPBACK_SIMULATION = "global_loopback_sim"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Weather settings per widgetId
    fun setWeatherConfig(context: Context, appWidgetId: Int, city: String, zip: String, unit: String) {
        getPrefs(context).edit()
            .putString(KEY_WEATHER_CITY + appWidgetId, city)
            .putString(KEY_WEATHER_ZIP + appWidgetId, zip)
            .putString(KEY_WEATHER_UNIT + appWidgetId, unit)
            .apply()
    }

    fun getWeatherCity(context: Context, appWidgetId: Int): String =
        getPrefs(context).getString(KEY_WEATHER_CITY + appWidgetId, "San Francisco") ?: "San Francisco"

    fun getWeatherZip(context: Context, appWidgetId: Int): String =
        getPrefs(context).getString(KEY_WEATHER_ZIP + appWidgetId, "94102") ?: "94102"

    fun getWeatherUnit(context: Context, appWidgetId: Int): String =
        getPrefs(context).getString(KEY_WEATHER_UNIT + appWidgetId, "F") ?: "F"

    // News settings per widgetId
    fun setNewsConfig(context: Context, appWidgetId: Int, topic: String) {
        getPrefs(context).edit()
            .putString(KEY_NEWS_TOPIC + appWidgetId, topic)
            .apply()
    }

    fun getNewsTopic(context: Context, appWidgetId: Int): String =
        getPrefs(context).getString(KEY_NEWS_TOPIC + appWidgetId, "TECH & WORLD") ?: "TECH & WORLD"

    // Visual Transparency / Opacity (0 = completely clear/transparent, 100 = solid)
    fun setTransparency(context: Context, appWidgetId: Int, percent: Int) {
        getPrefs(context).edit().putInt(KEY_TRANSPARENCY + appWidgetId, percent.coerceIn(0, 100)).apply()
    }

    fun getTransparency(context: Context, appWidgetId: Int): Int =
        getPrefs(context).getInt(KEY_TRANSPARENCY + appWidgetId, 85) // Default 85% opacity (dark glass)

    // Update interval (in minutes: 0 = manual only, 15, 30, 60, 180, 360)
    fun setIntervalMinutes(context: Context, appWidgetId: Int, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_INTERVAL_MIN + appWidgetId, minutes).apply()
    }

    fun getIntervalMinutes(context: Context, appWidgetId: Int): Int =
        getPrefs(context).getInt(KEY_INTERVAL_MIN + appWidgetId, 60) // Default 1 hour

    // Global Service / Pally AI Phone Number (Agnostic AI SMS Gateway)
    fun getPallyPhoneNumber(context: Context): String =
        com.cellular.rpc.domain.service.CellularServiceManager.getActiveService(context).phoneNumber

    fun setPallyPhoneNumber(context: Context, number: String) {
        com.cellular.rpc.domain.service.CellularServiceManager.setManualPhoneNumber(context, number.trim())
    }

    // Global Admin Approval Mode
    private const val KEY_ADMIN_APPROVAL_MODE = "global_admin_approval_mode"

    fun isAdminApprovalModeEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_ADMIN_APPROVAL_MODE, false)

    fun setAdminApprovalModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ADMIN_APPROVAL_MODE, enabled).apply()
    }

    // Global Loopback / Emulated Response Mode
    fun isLoopbackSimulationEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_LOOPBACK_SIMULATION, true)

    fun setLoopbackSimulationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LOOPBACK_SIMULATION, enabled).apply()
    }

    // Background color calculation with alpha
    fun getBackgroundColorArgb(context: Context, appWidgetId: Int): Int {
        val opacityPercent = getTransparency(context, appWidgetId)
        val alpha = (255 * (opacityPercent / 100f)).toInt().coerceIn(0, 255)
        val red = 0x10
        val green = 0x18
        val blue = 0x23
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    // MCP (Model Context Protocol) Discovery & Sync State
    private const val KEY_MCP_SYNCED_HASH = "mcp_synced_hash"
    private const val KEY_MCP_SYNC_TIMESTAMP = "mcp_sync_timestamp"

    fun getMcpSyncedHash(context: Context): String? =
        getPrefs(context).getString(KEY_MCP_SYNCED_HASH, null)

    fun setMcpSyncedHash(context: Context, hash: String) {
        getPrefs(context).edit()
            .putString(KEY_MCP_SYNCED_HASH, hash)
            .putLong(KEY_MCP_SYNC_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getMcpLastSyncTimestamp(context: Context): Long =
        getPrefs(context).getLong(KEY_MCP_SYNC_TIMESTAMP, 0L)

    fun isMcpSynced(context: Context, currentCatalogHash: String): Boolean =
        getMcpSyncedHash(context) == currentCatalogHash
}
