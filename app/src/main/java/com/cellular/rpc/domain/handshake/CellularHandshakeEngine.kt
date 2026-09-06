package com.cellular.rpc.domain.handshake

import android.content.Context
import android.util.Log
import com.cellular.rpc.domain.mcp.CellularMcpRegistry
import com.cellular.rpc.domain.payload.CellularRequest
import com.cellular.rpc.domain.payload.CellularResponse
import com.cellular.rpc.domain.payload.CellularStatusCode
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent Handshake, Channel Router, and Awaiting State Engine.
 *
 * Solves:
 * 1. Schema Sync: Probes AI with catalog hash (`HELLO:<hash>`). If AI is ready, no redundant manifest is sent.
 *    If AI responds `PROBE_SCHEMA`, sends MCP Genesis Manifest.
 * 2. Channel & Action Routing: Correlates outbound reqId with target channel (Chat, Widget, Dynamic App, Tool).
 * 3. Proactive Awaiting Lifecycle:
 *    - Tracks pending transactions with timers.
 *    - At 45s: Auto-nudges AI once (`[RPC:NUDGE:<reqId>]`).
 *    - If still unresponsive or on uninterpretable error: Escalates to User Intervention.
 */
object CellularHandshakeEngine {

    private const val TAG = "CellularHandshakeEngine"
    private const val TIMEOUT_NUDGE_MS = 45_000L
    private const val MAX_INTERVENTION_MS = 90_000L

    // Handshake state
    private val _handshakeStatus = MutableStateFlow(HandshakeStatus.UNINITIALIZED)
    val handshakeStatus: StateFlow<HandshakeStatus> = _handshakeStatus.asStateFlow()

    private val _currentCatalogHash = MutableStateFlow(CellularMcpRegistry.computeCatalogHash())
    val currentCatalogHash: StateFlow<String> = _currentCatalogHash.asStateFlow()

    // Pending transactions map: reqId -> PendingTransaction
    private val pendingTransactions = ConcurrentHashMap<String, PendingTransaction>()

    private val _pendingList = MutableStateFlow<List<PendingTransaction>>(emptyList())
    val pendingList: StateFlow<List<PendingTransaction>> = _pendingList.asStateFlow()

