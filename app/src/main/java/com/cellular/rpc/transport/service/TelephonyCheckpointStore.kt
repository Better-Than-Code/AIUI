package com.cellular.rpc.transport.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Persistent checkpoint store for Telephony ContentObserver cursors.
 * Guarantees monotonic row ID tracking across process restarts, LMK kills, and device reboots.
 */
object TelephonyCheckpointStore {
    private const val TAG = "TelephonyCheckpointStore"
    private const val PREFS_NAME = "cellular_telephony_checkpoints"
    private const val KEY_LAST_SEEN_SMS_ID = "last_seen_sms_id"
    private const val KEY_LAST_SEEN_MMS_ID = "last_seen_mms_id"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getLastSeenSmsId(context: Context): Long =
        getPrefs(context).getLong(KEY_LAST_SEEN_SMS_ID, -1L)

    @Synchronized
    fun setLastSeenSmsId(context: Context, id: Long) {
        if (id <= 0L) return
        val current = getLastSeenSmsId(context)
        if (id > current) {
            getPrefs(context).edit().putLong(KEY_LAST_SEEN_SMS_ID, id).apply()
            Log.d(TAG, "Advanced SMS checkpoint: $current -> $id")
        }
    }

    @Synchronized
    fun getLastSeenMmsId(context: Context): Long =
        getPrefs(context).getLong(KEY_LAST_SEEN_MMS_ID, -1L)

    @Synchronized
    fun setLastSeenMmsId(context: Context, id: Long) {
        if (id <= 0L) return
        val current = getLastSeenMmsId(context)
        if (id > current) {
            getPrefs(context).edit().putLong(KEY_LAST_SEEN_MMS_ID, id).apply()
            Log.d(TAG, "Advanced MMS checkpoint: $current -> $id")
        }
    }

    @Synchronized
    fun resetCheckpoints(context: Context) {
        getPrefs(context).edit().clear().apply()
        Log.i(TAG, "Reset all telephony checkpoints.")
    }
}
