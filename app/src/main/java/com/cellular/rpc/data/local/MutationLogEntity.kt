package com.cellular.rpc.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mutation_logs")
data class MutationLogEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val triggerEvent: String,
    val patchApplied: String,
    val stabilityStatus: String
)
