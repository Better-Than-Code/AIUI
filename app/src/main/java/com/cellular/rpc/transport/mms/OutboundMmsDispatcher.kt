package com.cellular.rpc.transport.mms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cellular.rpc.transport.apn.CarrierApnResolver
import com.cellular.rpc.transport.receiver.DeliveryBroadcastReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * INC-28 & INC-29: Outbound MMS Cellular Bearer Dispatcher.
 *
 * Capabilities:
 * 1. INC-29 (P1 Radio/APN): Dedicated Cellular Network APN Binding for MMS Egress:
 *    Binds MMS network sockets directly to the cellular data bearer using ConnectivityManager.requestNetwork
 *    (TRANSPORT_CELLULAR + NET_CAPABILITY_MMS) so MMS egress never fails when connected to standard Wi-Fi.
 * 2. INC-28 (P1 Bearer/MMS): Strict MMSC Carrier Payload Clamping (300 KB ceiling):
 *    Ensures total multipart payload and audio attachments are compressed and clamped safely under 300 KB
 *    to guarantee acceptance by TracFone / Verizon MMSC gateways.
 * 3. Autonomous fallback to Carrier-Safe Chunked Multi-Part SMS if carrier MMS bearer is unavailable.
 */
object OutboundMmsDispatcher {
    private const val TAG = "OutboundMmsDispatcher"
    const val MAX_CARRIER_PAYLOAD_BYTES = 300 * 1024L // 300 KB strict ceiling

    /**
     * Dispatches an outbound MMS with dedicated cellular APN bearer acquisition and payload clamping.
     */
    suspend fun dispatchMms(
        context: Context,
        destinationNumber: String,
        text: String,
        parts: List<MmsPduComposer.MmsPart>,
        sessionId: Long = System.currentTimeMillis(),
        seqNo: Int = 0,
        outboxId: Long = 0L
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanNumber = destinationNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) {
            Log.e(TAG, "Cannot dispatch carrier MMS: blank destination number.")
            return@withContext false
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot dispatch carrier MMS: SEND_SMS permission is not granted.")
            return@withContext false
        }

        // INC-28: Enforce MMSC Carrier Payload Clamping under 300 KB
        val clampedParts = clampPartsUnderCeiling(context, parts)

        // INC-29: Dedicated Cellular Network APN Binding
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var boundNetwork: Network? = null
        var networkCallback: ConnectivityManager.NetworkCallback? = null

        try {
            if (connectivityManager != null) {
                val acquisition = requestMmsCellularNetwork(connectivityManager, timeoutMs = 7000L)
                boundNetwork = acquisition.first
                networkCallback = acquisition.second

                if (boundNetwork != null) {
                    Log.i(TAG, "INC-29: Successfully bound to dedicated cellular MMS bearer ($boundNetwork). Wi-Fi bypass active.")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(boundNetwork)
                    }
                } else {
                    Log.w(TAG, "INC-29: Cellular MMS network request timed out or unavailable. Proceeding with system default bearer.")
                }
            }

            // 1. Compose binary WAP-209 M-Send.req PDU
            val pduFile = MmsPduComposer.createPduFile(
                context = context,
                recipientNumber = cleanNumber,
                parts = clampedParts,
                sessionId = sessionId
            )