    // Active intervention alert if user action is needed
    private val _activeIntervention = MutableStateFlow<PendingTransaction?>(null)
    val activeIntervention: StateFlow<PendingTransaction?> = _activeIntervention.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())
    private var monitoringJob: Job? = null

    init {
        startMonitoringLoop()
    }

    /**
     * Builds standard zero-bandwidth probe payload.
     */
    fun buildProbePayload(): String {
        val hash = CellularMcpRegistry.computeCatalogHash()
        _currentCatalogHash.value = hash
        _handshakeStatus.value = HandshakeStatus.PROBING_AI
        return "HELLO:${CellularMcpRegistry.MCP_PROTOCOL_VERSION}:$hash"
    }

    /**
     * Initiates the lightweight probe handshake to check if remote AI has local schemas.
     */
    fun startProbeHandshake(context: Context, destinationAddress: String): String {
        val hash = CellularMcpRegistry.computeCatalogHash()
        _currentCatalogHash.value = hash
        _handshakeStatus.value = HandshakeStatus.PROBING_AI

        val probePayload = "HELLO:${CellularMcpRegistry.MCP_PROTOCOL_VERSION}:$hash"
        Log.i(TAG, "Initiating Handshake probe: $probePayload to $destinationAddress")

        registerPending(
            reqId = "handshake_probe",
            channel = TargetChannel.SYSTEM,
            featureId = "handshake",
            displayPrompt = "Probing AI Gateway with Schema Hash $hash"
        )

        return probePayload
    }

    /**
     * Registers a new outbound request into the awaiting state tracker.
     */
    fun registerPending(
        reqId: String,
        channel: TargetChannel,
        featureId: String,
        displayPrompt: String
    ): PendingTransaction {
        val tx = PendingTransaction(
            reqId = reqId,
            targetChannel = channel,
            featureId = featureId,
            displayPrompt = displayPrompt,
            sentTimestampMs = System.currentTimeMillis()
        )
        pendingTransactions[reqId] = tx
        refreshPendingState()
        Log.d(TAG, "Registered pending transaction: [$reqId] channel=$channel feature=$featureId")
        return tx
    }

    /**
     * Inspects inbound message text for Handshake control signals:
     * - `READY` or `[RPC:ACK:READY]` -> Session Ready
     * - `PROBE_SCHEMA` or `[RPC:PROBE:SCHEMAS]` -> AI needs full genesis manifest
     * - `REQ_INTERVENTION` -> AI explicitly requested user clarification
     */
    fun inspectInboundHandshake(context: Context, text: String): Boolean {
        val trimmed = text.trim()

        // 1. AI indicates it has schema in persistent memory
        if (trimmed.contains("READY") || trimmed.contains("ACK:READY") || trimmed.contains("MCP:READY")) {
            Log.i(TAG, "AI confirmed schema sync. Handshake complete -> SESSION_READY")
            _handshakeStatus.value = HandshakeStatus.SESSION_READY
            completeTransaction("handshake_probe")
            return true
        }

        // 2. AI needs Genesis Manifest
        if (trimmed.contains("PROBE_SCHEMA") || trimmed.contains("PROBE:SCHEMAS") || trimmed.contains("SEND_GENESIS")) {
            Log.i(TAG, "AI requested full MCP Genesis Manifest -> AWAITING_GENESIS")
            _handshakeStatus.value = HandshakeStatus.AWAITING_GENESIS
            completeTransaction("handshake_probe")

            // Send Genesis Manifest automatically
            val genesis = CellularMcpRegistry.buildGenesisManifestJson()
            val carrierEngine = CarrierSafeQueueEngine.getInstance(context)
            engineScope.launch {
                carrierEngine.enqueuePayload(
                    sessionId = 0,
                    pktType = 0x05.toByte(), // RPC Request
                    payload = genesis.toByteArray(Charsets.UTF_8)
                )
            }
            _handshakeStatus.value = HandshakeStatus.SESSION_READY
            return true
        }

        return false
    }

    /**
     * Completes a pending transaction by reqId.
     */
    fun completeTransaction(reqId: String) {
        val tx = pendingTransactions.remove(reqId)
        if (tx != null) {
            tx.awaitingState = AwaitingState.COMPLETED
            if (_activeIntervention.value?.reqId == reqId) {
                _activeIntervention.value = null
            }
            refreshPendingState()
            Log.d(TAG, "Completed transaction: [$reqId]")
        }
    }

    /**
     * Resolves which channel and feature an inbound response belongs to.
     */
    fun correlateResponse(response: CellularResponse): PendingTransaction? {
        val reqId = response.reqId
        if (!reqId.isNullOrEmpty()) {
            val tx = pendingTransactions[reqId]
            if (tx != null) {
                completeTransaction(reqId)
                return tx
            }
        }
        return null
    }

    /**
     * Manually triggers user intervention resolution (Retry, Cancel, or Override).
     */
    fun resolveIntervention(reqId: String, action: String) {
        val tx = pendingTransactions[reqId] ?: return
        when (action.uppercase()) {
            "DISMISS", "CANCEL" -> {
                pendingTransactions.remove(reqId)
                if (_activeIntervention.value?.reqId == reqId) {
                    _activeIntervention.value = null
                }
                refreshPendingState()
            }
            "RETRY" -> {
                tx.awaitingState = AwaitingState.AWAITING_RESPONSE
                tx.retryCount++
                if (_activeIntervention.value?.reqId == reqId) {
                    _activeIntervention.value = null
                }
                refreshPendingState()
            }
        }
    }

    private fun startMonitoringLoop() {
        monitoringJob?.cancel()
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(5000)
                checkTimeouts()
            }
        }
    }

    private fun checkTimeouts() {
        val now = System.currentTimeMillis()
        var stateChanged = false

        for ((reqId, tx) in pendingTransactions) {
            val elapsed = now - tx.sentTimestampMs

            if (tx.awaitingState == AwaitingState.AWAITING_RESPONSE && elapsed > TIMEOUT_NUDGE_MS) {
                // Auto-nudge state
                tx.awaitingState = AwaitingState.AUTO_NUDGING
                tx.retryCount = 1
                stateChanged = true
                Log.w(TAG, "Transaction [$reqId] timed out after ${elapsed}ms -> AUTO_NUDGE triggered")
            } else if (tx.awaitingState == AwaitingState.AUTO_NUDGING && elapsed > MAX_INTERVENTION_MS) {
                // Escalate to user intervention
                tx.awaitingState = AwaitingState.USER_INTERVENTION
                tx.lastErrorReason = "No response from AI after auto-nudge (${elapsed / 1000}s elapsed). Check cellular coverage."
                _activeIntervention.value = tx
                stateChanged = true
                Log.e(TAG, "Transaction [$reqId] unresponsive -> USER_INTERVENTION required")
            }
        }

        if (stateChanged) {
            refreshPendingState()
        }
    }

    private fun refreshPendingState() {
        _pendingList.value = pendingTransactions.values.toList()
    }
}
