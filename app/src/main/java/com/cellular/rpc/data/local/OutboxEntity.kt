package com.cellular.rpc.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Int,
    val seqNo: Int,
    val pktType: Byte,
    val payloadBase85: String,
    val rawPayloadHex: String,
    val status: String = STATUS_PENDING, // PENDING, IN_FLIGHT, ACKNOWLEDGED, FAILED
    val retries: Int = 0,
    val lastAttemptMs: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_FLIGHT = "IN_FLIGHT"
        const val STATUS_ACKNOWLEDGED = "ACKNOWLEDGED"
        const val STATUS_FAILED = "FAILED"
    }
}
