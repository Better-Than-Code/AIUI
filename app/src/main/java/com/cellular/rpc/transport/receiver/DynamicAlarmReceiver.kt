package com.cellular.rpc.transport.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cellular.rpc.domain.dynamic.DynamicNativeBridge

/**
 * BroadcastReceiver for offline reminders and alarm events scheduled by sandboxed mini-apps.
 * Fires notifications even when the device is in low-power sleep with no cellular or internet connection.
 */
class DynamicAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val featureId = intent.getStringExtra("featureId") ?: "app"
        val title = intent.getStringExtra("title") ?: "Cellular Reminder"
        val body = intent.getStringExtra("body") ?: "Offline reminder alert"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val notification = NotificationCompat.Builder(context, DynamicNativeBridge.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationId = (featureId.hashCode() + System.currentTimeMillis().toInt()) and 0x7FFFFFFF
        notificationManager.notify(notificationId, notification)
    }
}
