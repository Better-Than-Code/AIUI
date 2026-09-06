package com.cellular.rpc.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DynamicFeatureDao {

    @Query("SELECT * FROM dynamic_features ORDER BY lastUpdatedMs DESC")
    fun getAllFeaturesFlow(): Flow<List<DynamicFeatureEntity>>

    @Query("SELECT * FROM dynamic_features WHERE isEnabled = 1 ORDER BY lastUpdatedMs DESC")
    fun getEnabledFeaturesFlow(): Flow<List<DynamicFeatureEntity>>

    @Query("SELECT * FROM dynamic_features WHERE featureId = :featureId LIMIT 1")
    suspend fun getFeatureById(featureId: String): DynamicFeatureEntity?

    @Query("SELECT * FROM dynamic_features WHERE featureId = :featureId LIMIT 1")
    fun getFeatureFlow(featureId: String): Flow<DynamicFeatureEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(feature: DynamicFeatureEntity)

    @Update
    suspend fun update(feature: DynamicFeatureEntity)

    @Query("UPDATE dynamic_features SET current_state_json = :stateJson, lastUpdatedMs = :timestamp, executionCount = executionCount + 1 WHERE featureId = :featureId")
    suspend fun updateState(featureId: String, stateJson: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM dynamic_features WHERE featureId = :featureId")
    suspend fun deleteFeature(featureId: String)

    @Query("DELETE FROM dynamic_features")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM dynamic_features")
    suspend fun getCount(): Int
}
