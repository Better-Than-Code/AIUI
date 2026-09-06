package com.cellular.rpc.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted representation of a dynamically deployed cellular micro-app / feature.
 *
 * Received over cellular SMS via [APP:BUILD:<feature_id>] and compiled into
 * a reactive Jetpack Compose AST layout and sandboxed JavaScript business logic.
 */
@Entity(tableName = "dynamic_features")
data class DynamicFeatureEntity(
    @PrimaryKey
    val featureId: String,
    val title: String,
    val version: String,
    val description: String = "",
    val iconName: String = "extension",
    @ColumnInfo(name = "initial_state_json")
    val initialStateJson: String,
    @ColumnInfo(name = "current_state_json")
    val currentStateJson: String,
    @ColumnInfo(name = "ui_ast_json")
    val uiAstJson: String,
    @ColumnInfo(name = "js_logic")
    val jsLogic: String,
    val installedAtMs: Long = System.currentTimeMillis(),
    val lastUpdatedMs: Long = System.currentTimeMillis(),
    val executionCount: Int = 0,
    val isEnabled: Boolean = true
)
