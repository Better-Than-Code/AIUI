package com.cellular.rpc.domain.miniapp

import android.content.Context
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.MiniAppDocumentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Atomic State Mutex & LMK Lifecycle Checkpointer.
 * 1. Serializes rapid consecutive user taps (e.g. rapid counter increments) per appId
 *    to prevent coroutine race conditions and asynchronous state write overwrites.
 * 2. Provides immediate persistence to Room SQLite across onPause() / onStop() transitions
 *    to prevent form amnesia when Android Low Memory Killer (LMK) kills the process.
 */
object AtomicStateMutex {
    private const val TAG = "AtomicStateMutex"

    // Per-app Mutex to ensure linear FIFO action execution
    private val appMutexes = ConcurrentHashMap<String, Mutex>()

    // In-memory hot state checkpoint cache
    private val activeStateSnapshots = ConcurrentHashMap<String, MutableMap<String, Any?>>()

    private fun getMutexForApp(appId: String): Mutex {
        return appMutexes.computeIfAbsent(appId) { Mutex() }
    }

    /**
     * Executes a state transition atomically under the app's dedicated Mutex.
     */
    suspend fun <T> withAtomicState(appId: String, action: suspend () -> T): T {
        val mutex = getMutexForApp(appId)
        return mutex.withLock {
            action()
        }
    }

    /**
     * Updates the in-memory checkpoint for an active mini-app.
     */
    fun checkpointState(appId: String, state: Map<String, Any?>) {
        val current = activeStateSnapshots.computeIfAbsent(appId) { ConcurrentHashMap() }
        current.clear()
        current.putAll(state)
    }

    /**
     * Retrieves the last cached in-memory state checkpoint for an app.
     */
    fun getCheckpointedState(appId: String): Map<String, Any?> {
        return activeStateSnapshots[appId]?.toMap() ?: emptyMap()
    }

    /**
     * Persists all active in-memory form checkpoints directly to Room SQLite
     * during lifecycle onPause() / onStop() to survive LMK process terminations.
     */
    suspend fun flushCheckpointsToDisk(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        if (activeStateSnapshots.isEmpty()) return

        try {
            val dao = AppDatabase.getInstance(context).miniAppDocumentDao()
            for ((appId, stateMap) in activeStateSnapshots) {
                if (stateMap.isNotEmpty()) {
                    val jsonStr = JSONObject(stateMap).toString()
                    dao.upsertDocument(
                        MiniAppDocumentEntity(
                            appId = appId,
                            collection = "_checkpoints",
                            docId = "active_state",
                            jsonPayload = jsonStr
                        )
                    )
                }
            }
            Log.d(TAG, "Successfully flushed ${activeStateSnapshots.size} app checkpoints to disk.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush state checkpoints to disk: ${e.message}")
        }
    }

    /**
     * Restores checkpointed state for an appId from SQLite if in-memory cache is empty.
     */
    suspend fun restoreCheckpointedState(context: Context, appId: String): Map<String, Any?> {
        val inMemory = getCheckpointedState(appId)
        if (inMemory.isNotEmpty()) return inMemory

        return try {
            val dao = AppDatabase.getInstance(context).miniAppDocumentDao()
            val doc = dao.getDocument(appId = appId, collection = "_checkpoints", docId = "active_state")
            if (doc != null) {
                val json = JSONObject(doc.jsonPayload)
                val map = mutableMapOf<String, Any?>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.opt(key)
                }
                activeStateSnapshots[appId] = ConcurrentHashMap(map)
                map
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore checkpoint for $appId: ${e.message}")
            emptyMap()
        }
    }
}
