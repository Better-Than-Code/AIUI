package com.cellular.rpc.transport.apn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * FEAT-09: Carrier APN Auto-Detection & Direct MMSC Endpoint Resolver
 *
 * Provides intelligent on-device carrier network identification, MCC/MNC extraction,
 * system telephony APN querying, and known carrier fallback tables (Verizon, AT&T,
 * T-Mobile, Mint Mobile, TracFone, Cricket, US Cellular).
 */
data class CarrierNetworkProfile(
    val carrierName: String,
    val simOperator: String, // MCC+MNC (e.g., "310410")
    val mcc: String,
    val mnc: String,
    val activeMmscUrl: String,
    val mmsProxy: String?,
    val mmsPort: Int?,
    val isApnResolvedFromSystem: Boolean,
    val subId: Int
)

object CarrierApnResolver {
    private const val TAG = "CarrierApnResolver"
    private val APN_CONTENT_URI = Uri.parse("content://telephony/carriers")
    private val CURRENT_APN_URI = Uri.parse("content://telephony/carriers/current")

    // Known fallback MMSC configurations for US & Global carrier profiles
    private val KNOWN_MMSC_FALLBACKS = mapOf(
        // T-Mobile USA / Mint Mobile / Ultra Mobile
        "310260" to Pair("http://mms.msg.eng.t-mobile.com/mms/wapenc", null),
        "310240" to Pair("http://mms.msg.eng.t-mobile.com/mms/wapenc", null),
        "310160" to Pair("http://mms.msg.eng.t-mobile.com/mms/wapenc", null),
        // AT&T / Cricket Wireless
        "310410" to Pair("http://mmsc.mobile.att.net", Pair("proxy.mobile.att.net", 80)),
        "310280" to Pair("http://mmsc.mobile.att.net", Pair("proxy.mobile.att.net", 80)),
        "310150" to Pair("http://mmsc.mobile.att.net", Pair("proxy.mobile.att.net", 80)),
        "310030" to Pair("http://mmsc.aiowireless.net", Pair("proxy.aiowireless.net", 80)), // Cricket
        // Verizon Wireless / Visible
        "311480" to Pair("http://mms.vtext.com/servlets/mms", null),
        "310012" to Pair("http://mms.vtext.com/servlets/mms", null),
        "311270" to Pair("http://mms.vtext.com/servlets/mms", null),
        "311280" to Pair("http://mms.vtext.com/servlets/mms", null),
        // US Cellular
        "311580" to Pair("http://mms.uscc.net/servlets/mms", null),
        "311220" to Pair("http://mms.uscc.net/servlets/mms", null),
        // TracFone Multi-Carrier MVNO
        "310990" to Pair("http://mms-tf.net", Pair("mms3.tracfone.com", 80))
    )

    fun resolveProfile(context: Context): CarrierNetworkProfile {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val carrierName = telephonyManager?.simOperatorName?.ifBlank { null }
            ?: telephonyManager?.networkOperatorName?.ifBlank { null }
            ?: "Cellular Network"

        val simOperator = telephonyManager?.simOperator ?: ""
        val mcc = if (simOperator.length >= 3) simOperator.substring(0, 3) else "000"
        val mnc = if (simOperator.length >= 5) simOperator.substring(3) else "00"

        val defaultSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } else {
            -1
        }

        // 1. Try resolving directly from Android Telephony Carriers ContentProvider
        val systemApn = querySystemApn(context, simOperator)
        if (systemApn != null && systemApn.first.isNotBlank()) {
            Log.i(TAG, "Resolved MMSC from system Telephony Provider: ${systemApn.first}")
            return CarrierNetworkProfile(
                carrierName = carrierName,
                simOperator = simOperator,
                mcc = mcc,
                mnc = mnc,
                activeMmscUrl = systemApn.first,
                mmsProxy = systemApn.second?.first,
                mmsPort = systemApn.second?.second,
                isApnResolvedFromSystem = true,
                subId = defaultSubId
            )
        }

        // 2. Query known fallback lookup table via SIM operator MCC+MNC or Carrier Name matching
        val fallback = KNOWN_MMSC_FALLBACKS[simOperator]
            ?: findFallbackByCarrierName(carrierName)

        val mmscUrl = fallback?.first ?: "http://mms.msg.eng.t-mobile.com/mms/wapenc" // standard default fallback
        val proxyInfo = fallback?.second

        Log.i(TAG, "Using known carrier APN profile for $carrierName ($simOperator): $mmscUrl")

        return CarrierNetworkProfile(
            carrierName = carrierName,
            simOperator = simOperator,
            mcc = mcc,
            mnc = mnc,
            activeMmscUrl = mmscUrl,
            mmsProxy = proxyInfo?.first,
            mmsPort = proxyInfo?.second,
            isApnResolvedFromSystem = false,
            subId = defaultSubId
        )
    }

    private fun querySystemApn(context: Context, simOperator: String): Pair<String, Pair<String, Int>?>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        try {
            val projection = arrayOf("mmsc", "mmsproxy", "mmsport", "current", "type")
            val selection = if (simOperator.isNotBlank()) "numeric = ?" else null
            val selectionArgs = if (simOperator.isNotBlank()) arrayOf(simOperator) else null

            // Try current APN first
            val cursor = context.contentResolver.query(CURRENT_APN_URI, projection, null, null, null)
                ?: context.contentResolver.query(APN_CONTENT_URI, projection, selection, selectionArgs, null)

            cursor?.use {
                while (it.moveToNext()) {
                    val mmscIndex = it.getColumnIndex("mmsc")
                    if (mmscIndex >= 0) {
                        val mmsc = it.getString(mmscIndex)
                        if (!mmsc.isNullOrBlank() && mmsc.startsWith("http")) {
                            val proxyIndex = it.getColumnIndex("mmsproxy")
                            val portIndex = it.getColumnIndex("mmsport")
                            val proxy = if (proxyIndex >= 0) it.getString(proxyIndex) else null
                            val port = if (portIndex >= 0) it.getString(portIndex)?.toIntOrNull() else null
                            
                            val proxyPair = if (!proxy.isNullOrBlank()) Pair(proxy, port ?: 80) else null
                            return Pair(mmsc, proxyPair)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "Read Telephony Carriers permission restricted: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Error querying system APN: ${e.message}")
        }
        return null
    }

    private fun findFallbackByCarrierName(name: String): Pair<String, Pair<String, Int>?>? {
        val lower = name.lowercase()
        return when {
            lower.contains("verizon") || lower.contains("visible") ->
                Pair("http://mms.vtext.com/servlets/mms", null)
            lower.contains("att") || lower.contains("at&t") || lower.contains("cricket") ->
                Pair("http://mmsc.mobile.att.net", Pair("proxy.mobile.att.net", 80))
            lower.contains("t-mobile") || lower.contains("mint") || lower.contains("metro") || lower.contains("ultra") ->
                Pair("http://mms.msg.eng.t-mobile.com/mms/wapenc", null)
            lower.contains("tracfone") || lower.contains("straight talk") ->
                Pair("http://mms-tf.net", Pair("mms3.tracfone.com", 80))
            lower.contains("cellular") || lower.contains("uscc") ->
                Pair("http://mms.uscc.net/servlets/mms", null)
            else -> null
        }
    }
}