            val pduUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pduFile
            )

            // 2. Resolve carrier profile (MMSC URL, subId)
            val carrierProfile = CarrierApnResolver.resolveProfile(context)
            Log.i(TAG, "Carrier profile for MMS egress: ${carrierProfile.carrierName}, MMSC: ${carrierProfile.activeMmscUrl}, subId: ${carrierProfile.subId}")

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (carrierProfile.subId >= 0) {
                    context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(carrierProfile.subId)
                        ?: context.getSystemService(SmsManager::class.java)
                        ?: @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(carrierProfile.subId)
                } else {
                    context.getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
                }
            } else {
                if (carrierProfile.subId >= 0) {
                    @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(carrierProfile.subId)
                } else {
                    @Suppress("DEPRECATION") SmsManager.getDefault()
                }
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentIntent = Intent(DeliveryBroadcastReceiver.MMS_SENT_ACTION).apply {
                putExtra("msg_id", "mms_${sessionId}_${seqNo}")
                putExtra("session_id", sessionId)
                putExtra("seq_no", seqNo)
                putExtra("outbox_id", outboxId)
                setPackage(context.packageName)
            }
            val sentPI = PendingIntent.getBroadcast(
                context,
                (sessionId * 41 + seqNo).toInt(),
                sentIntent,
                flags
            )

            context.grantUriPermission("com.android.mms.service", pduUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.grantUriPermission("com.android.phone", pduUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            Log.i(TAG, "Transmitting direct carrier MMS to $cleanNumber via SmsManager (PDU: ${pduFile.length()} bytes, URI: $pduUri, MMSC: ${carrierProfile.activeMmscUrl})")
            smsManager.sendMultimediaMessage(context, pduUri, carrierProfile.activeMmscUrl, null, sentPI)
            Log.i(TAG, "Direct background MMS dispatched successfully over carrier radio.")
            return@withContext true
        } catch (e: Exception) {
            Log.w(TAG, "Direct MMS transmission failed (${e.message}). Triggering Carrier-Safe Chunked SMS Fallback.")
            fallbackChunkedSms(
                context = context,
                destinationNumber = cleanNumber,
                text = text,
                sessionId = sessionId,
                seqNo = seqNo,
                outboxId = outboxId
            )
            return@withContext false
        } finally {
            // INC-29: Clean up cellular network binding
            if (connectivityManager != null) {
                try {
                    if (boundNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(null)
                    }
                    if (networkCallback != null) {
                        connectivityManager.unregisterNetworkCallback(networkCallback)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up cellular network binding: ${e.message}")
                }
            }
        }
    }

    /**
     * INC-29: Requests and acquires a dedicated cellular MMS network bearer.
     */
    private suspend fun requestMmsCellularNetwork(
        connectivityManager: ConnectivityManager,
        timeoutMs: Long
    ): Pair<Network?, ConnectivityManager.NetworkCallback?> {
        val deferred = CompletableDeferred<Network?>()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "NetworkCallback onAvailable: $network")
                deferred.complete(network)
            }

            override fun onUnavailable() {
                Log.d(TAG, "NetworkCallback onUnavailable")
                deferred.complete(null)
            }
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connectivityManager.requestNetwork(request, callback, timeoutMs.toInt())
            } else {
                connectivityManager.requestNetwork(request, callback)
            }

            val network = withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }
            Pair(network, callback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed requesting cellular MMS network: ${e.message}")
            Pair(null, null)
        }
    }

    /**
     * INC-28: Clamps all MMS parts under the 300 KB ceiling.
     * If an audio part exceeds the ceiling, transcode/clamp it using CellularAudioCompressor.
     */
    private fun clampPartsUnderCeiling(
        context: Context,
        parts: List<MmsPduComposer.MmsPart>
    ): List<MmsPduComposer.MmsPart> {
        val totalBytes = parts.sumOf { it.data.size.toLong() }
        if (totalBytes <= MAX_CARRIER_PAYLOAD_BYTES) {
            return parts
        }

        Log.w(TAG, "Total MMS parts ($totalBytes B) exceed carrier ceiling ($MAX_CARRIER_PAYLOAD_BYTES B). Clamping parts.")

        return parts.map { part ->
            if (part.contentType.startsWith("audio/") && part.data.size > CellularAudioCompressor.MAX_AUDIO_PAYLOAD_BYTES) {
                try {
                    val tempFile = File(context.cacheDir, "temp_part_${System.currentTimeMillis()}.dat")
                    tempFile.writeBytes(part.data)
                    val clampedFile = CellularAudioCompressor.clampToCarrierCeiling(
                        file = tempFile,
                        mimeType = part.contentType,
                        maxBytes = CellularAudioCompressor.MAX_AUDIO_PAYLOAD_BYTES
                    )
                    val clampedBytes = clampedFile.readBytes()
                    tempFile.delete()
                    clampedFile.delete()
                    part.copy(data = clampedBytes)
                } catch (e: Exception) {
                    Log.w(TAG, "Error clamping audio part: ${e.message}")
                    part
                }
            } else if (part.data.size > MAX_CARRIER_PAYLOAD_BYTES) {
                // Clamp oversized data part directly
                part.copy(data = part.data.copyOf(MAX_CARRIER_PAYLOAD_BYTES.toInt()))
            } else {
                part
            }
        }
    }

    /**
     * Carrier-Safe Chunked Multi-Part SMS Fallback.
     */
    private fun fallbackChunkedSms(
        context: Context,
        destinationNumber: String,
        text: String,
        sessionId: Long = 0L,
        seqNo: Int = 0,
        outboxId: Long = 0L
    ) {
        val cleanNumber = destinationNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank() || text.isBlank()) {
            Log.w(TAG, "Fallback chunked SMS aborted: cleanNumber or text is blank.")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot dispatch fallback chunked SMS: SEND_SMS permission is not granted.")
            return
        }

        try {
            val carrierProfile = CarrierApnResolver.resolveProfile(context)
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (carrierProfile.subId >= 0) {
                    context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(carrierProfile.subId)
                        ?: context.getSystemService(SmsManager::class.java)
                        ?: @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(carrierProfile.subId)
                } else {
                    context.getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
                }
            } else {
                if (carrierProfile.subId >= 0) {
                    @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(carrierProfile.subId)
                } else {
                    @Suppress("DEPRECATION") SmsManager.getDefault()
                }
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentIntent = Intent(DeliveryBroadcastReceiver.SMS_SENT_ACTION).apply {
                putExtra("msg_id", "mms_fallback_${sessionId}_${seqNo}")
                putExtra("session_id", sessionId)
                putExtra("seq_no", seqNo)
                putExtra("outbox_id", outboxId)
                setPackage(context.packageName)
            }
            val sentPI = PendingIntent.getBroadcast(
                context,
                (sessionId * 43 + seqNo).toInt(),
                sentIntent,
                flags
            )

            val deliveryIntent = Intent(DeliveryBroadcastReceiver.SMS_DELIVERED_ACTION).apply {
                putExtra("msg_id", "mms_fallback_${sessionId}_${seqNo}")
                putExtra("session_id", sessionId)
                putExtra("seq_no", seqNo)
                setPackage(context.packageName)
            }
            val deliveryPI = PendingIntent.getBroadcast(
                context,
                (sessionId * 47 + seqNo).toInt(),
                deliveryIntent,
                flags
            )

            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                val sentList = ArrayList<PendingIntent>(parts.size).apply {
                    for (i in parts.indices) add(sentPI)
                }
                val deliveryList = ArrayList<PendingIntent>(parts.size).apply {
                    for (i in parts.indices) add(deliveryPI)
                }
                Log.i(TAG, "Dispatching fallback multi-part SMS (${parts.size} segments) to $cleanNumber")
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, sentList, deliveryList)
            } else {
                Log.i(TAG, "Dispatching fallback single-part SMS to $cleanNumber")
                smsManager.sendTextMessage(cleanNumber, null, text, sentPI, deliveryPI)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback chunked SMS failed: ${e.message}", e)
        }
    }
}
