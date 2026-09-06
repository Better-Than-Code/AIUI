package com.cellular.rpc.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetCacheDao {

    @Query("SELECT * FROM widget_cache")
    fun getAllWidgetsFlow(): Flow<List<WidgetCacheEntity>>

    @Query("SELECT * FROM widget_cache WHERE widgetType = :widgetType LIMIT 1")
    suspend fun getWidgetByType(widgetType: String): WidgetCacheEntity?

    @Query("SELECT * FROM widget_cache WHERE widgetType = :widgetType LIMIT 1")
    fun getWidgetFlowByType(widgetType: String): Flow<WidgetCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: WidgetCacheEntity)

    @Query("UPDATE widget_cache SET lastStatus = :status, lastUpdatedMs = :timestamp WHERE widgetType = :widgetType")
    suspend fun updateStatus(widgetType: String, status: String, timestamp: Long)

    @Query("DELETE FROM widget_cache")
    suspend fun clearCache()
}
