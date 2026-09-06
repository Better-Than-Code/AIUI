package com.cellular.rpc.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widget_cache")
data class WidgetCacheEntity(
    @PrimaryKey
    val widgetType: String, // "weather", "news_digest", "market_ticker"
    val contentHash: String, // e.g. SHA-256 or hex digest for ETag/304 check
    val jsonPayload: String, // minified JSON payload conforming to schema
    val lastStatus: String = "200_OK", // "200_OK", "304_NOT_MODIFIED"
    val byteSize: Int = 0,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)
