package com.cellular.rpc.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "calendar_events",
    indices = [Index(value = ["startTimeMs"])]
)
data class CalendarEventEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isAllDay: Boolean = false,
    val colorHex: String? = null,
    val recurrenceRule: String? = null,
    val externalEventId: String? = null, // ID from Android CalendarProvider if synced
    val createdAtMs: Long = System.currentTimeMillis()
)

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY startTimeMs ASC")
    fun getAllEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE startTimeMs >= :startMs AND startTimeMs <= :endMs ORDER BY startTimeMs ASC")
    fun getEventsInRange(startMs: Long, endMs: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity)

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    @Delete
    suspend fun deleteEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEventById(id: String)
}
