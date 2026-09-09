package com.cellular.rpc.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MutationLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mutation: MutationLogEntity)

    @Query("SELECT * FROM mutation_logs ORDER BY timestamp DESC")
    fun getAllMutationsFlow(): Flow<List<MutationLogEntity>>

    @Query("SELECT * FROM mutation_logs ORDER BY timestamp DESC")
    suspend fun getAllMutations(): List<MutationLogEntity>

    @Query("SELECT * FROM mutation_logs WHERE stabilityStatus = :status ORDER BY timestamp DESC")
    suspend fun getMutationsByStatus(status: String): List<MutationLogEntity>

    @Query("UPDATE mutation_logs SET stabilityStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM mutation_logs")
    suspend fun clearAll()
}
