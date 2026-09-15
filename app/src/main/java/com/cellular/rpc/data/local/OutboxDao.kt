package com.cellular.rpc.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox ORDER BY createdAtMs ASC")
    fun getAllFlow(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE status = 'PENDING' OR status = 'IN_FLIGHT' ORDER BY createdAtMs ASC")
    fun getActiveQueueFlow(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE status = :status ORDER BY seqNo ASC")
    suspend fun getByStatus(status: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAtMs ASC LIMIT :limit")
    suspend fun getPendingFrames(limit: Int = 10): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE status = 'IN_FLIGHT' AND lastAttemptMs < :staleThresholdMs")
    suspend fun getStalledInFlightFrames(staleThresholdMs: Long): List<OutboxEntity>

    @Query("UPDATE outbox SET status = CASE WHEN retries >= 3 THEN 'FAILED' ELSE 'PENDING' END, retries = retries + 1 WHERE status = 'IN_FLIGHT' AND lastAttemptMs < :staleThresholdMs")
    suspend fun resetStalledToPending(staleThresholdMs: Long): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING' OR status = 'IN_FLIGHT'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING' OR status = 'IN_FLIGHT'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE sessionId = :sessionId AND pktType = :pktType AND payloadBase85 = :payloadBase85 AND (status = 'PENDING' OR status = 'IN_FLIGHT')")
    suspend fun countDuplicatePending(sessionId: Int, pktType: Byte, payloadBase85: String): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE payloadBase85 = :payloadBase85 AND (status = 'PENDING' OR status = 'IN_FLIGHT')")
    suspend fun countDuplicatePayloadPending(payloadBase85: String): Int

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun getById(id: Long): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE sessionId = :sessionId AND seqNo = :seqNo LIMIT 1")
    suspend fun getBySessionAndSeq(sessionId: Int, seqNo: Int): OutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(frame: OutboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(frames: List<OutboxEntity>): List<Long>

    @Update
    suspend fun update(frame: OutboxEntity)

    @Query("UPDATE outbox SET status = :newStatus, lastAttemptMs = :timestamp, retries = retries + 1 WHERE id = :id")
    suspend fun markAttempted(id: Long, newStatus: String, timestamp: Long)

    @Query("UPDATE outbox SET status = :newStatus, seqNo = :seqNo, lastAttemptMs = :timestamp, retries = retries + 1 WHERE id = :id")
    suspend fun markAttemptedWithSeq(id: Long, newStatus: String, seqNo: Int, timestamp: Long)

    @Query("UPDATE outbox SET status = 'FAILED', lastAttemptMs = :timestamp WHERE id = :id")
    suspend fun markFailed(id: Long, timestamp: Long)

    @Query("UPDATE outbox SET status = 'FAILED', lastAttemptMs = :timestamp WHERE sessionId = :sessionId AND seqNo = :seqNo")
    suspend fun markFailedBySeq(sessionId: Int, seqNo: Int, timestamp: Long)

    @Query("UPDATE outbox SET status = 'ACKNOWLEDGED' WHERE sessionId = :sessionId AND seqNo = :seqNo")
    suspend fun markAcknowledged(sessionId: Int, seqNo: Int)

    @Query("UPDATE outbox SET status = 'ACKNOWLEDGED' WHERE (sessionId = :sessionId OR :sessionId = 0) AND (seqNo = :seqNo OR seqNo = :seqNo - 1)")
    suspend fun markAcknowledgedFlexible(sessionId: Int, seqNo: Int): Int

    @Query("UPDATE outbox SET status = 'ACKNOWLEDGED' WHERE sessionId = :sessionId")
    suspend fun markSessionAcknowledged(sessionId: Int): Int

    @Query("UPDATE outbox SET status = 'ACKNOWLEDGED' WHERE status = 'IN_FLIGHT'")
    suspend fun markAllInFlightAcknowledged(): Int

    @Query("UPDATE outbox SET status = 'ACKNOWLEDGED' WHERE id = :id")
    suspend fun markAcknowledgedById(id: Long)

    @Query("DELETE FROM outbox WHERE sessionId = :sessionId AND seqNo = :seqNo")
    suspend fun deleteBySeq(sessionId: Int, seqNo: Int)

    @Query("DELETE FROM outbox WHERE status = 'ACKNOWLEDGED'")
    suspend fun clearAcknowledged()

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'IN_FLIGHT'")
    suspend fun countInFlight(): Int

    @Query("DELETE FROM outbox")
    suspend fun clearAll()
}
