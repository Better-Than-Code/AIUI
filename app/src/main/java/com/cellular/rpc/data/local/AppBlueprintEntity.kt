package com.cellular.rpc.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Universal Mini App Persistence Entity in Room Database.
 * Holds the deterministic JSON blueprint and persistent JSON state for installed mini apps.
 */
@Entity(tableName = "installed_mini_apps")
data class AppBlueprintEntity(
    @PrimaryKey
    val appId: String,
    val version: Int = 1,
    val title: String,
    val icon: String = "checklist",
    val description: String = "",
    val category: String = "productivity",
    val rawBlueprintJson: String,
    val serializedStateJson: String,
    val installedAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface AppBlueprintDao {
    @Query("SELECT * FROM installed_mini_apps ORDER BY installedAt DESC")
    fun getAllInstalledAppsFlow(): Flow<List<AppBlueprintEntity>>

    @Query("SELECT * FROM installed_mini_apps WHERE appId = :appId LIMIT 1")
    suspend fun getAppById(appId: String): AppBlueprintEntity?

    @Query("SELECT * FROM installed_mini_apps WHERE appId = :appId LIMIT 1")
    fun getAppFlow(appId: String): Flow<AppBlueprintEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun installOrUpdate(app: AppBlueprintEntity)

    @Query("UPDATE installed_mini_apps SET serializedStateJson = :stateJson, lastUpdated = :timestamp WHERE appId = :appId")
    suspend fun updateAppState(appId: String, stateJson: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM installed_mini_apps WHERE appId = :appId")
    suspend fun deleteApp(appId: String)

    @Query("DELETE FROM installed_mini_apps")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM installed_mini_apps")
    suspend fun getInstalledCount(): Int
}
