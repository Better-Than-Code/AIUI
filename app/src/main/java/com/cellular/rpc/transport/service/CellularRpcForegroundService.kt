package com.cellular.rpc.transport.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class CellularRpcForegroundService : Service() {

    private var queueEngine: CarrierSafeQueueEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val db = AppDatabase.getInstance(applicationContext)
        queueEngine = CarrierSafeQueueEngine(
            context = applicationContext,
            outboxDao = db.outboxDao(),
            destinationAddress = "+16462619684",
            destinationPort = 8901
        ).also {
            instance = this
            activeEngine = it
        }
        queueEngine?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CellularRpc::TransmissionWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L /* 10 Minutes Max Safe Timeout */)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val channelId = "cellular_rpc_channel"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "Cellular RPC Link Active",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Maintains low-bandwidth SMS RPC transmission and queue state"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Cellular Transport Active")
            .setContentText("Listening for offline SMS packets and queue state.")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        queueEngine?.stop()
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        instance = null
        activeEngine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 8901
        const val ACTION_START = "com.cellular.rpc.START_SERVICE"
        const val ACTION_STOP = "com.cellular.rpc.STOP_SERVICE"

        var instance: CellularRpcForegroundService? = null
            private set
        var activeEngine: CarrierSafeQueueEngine? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
