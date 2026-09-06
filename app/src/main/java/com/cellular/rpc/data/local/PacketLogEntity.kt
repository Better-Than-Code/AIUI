package com.cellular.rpc.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "packet_logs")
data class PacketLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val direction: String, // "TX" (Outbound) or "RX" (Inbound)
    val sessionId: Int,
    val pktType: Byte,
    val pktTypeName: String,
    val seqNo: Int,
    val ackBitsHex: String,
    val payloadString: String,
    val wireFormat: String,
    val binaryByteCount: Int,
    val crc16Hex: String,
    val crcValid: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

@Dao
interface PacketLogDao {

    @Query("SELECT * FROM packet_logs ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 50): Flow<List<PacketLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PacketLogEntity): Long

    @Query("DELETE FROM packet_logs")
    suspend fun clearLogs()
}
