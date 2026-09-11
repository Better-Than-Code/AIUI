package com.cellular.rpc.transport.cooldown

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Epic 4: 15-Minute Carrier Cooldown Circuit Breaker.
 *
 * Protects subscriber SIM cards and carrier line reputation by preventing aggressive
 * outbound retry loops during carrier rate limits, line throttling, or physical radio failures.
 *
 * Implements a 3-state finite state machine:
 * - CLOSED: Normal operation. Outbound cellular messages dispatch freely.
 * - OPEN: Tripped. Outbound cellular transmissions are halted for 15 minutes (900,000ms).
 *   Messages remain safely queued in SQLite Outbox without dropping.
 * - HALF_OPEN: Canary probe state reached when the 15-minute cooldown elapses. Permits a single
 *   test transmission. If confirmed via Activity.RESULT_OK, circuit resets to CLOSED.
 *   If the probe fails, the circuit immediately trips back to OPEN for another 15 minutes.
 *
 * State is persistently stored in SharedPreferences to ensure that the 15-minute cooldown
 * cannot be bypassed by process termination or OS Low Memory Killer (LMK) cycles.
 */
class CarrierCooldownCircuitBreaker private constructor(context: Context) {

    enum class CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default)

    private var tickerJob: Job? = null

    // Reactive State Flows
    private val _state = MutableStateFlow(CircuitState.CLOSED)
    val state: StateFlow<CircuitState> = _state.asStateFlow()

    private val _cooldownRemainingMs = MutableStateFlow(0L)
    val cooldownRemainingMs: StateFlow<Long> = _cooldownRemainingMs.asStateFlow()

    private val _consecutiveFailures = MutableStateFlow(0)
    val consecutiveFailures: StateFlow<Int> = _consecutiveFailures.asStateFlow()

    private val _lastFailureReason = MutableStateFlow<String?>(null)
    val lastFailureReason: StateFlow<String?> = _lastFailureReason.asStateFlow()

    private val _lastResultCode = MutableStateFlow(-1)
    val lastResultCode: StateFlow<Int> = _lastResultCode.asStateFlow()

    private val _trippedAtMs = MutableStateFlow(0L)
    val trippedAtMs: StateFlow<Long> = _trippedAtMs.asStateFlow()

    @Volatile
    private var cooldownDurationMs: Long = DEFAULT_COOLDOWN_MS

    init {
        restoreFromPersistence()
        startTicker()
    }

    /**
     * Restores state from SharedPreferences and computes remaining cooldown against wall-clock time.
     */
    @Synchronized
    private fun restoreFromPersistence() {
        val savedStateStr = prefs.getString(KEY_CIRCUIT_STATE, CircuitState.CLOSED.name) ?: CircuitState.CLOSED.name
        val savedTrippedAt = prefs.getLong(KEY_TRIPPED_AT_MS, 0L)
        val savedDuration = prefs.getLong(KEY_COOLDOWN_DURATION_MS, DEFAULT_COOLDOWN_MS)
        val savedFailures = prefs.getInt(KEY_CONSECUTIVE_FAILURES, 0)
        val savedReason = prefs.getString(KEY_LAST_FAILURE_REASON, null)
        val savedCode = prefs.getInt(KEY_LAST_RESULT_CODE, -1)

        cooldownDurationMs = savedDuration
        _consecutiveFailures.value = savedFailures
        _lastFailureReason.value = savedReason
        _lastResultCode.value = savedCode
        _trippedAtMs.value = savedTrippedAt

        val now = System.currentTimeMillis()
        if (savedStateStr == CircuitState.OPEN.name) {
            val elapsed = now - savedTrippedAt
            if (elapsed >= cooldownDurationMs) {
                // Cooldown already elapsed while app was killed -> Transition to HALF_OPEN
                Log.i(TAG, "Restored state: 15-minute cooldown elapsed while offline. Transitioning to HALF_OPEN probe.")
                _state.value = CircuitState.HALF_OPEN
                _cooldownRemainingMs.value = 0L
                persistState(CircuitState.HALF_OPEN)
            } else {
                val remaining = cooldownDurationMs - elapsed
                Log.w(TAG, "Restored state: Circuit is OPEN with ${remaining / 1000}s remaining in 15-minute cooldown.")
                _state.value = CircuitState.OPEN
                _cooldownRemainingMs.value = remaining
            }
        } else if (savedStateStr == CircuitState.HALF_OPEN.name) {
            _state.value = CircuitState.HALF_OPEN
            _cooldownRemainingMs.value = 0L
        } else {
            _state.value = CircuitState.CLOSED
            _cooldownRemainingMs.value = 0L
        }
    }

    /**
     * Determines whether outbound physical radio transmission is currently permitted.
     * - In CLOSED: True
     * - In HALF_OPEN: True (allows 1 canary probe)
     * - In OPEN: False (suppressed)
     */
    @Synchronized
    fun canTransmit(): Boolean {
        refreshState()
        return when (_state.value) {
            CircuitState.CLOSED -> true
            CircuitState.HALF_OPEN -> true
            CircuitState.OPEN -> false
        }
    }

    /**
     * Checks if cooldown has expired and updates state accordingly.
     */
    @Synchronized
    fun refreshState() {
        if (_state.value == CircuitState.OPEN) {
            val remaining = getCooldownRemainingMs()
            _cooldownRemainingMs.value = remaining
            if (remaining <= 0L) {
                Log.i(TAG, "15-minute cooldown completed. Advancing circuit breaker from OPEN to HALF_OPEN canary probe.")
                _state.value = CircuitState.HALF_OPEN
                persistState(CircuitState.HALF_OPEN)
            }
        }
    }

    /**
     * Records a successful physical transmission (Activity.RESULT_OK).
     * Resets failure counter to 0 and brings circuit back to CLOSED.
     */
    @Synchronized
    fun recordSuccess() {
        val previousState = _state.value
        _consecutiveFailures.value = 0
        _state.value = CircuitState.CLOSED
        _cooldownRemainingMs.value = 0L
        _lastFailureReason.value = null
        _lastResultCode.value = -1

        prefs.edit()
            .putString(KEY_CIRCUIT_STATE, CircuitState.CLOSED.name)
            .putInt(KEY_CONSECUTIVE_FAILURES, 0)
            .remove(KEY_LAST_FAILURE_REASON)
            .putInt(KEY_LAST_RESULT_CODE, -1)
            .putLong(KEY_LAST_SUCCESS_MS, System.currentTimeMillis())
            .apply()

        if (previousState != CircuitState.CLOSED) {
            Log.i(TAG, "Canary transmission confirmed by carrier! Circuit breaker reset from $previousState to CLOSED.")
        }
    }

    /**
     * Records a physical radio transmission failure.
     * Evaluates whether to trip the 15-minute circuit breaker:
     * 1. If in HALF_OPEN (canary probe failed): Trips immediately back to OPEN for 15 minutes.
     * 2. If resultCode indicates hard carrier throttling (RESULT_ERROR_LIMIT_EXCEEDED, code 5): Trips immediately.
     * 3. If consecutive failures reach MAX_CONSECUTIVE_FAILURES (3): Trips to OPEN.
     */
    @Synchronized
    fun recordFailure(resultCode: Int, reason: String = "") {
        val newFailureCount = _consecutiveFailures.value + 1
        _consecutiveFailures.value = newFailureCount
        _lastResultCode.value = resultCode

        val resolvedReason = when {
            reason.isNotBlank() -> reason
            resultCode == SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Carrier limit exceeded (Code 5)"
            resultCode == SmsManager.RESULT_ERROR_RADIO_OFF -> "Cellular radio off (Code 2)"
            resultCode == SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service (Code 4)"
            resultCode == SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU error (Code 3)"
            resultCode == SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic carrier failure (Code 1)"
            else -> "Radio transmission failed (Code $resultCode)"
        }
        _lastFailureReason.value = resolvedReason

        prefs.edit()
            .putInt(KEY_CONSECUTIVE_FAILURES, newFailureCount)
            .putString(KEY_LAST_FAILURE_REASON, resolvedReason)
            .putInt(KEY_LAST_RESULT_CODE, resultCode)
            .apply()

        Log.w(TAG, "Transmission failure recorded: $resolvedReason (Consecutive failures: $newFailureCount)")

        // Trip evaluation
        if (_state.value == CircuitState.HALF_OPEN) {
            // Canary probe failed -> Re-trip for another full 15 minutes
            tripBreaker(
                reason = "Canary probe failed: $resolvedReason",
                resultCode = resultCode
            )
        } else if (resultCode == SmsManager.RESULT_ERROR_LIMIT_EXCEEDED) {
            // Carrier explicitly returned limit exceeded -> Immediate 15-minute cooldown
            tripBreaker(
                reason = "Hard carrier throttle: $resolvedReason",
                resultCode = resultCode
            )
        } else if (newFailureCount >= MAX_CONSECUTIVE_FAILURES) {
            // Consecutive failure threshold reached -> Trip for 15 minutes
            tripBreaker(
                reason = "Threshold exceeded ($newFailureCount/$MAX_CONSECUTIVE_FAILURES): $resolvedReason",
                resultCode = resultCode
            )
        }
    }

    /**
     * Trips the circuit breaker into OPEN state for the 15-minute cooldown.
     */
    @Synchronized
    fun tripBreaker(reason: String, resultCode: Int = -1, customDurationMs: Long? = null) {
        val duration = customDurationMs ?: DEFAULT_COOLDOWN_MS
        cooldownDurationMs = duration
        val now = System.currentTimeMillis()

        _state.value = CircuitState.OPEN
        _trippedAtMs.value = now
        _cooldownRemainingMs.value = duration
        _lastFailureReason.value = reason
        if (resultCode != -1) _lastResultCode.value = resultCode

        prefs.edit()
            .putString(KEY_CIRCUIT_STATE, CircuitState.OPEN.name)
            .putLong(KEY_TRIPPED_AT_MS, now)
            .putLong(KEY_COOLDOWN_DURATION_MS, duration)
            .putString(KEY_LAST_FAILURE_REASON, reason)
            .putInt(KEY_LAST_RESULT_CODE, _lastResultCode.value)
            .apply()

        Log.e(TAG, "[CIRCUIT BREAKER TRIPPED] Reason: '$reason'. Outbound cellular transmissions suspended for ${duration / 60000} minutes.")
    }

    /**
     * Manually advances circuit to HALF_OPEN (permits 1 canary probe message).
     */
    @Synchronized
    fun transitionToHalfOpen() {
        _state.value = CircuitState.HALF_OPEN
        _cooldownRemainingMs.value = 0L
        persistState(CircuitState.HALF_OPEN)
        Log.i(TAG, "Circuit breaker transitioned to HALF_OPEN canary state.")
    }

    /**
     * Administrative/Manual user override to clear cooldown and reset to normal operation.
     */
    @Synchronized
    fun forceReset() {
        _state.value = CircuitState.CLOSED
        _consecutiveFailures.value = 0
        _cooldownRemainingMs.value = 0L
        _lastFailureReason.value = null
        _lastResultCode.value = -1
        _trippedAtMs.value = 0L

        prefs.edit()
            .putString(KEY_CIRCUIT_STATE, CircuitState.CLOSED.name)
            .putInt(KEY_CONSECUTIVE_FAILURES, 0)
            .putLong(KEY_TRIPPED_AT_MS, 0L)
            .remove(KEY_LAST_FAILURE_REASON)
            .putInt(KEY_LAST_RESULT_CODE, -1)
            .apply()

        Log.i(TAG, "Circuit breaker manually reset by user to CLOSED.")
    }

    /**
     * Administrative/Manual trigger to force a line probe test.
     */
    @Synchronized
    fun forceProbe() {
        transitionToHalfOpen()
    }

    /**
     * Calculates milliseconds remaining in the current cooldown window.
     */
    fun getCooldownRemainingMs(): Long {
        if (_state.value != CircuitState.OPEN) return 0L
        val tripped = _trippedAtMs.value
        val now = System.currentTimeMillis()
        val elapsed = now - tripped
        return max(0L, cooldownDurationMs - elapsed)
    }

    /**
     * Formats remaining time into human-readable "MM:SS" (e.g. "14:35").
     */
    fun getFormattedRemainingTime(): String {
        val totalSecs = max(0L, _cooldownRemainingMs.value / 1000L)
        val mins = totalSecs / 60L
        val secs = totalSecs % 60L
        return String.format("%02d:%02d", mins, secs)
    }

    /**
     * Sets a custom cooldown duration (used for tests or rapid verification).
     */
    fun setCustomCooldownDuration(ms: Long) {
        cooldownDurationMs = ms
    }

    private fun persistState(state: CircuitState) {
        prefs.edit()
            .putString(KEY_CIRCUIT_STATE, state.name)
            .apply()
    }

    /**
     * Starts background ticker to decrement remaining cooldown seconds and auto-advance to HALF_OPEN.
     */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                if (_state.value == CircuitState.OPEN) {
                    val remaining = getCooldownRemainingMs()
                    _cooldownRemainingMs.value = remaining
                    if (remaining <= 0L) {
                        transitionToHalfOpen()
                    }
                }
                delay(1000L)
            }
        }
    }

    companion object {
        const val TAG = "CarrierCircuitBreaker"
        const val PREFS_NAME = "carrier_circuit_breaker_prefs"

        // Production default: 15 minutes (900,000 milliseconds)
        const val DEFAULT_COOLDOWN_MS = 15 * 60 * 1000L

        // Maximum consecutive physical transmission failures before tripping
        const val MAX_CONSECUTIVE_FAILURES = 3

        private const val KEY_CIRCUIT_STATE = "circuit_state"
        private const val KEY_TRIPPED_AT_MS = "tripped_at_ms"
        private const val KEY_COOLDOWN_DURATION_MS = "cooldown_duration_ms"
        private const val KEY_CONSECUTIVE_FAILURES = "consecutive_failures"
        private const val KEY_LAST_FAILURE_REASON = "last_failure_reason"
        private const val KEY_LAST_RESULT_CODE = "last_result_code"
        private const val KEY_LAST_SUCCESS_MS = "last_success_ms"

        @Volatile
        private var instance: CarrierCooldownCircuitBreaker? = null

        fun getInstance(context: Context): CarrierCooldownCircuitBreaker {
            return instance ?: synchronized(this) {
                instance ?: CarrierCooldownCircuitBreaker(context.applicationContext).also { instance = it }
            }
        }
    }
}
