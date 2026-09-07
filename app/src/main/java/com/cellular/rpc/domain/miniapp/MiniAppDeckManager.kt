package com.cellular.rpc.domain.miniapp

import android.content.Context
import android.util.Log
import com.cellular.rpc.data.local.AppBlueprintDao
import com.cellular.rpc.data.local.AppBlueprintEntity
import com.cellular.rpc.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * Universal Mini App Deck Manager.
 * Governs installation, retrieval, state preservation, and uninstallation of mini apps in the Deck.
 */
object MiniAppDeckManager {
    private const val TAG = "MiniAppDeckManager"

    private fun getDao(context: Context): AppBlueprintDao {
        return AppDatabase.getInstance(context).appBlueprintDao()
    }

    fun getAllInstalledAppsFlow(context: Context): Flow<List<AppBlueprintEntity>> {
        return getDao(context).getAllInstalledAppsFlow()
    }

    suspend fun getApp(context: Context, appId: String): AppBlueprintEntity? {
        return getDao(context).getAppById(appId)
    }

    suspend fun installApp(
        context: Context,
        blueprint: MiniAppBlueprint,
        currentState: Map<String, Any?>
    ): AppBlueprintEntity {
        val stateJson = JSONObject(currentState).toString()
        val entity = AppBlueprintEntity(
            appId = blueprint.appId,
            version = blueprint.version,
            title = blueprint.metadata.title,
            icon = blueprint.metadata.icon,
            description = blueprint.metadata.description,
            category = blueprint.metadata.category,
            rawBlueprintJson = blueprint.rawJson.ifBlank { blueprintToJson(blueprint) },
            serializedStateJson = stateJson,
            installedAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )
        getDao(context).installOrUpdate(entity)
        Log.i(TAG, "Successfully installed mini-app [${blueprint.appId}] to Deck: ${blueprint.metadata.title}")
        return entity
    }

    suspend fun updateState(context: Context, appId: String, newState: Map<String, Any?>) {
        val stateJson = JSONObject(newState).toString()
        getDao(context).updateAppState(appId, stateJson)
    }

    suspend fun uninstallApp(context: Context, appId: String) {
        getDao(context).deleteApp(appId)
        Log.i(TAG, "Uninstalled mini-app [$appId] from Deck")
    }

    private fun blueprintToJson(blueprint: MiniAppBlueprint): String {
        return try {
            JSONObject().apply {
                put("type", blueprint.type)
                put("appId", blueprint.appId)
                put("version", blueprint.version)
                put("metadata", JSONObject().apply {
                    put("title", blueprint.metadata.title)
                    put("icon", blueprint.metadata.icon)
                    put("description", blueprint.metadata.description)
                    put("category", blueprint.metadata.category)
                })
                put("initialState", JSONObject(blueprint.initialState))
            }.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    suspend fun seedSampleMiniAppsIfEmpty(context: Context) {
        try {
            val count = getDao(context).getInstalledCount()
            if (count > 0) return

            // Seed Sprint Task Checklist v1
            val taskChecklistJson = """
            {
              "type": "mini_app_blueprint",
              "appId": "task_checklist_v1",
              "version": 1,
              "metadata": {
                "title": "Sprint Task Checklist",
                "icon": "checklist",
                "description": "Offline task tracker with instant toggle",
                "category": "productivity"
              },
              "initialState": {
                "title": "Deploy Checklist",
                "items": [
                  {"id": "1", "text": "Enforce single-PDU budget", "done": true},
                  {"id": "2", "text": "Register abortBroadcast", "done": true},
                  {"id": "3", "text": "Reconcile outbox on RESULT_OK", "done": true},
                  {"id": "4", "text": "Test SDUI preview install", "done": false}
                ],
                "inputDraft": ""
              },
              "ui": {
                "type": "Card",
                "padding": 16,
                "children": [
                  {
                    "type": "Row",
                    "align": "SpaceBetween",
                    "children": [
                      {"type": "Text", "style": "TitleMedium", "bind": "title"},
                      {"type": "Badge", "text": "Offline Ready"}
                    ]
                  },
                  {
                    "type": "Divider",
                    "spacing": 8
                  },
                  {
                    "type": "List",
                    "bindItems": "items",
                    "itemTemplate": {
                      "type": "Row",
                      "align": "Center",
                      "children": [
                        {
                          "type": "Checkbox",
                          "bindChecked": "${'$'}item.done",
                          "onToggle": {
                            "action": "MUTATE_STATE",
                            "target": "items",
                            "op": "TOGGLE_PROP",
                            "id": "${'$'}item.id",
                            "prop": "done"
                          }
                        },
                        {
                          "type": "Text",
                          "bind": "${'$'}item.text",
                          "strikethroughWhen": "${'$'}item.done",
                          "modifier": {"weight": 1.0}
                        }
                      ]
                    }
                  },
                  {
                    "type": "Row",
                    "spacing": 8,
                    "children": [
                      {
                        "type": "TextField",
                        "hint": "Add new task...",
                        "bindValue": "inputDraft",
                        "modifier": {"weight": 1.0}
                      },
                      {
                        "type": "IconButton",
                        "icon": "add",
                        "onClick": {
                          "action": "MUTATE_STATE",
                          "target": "items",
                          "op": "APPEND",
                          "value": {"id": "${'$'}uuid", "text": "${'$'}state.inputDraft", "done": false},
                          "clearAfter": "inputDraft"
                        }
                      }
                    ]
                  },
                  {
                    "type": "InstallFooter",
                    "showInPreviewOnly": true,
                    "actions": {
                      "onInstall": {
                        "action": "INSTALL_APP",
                        "appId": "task_checklist_v1"
                      },
                      "onDiscard": {
                        "action": "DISCARD_PREVIEW",
                        "appId": "task_checklist_v1"
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent()

            val blueprint = MiniAppBlueprint.fromJson(taskChecklistJson)
            if (blueprint != null) {
                installApp(context, blueprint, blueprint.initialState)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding sample mini apps: ${e.message}", e)
        }
    }
}
