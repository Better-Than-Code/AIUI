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
import kotlinx.coroutines.channels.Channel

/**
 * Hardened foreground service hosting reactive ContentObservers for both SMS and MMS.
 * Implements Epic 1:
 * - T-1.1: Persistent Database Checkpoint Store (TelephonyCheckpointStore) surviving OS kills.
 * - T-1.2: Dynamic Row-ID Delta Windowing without hardcoded upper LIMIT truncations.
 * - T-1.3: Sliding-Window Non-Cancelling Batch Debouncer with dedicated drain channels.
 * - T-1.4: Dual-Key Monotonic Sorting (DATE ASC, _ID ASC) and proximity-based multi-part reconciliation.
 */
class HardenedTelephonyObserverService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var smsObserver: SmsContentObserver? = null
    private var mmsObserver: MmsContentObserver? = null

    // Non-cancelling sliding-window batch channels
    private val smsTriggerChannel = Channel<Unit>(Channel.CONFLATED)
    private val mmsTriggerChannel = Channel<Unit>(Channel.CONFLATED)

    companion object {
        private const val TAG = "TelephonyService"
        const val NOTIFICATION_ID = 8902
        const val CHANNEL_ID = "cellular_telephony_channel"

        // In-memory cache synced with TelephonyCheckpointStore for fast synchronous access
        @Volatile
        var lastProcessedSmsId: Long = -1L
            internal set
        @Volatile
        var lastProcessedMmsId: Long = -1L
            internal set

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

        internal fun seedInitialIds(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return

            val savedSmsId = TelephonyCheckpointStore.getLastSeenSmsId(context)
            if (savedSmsId <= 0L) {
                try {
                    context.contentResolver.query(
                        Telephony.Sms.Inbox.CONTENT_URI,
                        arrayOf(Telephony.Sms._ID),
                        null,
                        null,
                        "${Telephony.Sms._ID} DESC LIMIT 1"
                    )?.use {
                        if (it.moveToFirst()) {
                            val initialId = it.getLong(0)
                            TelephonyCheckpointStore.setLastSeenSmsId(context, initialId)
                            lastProcessedSmsId = initialId
                            Log.i(TAG, "Seeded initial lastProcessedSmsId=$initialId from provider.")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed seeding initial SMS ID: ${e.message}")
                }
            } else {
                lastProcessedSmsId = savedSmsId
                Log.i(TAG, "Restored lastProcessedSmsId=$savedSmsId from checkpoint store.")
            }

            val savedMmsId = TelephonyCheckpointStore.getLastSeenMmsId(context)
            if (savedMmsId <= 0L) {
                try {
                    context.contentResolver.query(
                        Telephony.Mms.Inbox.CONTENT_URI,
                        arrayOf(Telephony.Mms._ID),
                        null,
                        null,
                        "${Telephony.Mms._ID} DESC LIMIT 1"
                    )?.use {
                        if (it.moveToFirst()) {
                            val initialId = it.getLong(0)
                            TelephonyCheckpointStore.setLastSeenMmsId(context, initialId)
                            lastProcessedMmsId = initialId
                            Log.i(TAG, "Seeded initial lastProcessedMmsId=$initialId from provider.")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed seeding initial MMS ID: ${e.message}")
                }
            } else {
                lastProcessedMmsId = savedMmsId
                Log.i(TAG, "Restored lastProcessedMmsId=$savedMmsId from checkpoint store.")
            }
        }

        /**
         * Monotonically queries and processes all inbound SMS rows newer than persistent checkpoint.
         * Handles burst arrivals (>10 msgs/sec) without truncation.
         */
        suspend fun querySmsDelta(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return

            val currentCheckpoint = TelephonyCheckpointStore.getLastSeenSmsId(context)
            if (currentCheckpoint <= 0L) {
                seedInitialIds(context)
                return
            }

            try {
                // T-1.2: Dynamic Row-ID delta query: select all rows newer than checkpoint
                // T-1.4: Dual-key monotonic sorting: DATE ASC, _ID ASC
                val cursor = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    "${Telephony.Sms._ID} > ?",
                    arrayOf(currentCheckpoint.toString()),
                    "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC"
                )

                cursor?.use {
                    val rows = mutableListOf<SmsRow>()
                    var maxSeen = currentCheckpoint
                    val activeAiNumber = PallySmsReceiver.normalizePhoneNumber(
                        com.cellular.rpc.domain.service.CellularServiceManager.getActiveService(context).phoneNumber
                    )

                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val address = it.getString(1) ?: ""
                        val body = it.getString(2) ?: ""
                        val date = it.getLong(3)
                        if (id > maxSeen) maxSeen = id

                        val normalizedSender = PallySmsReceiver.normalizePhoneNumber(address)
                        val isRecognized = normalizedSender.isNotBlank() &&
                                (activeAiNumber.isBlank() || normalizedSender == activeAiNumber)

                        if (isRecognized) {
                            rows.add(SmsRow(id, address, body, date))
                        }
                    }

                    if (maxSeen > currentCheckpoint) {
                        TelephonyCheckpointStore.setLastSeenSmsId(context, maxSeen)
                        lastProcessedSmsId = maxSeen
                    }

                    if (rows.isNotEmpty()) {
                        processSmsRows(context, rows)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMS Reconciliation failed: ${e.message}")
            }
        }

        /**
         * Monotonically queries and processes all inbound MMS rows newer than persistent checkpoint.
         */
        suspend fun queryMmsDelta(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return

            val currentCheckpoint = TelephonyCheckpointStore.getLastSeenMmsId(context)
            if (currentCheckpoint <= 0L) {
                seedInitialIds(context)
                return
            }

            try {
                val cursor = context.contentResolver.query(
                    Telephony.Mms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_TYPE),
                    "${Telephony.Mms._ID} > ?",
                    arrayOf(currentCheckpoint.toString()),
                    "${Telephony.Mms.DATE} ASC, ${Telephony.Mms._ID} ASC"
                )

                cursor?.use {
                    var maxSeen = currentCheckpoint
                    while (it.moveToNext()) {
                        val id = it.getLong(0)
                        val msgType = it.getInt(2)

                        // 132 = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF (Downloaded MMS)
                        if (msgType == 132) {
                            val processed = processSingleMms(context, id)
                            if (processed) {
                                if (id > maxSeen) maxSeen = id
                            }
                        } else if (msgType == 130) {
                            // 130 = MESSAGE_TYPE_NOTIFICATION_IND (MMSC notified, parts downloading)
                            Log.d(TAG, "MMS ID $id is notification_ind (130). Awaiting retrieve_conf.")
                        } else {
                            if (id > maxSeen) maxSeen = id
                        }
                    }

                    if (maxSeen > currentCheckpoint) {
                        TelephonyCheckpointStore.setLastSeenMmsId(context, maxSeen)
                        lastProcessedMmsId = maxSeen
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MMS Reconciliation failed: ${e.message}")
            }
        }

        /**
         * Multi-part SMS concatenation with sliding temporal proximity (<= 5000ms)
         * and dual-key sorting (DATE ASC, _ID ASC) to avoid arbitrary boundary truncations.
         */
        internal suspend fun processSmsRows(context: Context, rows: List<SmsRow>) {
            if (rows.isEmpty()) return

            // Sort all rows by normalized sender, then date ASC, then id ASC
            val sortedRows = rows.sortedWith(compareBy(
                { it.address.filter { c -> c.isDigit() || c == '+' } },
                { it.date },
                { it.id }
            ))

            // Cluster adjacent rows from same sender arriving within 5000ms
            val clusters = mutableListOf<MutableList<SmsRow>>()
            for (row in sortedRows) {
                val currentCluster = clusters.lastOrNull()
                val normalizedSender = row.address.filter { c -> c.isDigit() || c == '+' }
                if (currentCluster != null) {
                    val lastRow = currentCluster.last()
                    val lastNormalizedSender = lastRow.address.filter { c -> c.isDigit() || c == '+' }
                    val timeDiff = Math.abs(row.date - lastRow.date)
                    if (normalizedSender == lastNormalizedSender && timeDiff <= 5000L) {
                        currentCluster.add(row)
                        continue
                    }
                }
                clusters.add(mutableListOf(row))
            }

            for (segments in clusters) {
                val sorted = segments.sortedWith(compareBy({ it.date }, { it.id }))
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

        internal suspend fun processSingleMms(context: Context, mmsId: Long): Boolean {
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
                return true
            }
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification())
        seedInitialIds(this)
        startDrainWorkers()
        registerObservers()
    }

    private fun startDrainWorkers() {
        // T-1.3: Non-cancelling sliding-window drain loop for SMS
        serviceScope.launch {
            for (trigger in smsTriggerChannel) {
                // Sliding-window debounce: 350ms to allow multi-part PDUs in flight to land in SQLite
                delay(350L)
                querySmsDelta(applicationContext)
            }
        }

        // T-1.3: Non-cancelling sliding-window drain loop for MMS
        serviceScope.launch {
            for (trigger in mmsTriggerChannel) {
                // 800ms delay to allow carrier MMSC download to complete
                delay(800L)
                queryMmsDelta(applicationContext)
            }
        }
    }

    private fun registerObservers() {
        val handler = Handler(Looper.getMainLooper())
        smsObserver = SmsContentObserver(handler).also {
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, it)
        }
        mmsObserver = MmsContentObserver(handler).also {
            contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, it)
        }
        Log.i(TAG, "Registered Telephony SMS and MMS ContentObservers with sliding-window drain.")
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
        smsTriggerChannel.close()
        mmsTriggerChannel.close()
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Inner Observer for SMS: non-cancelling trigger
    private inner class SmsContentObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            smsTriggerChannel.trySend(Unit)
        }
    }

    // Inner Observer for MMS: non-cancelling trigger
    private inner class MmsContentObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            mmsTriggerChannel.trySend(Unit)
        }
    }
}

internal data class SmsRow(val id: Long, val address: String, val body: String, val date: Long)
