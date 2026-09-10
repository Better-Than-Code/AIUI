package com.cellular.rpc.engine

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watchdog Rollback & Safe-Boot Engine.
 * 
 * Intercepts fatal uncaught UI exceptions, tracks stable vs staged configurations
 * (themes, blueprints, AST layouts), and automatically rolls back unverified patches
 * if a fault or crash occurs within a 5-second health verification window.
 * 
 * Provides an emergency zero-dependency hardcoded fallback chat mode if catastrophic
 * cascading crashes occur.
 */
object WatchdogSafeBootManager {
    private const val TAG = "WatchdogSafeBoot"
    private const val PREFS_NAME = "cellular_watchdog_prefs"
    private const val KEY_STABLE_THEME = "config_theme_stable"
    private const val KEY_STAGED_THEME = "config_theme_staged"
    private const val KEY_IS_STAGED = "is_staged_active"
    private const val KEY_CONSECUTIVE_CRASHES = "consecutive_crashes"
    private const val STABLE_HEALTH_WINDOW_MS = 5000L

    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var prefs: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var healthCommitRunnable: Runnable? = null

    private val _isFallbackModeActive = MutableStateFlow(false)
    val isFallbackModeActive: StateFlow<Boolean> = _isFallbackModeActive.asStateFlow()

    private var onRollbackListener: (() -> Unit)? = null

    fun initialize(context: Context, onRollback: (() -> Unit)? = null) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        onRollbackListener = onRollback

        // Check crash counter
        val crashes = prefs?.getInt(KEY_CONSECUTIVE_CRASHES, 0) ?: 0
        if (crashes >= 3) {
            Log.e(TAG, "Consecutive crash count ($crashes) exceeds safety threshold. Activating Emergency Fallback Mode!")
            _isFallbackModeActive.value = true
        }

        // If a staged boot was active when process died, rollback immediately
        if (prefs?.getBoolean(KEY_IS_STAGED, false) == true) {
            Log.w(TAG, "Uncommitted staged state detected on boot! Rolling back to last stable configuration.")
            rollbackToStable(context, "Process terminated during staged boot")
        }

        // Install uncaught exception handler
        if (defaultExceptionHandler == null) {
            defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                handleUncaughtException(context, thread, throwable)
            }
        }
    }

    private fun handleUncaughtException(context: Context, thread: Thread, throwable: Throwable) {
        val isStaged = prefs?.getBoolean(KEY_IS_STAGED, false) == true
        val currentCrashes = (prefs?.getInt(KEY_CONSECUTIVE_CRASHES, 0) ?: 0) + 1
        prefs?.edit()?.putInt(KEY_CONSECUTIVE_CRASHES, currentCrashes)?.commit()

        Log.e(TAG, "CRITICAL: Uncaught exception in thread '${thread.name}': ${throwable.message}. Staged: $isStaged, Crash count: $currentCrashes")

        // Log fault to CellularMedic
        CellularMedic.onFaultDetected(
            context = context,
            componentName = "WatchdogSafeBootManager",
            faultDescription = "Uncaught Exception in ${thread.name}: ${throwable.message}",
            rawPayload = Log.getStackTraceString(throwable)
        )

        if (isStaged) {
            rollbackToStable(context, "Crash intercepted during staged state: ${throwable.message}")
        }

        if (currentCrashes >= 3) {
            _isFallbackModeActive.value = true
        }

        // Forward to default Android handler so OS crash lifecycle is respected
        defaultExceptionHandler?.uncaughtException(thread, throwable)
    }

    /**
     * Stages a newly received dynamic theme or blueprint. Starts a 5-second health window.
     */
    fun stageConfiguration(context: Context, themeJson: String) {
        healthCommitRunnable?.let { mainHandler.removeCallbacks(it) }

        prefs?.edit()
            ?.putString(KEY_STAGED_THEME, themeJson)
            ?.putBoolean(KEY_IS_STAGED, true)
            ?.apply()

        Log.i(TAG, "Staged new configuration. Starting ${STABLE_HEALTH_WINDOW_MS}ms health verification timer...")

        val commitTask = Runnable {
            commitStagedAsStable()
        }
        healthCommitRunnable = commitTask
        mainHandler.postDelayed(commitTask, STABLE_HEALTH_WINDOW_MS)
    }

    /**
     * Promotes staged configuration to stable once verified healthy.
     */
    fun commitStagedAsStable() {
        val staged = prefs?.getString(KEY_STAGED_THEME, null)
        if (staged != null) {
            prefs?.edit()
                ?.putString(KEY_STABLE_THEME, staged)
                ?.putBoolean(KEY_IS_STAGED, false)
                ?.putInt(KEY_CONSECUTIVE_CRASHES, 0)
                ?.apply()
            Log.i(TAG, "Health verification passed! Configuration promoted to stable.")
        }
        healthCommitRunnable = null
    }

    /**
     * Wipes staged state and reverts to stable configuration.
     */
    fun rollbackToStable(context: Context, reason: String) {
        healthCommitRunnable?.let { mainHandler.removeCallbacks(it) }
        healthCommitRunnable = null

        val stable = prefs?.getString(KEY_STABLE_THEME, null)
        prefs?.edit()
            ?.putBoolean(KEY_IS_STAGED, false)
            ?.remove(KEY_STAGED_THEME)
            ?.apply()

        Log.w(TAG, "Watchdog rollback executed: $reason. Restored stable theme.")
        onRollbackListener?.invoke()
    }

    fun getStableThemeJson(): String? {
        return prefs?.getString(KEY_STABLE_THEME, null)
    }

    fun resetEmergencyFallback() {
        prefs?.edit()?.putInt(KEY_CONSECUTIVE_CRASHES, 0)?.apply()
        _isFallbackModeActive.value = false
    }
}
