package com.cellular.rpc.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MiniAppDocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(doc: MiniAppDocumentEntity)

    @Query("SELECT * FROM mini_app_documents WHERE appId = :appId AND collection = :collection ORDER BY updatedAt DESC")
    fun observeCollection(appId: String, collection: String): Flow<List<MiniAppDocumentEntity>>

    @Query("SELECT * FROM mini_app_documents WHERE appId = :appId AND collection = :collection ORDER BY updatedAt DESC")
    suspend fun getCollection(appId: String, collection: String): List<MiniAppDocumentEntity>

    @Query("SELECT * FROM mini_app_documents WHERE appId = :appId AND collection = :collection AND docId = :docId LIMIT 1")
    suspend fun getDocument(appId: String, collection: String, docId: String): MiniAppDocumentEntity?

    @Query("DELETE FROM mini_app_documents WHERE appId = :appId AND collection = :collection AND docId = :docId")
    suspend fun deleteDocument(appId: String, collection: String, docId: String)

    @Query("DELETE FROM mini_app_documents WHERE appId = :appId AND collection = :collection")
    suspend fun clearCollection(appId: String, collection: String)

    @Query("DELETE FROM mini_app_documents WHERE appId = :appId")
    suspend fun clearApp(appId: String)
}
