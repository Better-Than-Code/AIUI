package com.cellular.rpc.transport.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cellular.rpc.transport.handler.CellularMessageDispatcher
import com.cellular.rpc.transport.handler.CellularTransportType
import com.cellular.rpc.transport.handler.InboundCellularMessage
import com.cellular.rpc.transport.receiver.PallySmsReceiver
import kotlinx.coroutines.*

/**
 * Hardened foreground service hosting reactive ContentObservers for both SMS and MMS,
 * equipped with multi-part concatenation buffering, MMS part verification, and reconciliation.
 */
class HardenedTelephonyObserverService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var smsObserver: SmsContentObserver? = null
    private var mmsObserver: MmsContentObserver? = null

    companion object {
        private const val TAG = "TelephonyService"
        const val NOTIFICATION_ID = 8902
        const val CHANNEL_ID = "cellular_telephony_channel"

        @Volatile
        var lastProcessedSmsId = -1L
        @Volatile
        var lastProcessedMmsId = -1L

        fun start(context: Context) {
            try {
                val intent = Intent(context, HardenedTelephonyObserverService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed starting HardenedTelephonyObserverService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, HardenedTelephonyObserverService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Failed stopping HardenedTelephonyObserverService: ${e.message}")
            }
        }

        /**
         * Reconciliation hook called on MainActivity onResume() to guarantee zero dropped messages.
         */
        fun reconcileMissedMessages(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                querySmsDelta(context.applicationContext)
                queryMmsDelta(context.applicationContext)
            }
        }

        private suspend fun querySmsDelta(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
            try {
                val cursor = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null,
                    null,
                    "${Telephony.Sms._ID} DESC LIMIT 20"
                )
                cursor?.use {
                    val rows = mutableListOf<SmsRow>()
                    var maxSeen = lastProcessedSmsId
                    val activeAiNumber = PallySmsReceiver.normalizePhoneNumber(com.cellular.rpc.domain.service.CellularServiceManager.getActiveService(context).phoneNumber)

                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val address = it.getString(1) ?: ""
                        val body = it.getString(2) ?: ""
                        val date = it.getLong(3)
                        if (id > maxSeen) maxSeen = id

                        val normalizedSender = PallySmsReceiver.normalizePhoneNumber(address)
                        val isRecognized = normalizedSender.isNotBlank() && (activeAiNumber.isBlank() || normalizedSender == activeAiNumber)

                        if (lastProcessedSmsId != -1L && id > lastProcessedSmsId && isRecognized) {
                            rows.add(SmsRow(id, address, body, date))
                        }
                    }
                    if (maxSeen > lastProcessedSmsId) lastProcessedSmsId = maxSeen
                    processSmsRows(context, rows)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMS Reconciliation failed: ${e.message}")
            }
        }

        private suspend fun queryMmsDelta(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
            try {
                val cursor = context.contentResolver.query(
                    Telephony.Mms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_TYPE),
                    null,
                    null,
                    "${Telephony.Mms._ID} DESC LIMIT 10"
                )
                cursor?.use {
                    var maxSeen = lastProcessedMmsId
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val msgType = it.getInt(2)
                        if (id > maxSeen) maxSeen = id

                        // 132 = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF (Downloaded MMS)
                        if (lastProcessedMmsId != -1L && id > lastProcessedMmsId && msgType == 132) {
                            processSingleMms(context, id)
                        }
                    }
                    if (maxSeen > lastProcessedMmsId) lastProcessedMmsId = maxSeen
                }
            } catch (e: Exception) {
                Log.e(TAG, "MMS Reconciliation failed: ${e.message}")
            }
        }

        internal suspend fun processSmsRows(context: Context, rows: List<SmsRow>) {
            if (rows.isEmpty()) return
            // Group multi-part segments by sender and 4-second time cluster
            val clusters = rows.groupBy { "${it.address.filter { c -> c.isDigit() || c == '+' }}:${it.date / 4000L}" }
            for ((_, segments) in clusters) {
                val sorted = segments.sortedBy { it.id }
                val sender = sorted.first().address
                val fullBody = sorted.joinToString("") { it.body }
                val date = sorted.first().date

                Log.i(TAG, "Ingested SMS (${sorted.size} parts) from $sender: ${fullBody.take(60)}")
                CellularMessageDispatcher.dispatchInbound(
                    context,
                    InboundCellularMessage(
                        transportType = CellularTransportType.SMS_TEXT_WIRE,
                        senderAddress = sender,
                        rawText = fullBody,
                        timestampMs = date
                    )
                )
            }
        }

        internal suspend fun processSingleMms(context: Context, mmsId: Long) {
            val (sender, body, attachment) = HardenedMmsParser.extractMmsPayloadWithRetry(context, mmsId)
            if (body.isNotBlank() || attachment != null) {
                Log.i(TAG, "Ingested MMS ID $mmsId from $sender: text='${body.take(50)}', attachment=${attachment?.fileName}")
                CellularMessageDispatcher.dispatchInbound(
                    context,
                    InboundCellularMessage(
                        transportType = CellularTransportType.MMS_WAP_PUSH,
                        senderAddress = sender,
                        rawText = body,
                        attachment = attachment
                    )
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerObservers()
        seedInitialIds()
    }

    private fun registerObservers() {
        val handler = Handler(Looper.getMainLooper())
        smsObserver = SmsContentObserver(handler).also {
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, it)
        }
        mmsObserver = MmsContentObserver(handler).also {
            contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, it)
        }
        Log.i(TAG, "Registered Telephony SMS and MMS ContentObservers successfully.")
    }

    private fun seedInitialIds() {
        serviceScope.launch {
            if (ContextCompat.checkSelfPermission(this@HardenedTelephonyObserverService, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID), null, null, "${Telephony.Sms._ID} DESC LIMIT 1")?.use {
                    if (it.moveToFirst()) lastProcessedSmsId = it.getLong(0)
                }
                contentResolver.query(Telephony.Mms.Inbox.CONTENT_URI, arrayOf(Telephony.Mms._ID), null, null, "${Telephony.Mms._ID} DESC LIMIT 1")?.use {
                    if (it.moveToFirst()) lastProcessedMmsId = it.getLong(0)
                }
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CellularRpc::TelephonyObserverWakeLock").apply {
            acquire(15 * 60 * 1000L) // Safe 15-minute auto-release
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cellular Ingestion Monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Monitors cellular SMS/MMS ingestion in the background"
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cellular Ingestion Active")
            .setContentText("Monitoring inbound SMS and MMS SDUI payloads.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
        mmsObserver?.let { contentResolver.unregisterContentObserver(it) }
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Inner Observer for SMS
    private inner class SmsContentObserver(handler: Handler) : ContentObserver(handler) {
        private var debounceJob: Job? = null
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            debounceJob?.cancel()
            debounceJob = serviceScope.launch {
                delay(1200) // 1.2s debounce to capture all multi-part segments
                querySmsDelta(applicationContext)
            }
        }
    }

    // Inner Observer for MMS
    private inner class MmsContentObserver(handler: Handler) : ContentObserver(handler) {
        private var debounceJob: Job? = null
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            debounceJob?.cancel()
            debounceJob = serviceScope.launch {
                delay(2000) // 2.0s delay to allow carrier MMSC download to complete
                queryMmsDelta(applicationContext)
            }
        }
    }
}

internal data class SmsRow(val id: Long, val address: String, val body: String, val date: Long)
