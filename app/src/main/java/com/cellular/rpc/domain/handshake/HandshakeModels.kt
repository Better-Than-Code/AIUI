package com.cellular.rpc.domain.handshake

/**
 * Handshake and connection phase between the client and remote AI gateway.
 */
enum class HandshakeStatus {
    UNINITIALIZED,     // No probe sent yet
    PROBING_AI,        // Sent HELLO:hash, awaiting ACK / PROBE
    AWAITING_GENESIS,  // AI requested full schemas, sending Genesis Manifest
    SESSION_READY,     // Handshake complete, AI has persistent schemas in context
    DESYNC_DETECTED    // AI sent uninterpretable payload or mismatch detected
}

/**
 * Channel categorization for cellular requests and responses.
 */
enum class TargetChannel {
    CHAT,           // Freeform conversational messaging & prompts
    WIDGET,         // Structured 200/304 visual cards (weather, news, market)
    DYNAMIC_APP,    // [APP:BUILD:<id>] AST & Sandboxed extensions
    TOOL_EXECUTION, // Native client execution (poll vote, transfer confirm)
    SYSTEM          // Genesis sync, ping, heartbeat
}

/**
 * State of an outbound request currently awaiting an AI response.
 */
enum class AwaitingState {
    AWAITING_RESPONSE, // Normal waiting window (0 - 45s)
    AUTO_NUDGING,      // Timeout elapsed, 1 automatic nudge sent
    USER_INTERVENTION, // Stalled or uninterpretable error, requires user choice
    COMPLETED,         // Successfully resolved & dispatched to channel
    FAILED             // Terminated or cancelled
}

/**
 * Active transaction awaiting response.
 */
data class PendingTransaction(
    val reqId: String,
    val targetChannel: TargetChannel,
    val featureId: String,
    val displayPrompt: String,
    val sentTimestampMs: Long = System.currentTimeMillis(),
    val maxTimeoutMs: Long = 45_000L,
    var awaitingState: AwaitingState = AwaitingState.AWAITING_RESPONSE,
    var retryCount: Int = 0,
    var lastErrorReason: String? = null
)
