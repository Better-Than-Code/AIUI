package com.cellular.rpc.domain.dynamic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import com.cellular.rpc.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Sandboxed bridge exposed to the JavaScript execution runtime.
 *
 * Provides safe, audited native capabilities strictly scoped to pre-declared Android permissions:
 * - bridge.commit(jsonState): Persists mutated state back to Room and triggers reactive UI updates.
 * - bridge.notify(title, body): Dispatches a native Android notification.
 * - bridge.vibrate(durationMs): Performs haptic feedback.
 * - bridge.log(message): Emits debug log event.
 */
class DynamicNativeBridge(
    private val context: Context,
    private val featureId: String,
    private val onStateCommitted: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "DynamicNativeBridge"
        const val CHANNEL_ID = "cellular_dynamic_features"

        private val _bridgeEvents = MutableSharedFlow<BridgeEvent>(extraBufferCapacity = 64)
        val bridgeEvents: SharedFlow<BridgeEvent> = _bridgeEvents.asSharedFlow()
    }

    sealed class BridgeEvent {
        data class StateCommitted(val featureId: String, val newStateJson: String) : BridgeEvent()
        data class NotificationFired(val featureId: String, val title: String, val body: String) : BridgeEvent()
        data class LogEmitted(val featureId: String, val message: String) : BridgeEvent()
    }

    init {
        ensureNotificationChannel(context)
    }

    @JavascriptInterface
    fun commit(jsonState: String) {
        Log.d(TAG, "[$featureId] bridge.commit invoked with state: $jsonState")
        try {
            // Validate JSON
            org.json.JSONObject(jsonState)

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                db.dynamicFeatureDao().updateState(featureId, jsonState)
                _bridgeEvents.tryEmit(BridgeEvent.StateCommitted(featureId, jsonState))
                onStateCommitted?.invoke(jsonState)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$featureId] bridge.commit failed: ${e.message}")
        }
    }

    @JavascriptInterface
    fun notify(title: String, body: String) {
        Log.i(TAG, "[$featureId] bridge.notify: '$title' - '$body'")
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationId = (featureId.hashCode() + System.currentTimeMillis().toInt()) and 0x7FFFFFFF
            notificationManager.notify(notificationId, notification)

            _bridgeEvents.tryEmit(BridgeEvent.NotificationFired(featureId, title, body))
        } catch (e: Exception) {
            Log.e(TAG, "[$featureId] Failed to show notification: ${e.message}")
        }
    }

    @JavascriptInterface
    fun vibrate(durationMs: Long) {
        val safeDuration = durationMs.coerceIn(10, 1000)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(safeDuration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(safeDuration)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    @JavascriptInterface
    fun log(message: String) {
        Log.d(TAG, "[$featureId:JS] $message")
        _bridgeEvents.tryEmit(BridgeEvent.LogEmitted(featureId, message))
    }

    @JavascriptInterface
    fun scheduleAlarm(delaySeconds: Long, title: String, body: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = android.content.Intent(context, com.cellular.rpc.transport.receiver.DynamicAlarmReceiver::class.java).apply {
                putExtra("featureId", featureId)
                putExtra("title", title)
                putExtra("body", body)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                (featureId.hashCode() + System.currentTimeMillis().toInt()) and 0x7FFFFFFF,
                intent,
                flags
            )
            val triggerTimeMs = System.currentTimeMillis() + (delaySeconds.coerceAtLeast(1) * 1000)
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (canScheduleExact) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
            } else {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
            Log.i(TAG, "[$featureId] Scheduled offline alarm in ${delaySeconds}s: '$title'")
        } catch (e: Exception) {
            Log.e(TAG, "[$featureId] Failed to schedule alarm: ${e.message}")
        }
    }

    @JavascriptInterface
    fun setStorage(key: String, value: String) {
        val prefs = context.getSharedPreferences("cellular_bridge_$featureId", Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }

    @JavascriptInterface
    fun getStorage(key: String): String {
        val prefs = context.getSharedPreferences("cellular_bridge_$featureId", Context.MODE_PRIVATE)
        return prefs.getString(key, "") ?: ""
    }

    private fun ensureNotificationChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null && notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Cellular Dynamic Features",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications and alarms triggered by offline cellular micro-apps"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
