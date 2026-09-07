package com.cellular.rpc.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Persisted custom instant cellular AI action / quick prompt.
 */
@Entity(tableName = "custom_instant_actions")
data class CustomActionEntity(
    @PrimaryKey
    val id: String,
    val label: String,
    val prompt: String,
    val type: String,
    val iconName: String = "bolt",
    val colorHex: String = "#00E5FF",
    val isPreset: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Dao
interface CustomActionDao {

    @Query("SELECT * FROM custom_instant_actions ORDER BY isPreset DESC, createdAtMs ASC")
    fun getAllActionsFlow(): Flow<List<CustomActionEntity>>

    @Query("SELECT * FROM custom_instant_actions ORDER BY isPreset DESC, createdAtMs ASC")
    suspend fun getAllActions(): List<CustomActionEntity>

    @Query("SELECT * FROM custom_instant_actions WHERE id = :id LIMIT 1")
    suspend fun getActionById(id: String): CustomActionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: CustomActionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(actions: List<CustomActionEntity>)

    @Query("DELETE FROM custom_instant_actions WHERE id = :id")
    suspend fun deleteAction(id: String)

    @Query("SELECT COUNT(*) FROM custom_instant_actions")
    suspend fun getCount(): Int
}
