package com.cellular.rpc.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val sender: String, // "USER" or "AI_GATEWAY"
    val text: String,
    val widgetDataJson: String? = null,
    val is304NotModified: Boolean = false,
    val wirePacket: String? = null,
    val byteSize: Int = 0,
    val pduCount: Int = 1,
    val deliveryStatus: String = "DELIVERED", // "QUEUED", "IN_FLIGHT", "DELIVERED"
    val timestampMs: Long = System.currentTimeMillis(),
    // Embedded Attachment fields
    val attachmentId: String? = null,
    val attachmentType: String? = null, // "IMAGE", "FILE", "VOICE_NOTE"
    val attachmentUri: String? = null,
    val attachmentFileName: String? = null,
    val attachmentSizeBytes: Long = 0,
    val attachmentMimeType: String? = null,
    val attachmentDurationMs: Long = 0,
    val attachmentAmplitudes: String? = null // Comma-separated floats
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestampMs ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE text = :text AND timestampMs >= :sinceMs")
    suspend fun countRecentMatchingMessages(text: String, sinceMs: Long): Int

    @Query("SELECT * FROM chat_messages ORDER BY timestampMs ASC")
    suspend fun getAllMessagesList(): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
