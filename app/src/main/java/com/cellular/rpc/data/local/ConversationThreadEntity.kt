package com.cellular.rpc.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversation_threads")
data class ConversationThreadEntity(
    @PrimaryKey
    val threadId: String,                      // e.g. "th_main", "th_abc123"
    val title: String,                         // e.g. "General Chat", "Tokyo Trip"
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val lastSnippet: String = "",              // Preview snippet for directory
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val unreadCount: Int = 0
)

@Dao
interface ConversationThreadDao {
    @Query("SELECT * FROM conversation_threads WHERE isArchived = 0 ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getActiveThreadsFlow(): Flow<List<ConversationThreadEntity>>

    @Query("SELECT * FROM conversation_threads ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getAllThreadsFlow(): Flow<List<ConversationThreadEntity>>

    @Query("SELECT * FROM conversation_threads WHERE threadId = :threadId LIMIT 1")
    suspend fun getThreadById(threadId: String): ConversationThreadEntity?

    @Query("SELECT * FROM conversation_threads WHERE threadId = :threadId LIMIT 1")
    fun getThreadFlow(threadId: String): Flow<ConversationThreadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(thread: ConversationThreadEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultThread(thread: ConversationThreadEntity)

    @Query("UPDATE conversation_threads SET title = :newTitle WHERE threadId = :threadId")
    suspend fun renameThread(threadId: String, newTitle: String)

    @Query("UPDATE conversation_threads SET isPinned = :isPinned WHERE threadId = :threadId")
    suspend fun setPinned(threadId: String, isPinned: Boolean)

    @Query("UPDATE conversation_threads SET isArchived = :isArchived WHERE threadId = :threadId")
    suspend fun setArchived(threadId: String, isArchived: Boolean)

    @Query("UPDATE conversation_threads SET lastSnippet = :snippet, lastMessageTimestamp = :timestamp WHERE threadId = :threadId")
    suspend fun updateLastMessage(threadId: String, snippet: String, timestamp: Long)

    @Query("UPDATE conversation_threads SET unreadCount = 0 WHERE threadId = :threadId")
    suspend fun markThreadRead(threadId: String)

    @Query("UPDATE conversation_threads SET unreadCount = unreadCount + 1 WHERE threadId = :threadId")
    suspend fun incrementUnread(threadId: String)

    @Query("DELETE FROM conversation_threads WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)
}
