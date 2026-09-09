package com.cellular.rpc.engine

import android.content.Context
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.MutationLogEntity
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Sprint 6.3: The Medic (Anomaly & Fallback Guardian)
 * 
 * A global singleton that intercepts parsing errors or rendering faults.
 * When a fault is detected, it logs it to the Black Box (MutationLogEntity)
 * and dispatches a silent SMS to the AI Gateway requesting a dynamic SDUI patch.
 */
object CellularMedic {
    private const val TAG = "CellularMedic"

    // Debounce to prevent flooding the network if a crash loop happens
    private var lastInterventionMs = 0L
    private const val COOLDOWN_MS = 60_000L 

    /**
     * Called when a JSON parsing error, widget crash, or UI fault occurs.
     */
    fun onFaultDetected(
        context: Context,
        componentName: String,
        faultDescription: String,
        rawPayload: String? = null
    ) {
        val now = System.currentTimeMillis()
        if (now - lastInterventionMs < COOLDOWN_MS) {
            Log.w(TAG, "Medic on cooldown. Suppressing fault report for $componentName.")
            return
        }
        lastInterventionMs = now

        Log.e(TAG, "Medic triggered! Fault in $componentName: $faultDescription")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val mutationId = UUID.randomUUID().toString().take(8)

                // 1. Log the Anomaly to the Black Box
                val logEntry = MutationLogEntity(
                    id = mutationId,
                    timestamp = now,
                    triggerEvent = "FAULT: $componentName | $faultDescription",
                    patchApplied = "PENDING_GATEWAY_RESPONSE",
                    stabilityStatus = "AWAITING_FIX"
                )
                db.mutationLogDao().insert(logEntry)

                // 2. Dispatch a Silent SMS Diagnostic Ping
                val queueEngine = CarrierSafeQueueEngine.getInstance(context)
                
                // Construct the highly compressed diagnostic request
                val diagnosticPayload = "[DIAGNOSTIC_PING|TGT:$componentName|ERR:$faultDescription]"
                
                // Enqueue as a standard RPC Request (It will be BPE compressed automatically)
                queueEngine.enqueuePayload(
                    sessionId = mutationId.hashCode() and 0x7FFFFFFF, 
                    pktType = Frame.PKT_RPC_REQ,
                    payload = diagnosticPayload.toByteArray(Charsets.UTF_8)
                )
                
                Log.i(TAG, "Medic dispatched silent diagnostic ping for mutation $mutationId.")

            } catch (e: Exception) {
                Log.e(TAG, "Medic failed to process anomaly: ${e.message}")
            }
        }
    }
    
    /**
     * Called when the Gateway responds with a successful SDUI JSON Patch.
     */
    fun onPatchApplied(context: Context, componentName: String, patchJson: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                
                // Find the most recent awaiting fix
                val awaitingFixes = db.mutationLogDao().getMutationsByStatus("AWAITING_FIX")
                val targetMutation = awaitingFixes.firstOrNull { it.triggerEvent.contains(componentName) }
                
                if (targetMutation != null) {
                    val updatedLog = targetMutation.copy(
                        patchApplied = patchJson,
                        stabilityStatus = "VERIFIED_STABLE"
                    )
                    db.mutationLogDao().insert(updatedLog)
                    Log.i(TAG, "Medic verified and sealed mutation ${targetMutation.id} for $componentName.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Medic failed to log patch application: ${e.message}")
            }
        }
    }
}
