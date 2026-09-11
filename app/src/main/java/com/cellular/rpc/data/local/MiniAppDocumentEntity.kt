package com.cellular.rpc.data.local

import androidx.room.Entity

@Entity(
    tableName = "mini_app_documents",
    primaryKeys = ["appId", "collection", "docId"]
)
data class MiniAppDocumentEntity(
    val appId: String,
    val collection: String,
    val docId: String,
    val jsonPayload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
