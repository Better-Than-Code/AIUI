package com.cellular.rpc.orchestrator

data class AiTemplate(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val category: TemplateCategory,
    val schemaPromptPrefix: String,
    val defaultPrompt: String,
    val mockOfflinePayload: String
)

enum class TemplateCategory {
    MINI_APP,
    SDUI_WIDGET,
    DATA_TOOL
}

object OrchestratorTemplateCatalog {

    val TEMPLATES = listOf(
        AiTemplate(
            id = "tpl_tip_calculator",
            title = "Tip & Bill Splitter",
            description = "Calculates gratuity and splits bill evenly among attendees.",
            iconEmoji = "🧾",
            category = TemplateCategory.DATA_TOOL,
            schemaPromptPrefix = "[TEMPLATE:TIP_CALCULATOR]",
            defaultPrompt = "Split a $95 dinner bill between 3 people with 20% tip.",
            mockOfflinePayload = """
                {"type":"tool","id":"tip_calc","title":"Bill Splitter","subtitle":"Bill: $95.00 • 20% Gratuity","valuePrimary":"$114.00 Total","valueSecondary":"$38.00 / Person (3 guests)","actionLabel":"Recalculate"}
            """.trimIndent()
        ),
        AiTemplate(
            id = "tpl_habit_tracker",
            title = "Habit & Workout Counter",
            description = "Interactive counter mini-app with local state increment and target goals.",
            iconEmoji = "⚡",
            category = TemplateCategory.MINI_APP,
            schemaPromptPrefix = "[SCHEMA:MINIAPP] [TEMPLATE:COUNTER]",
            defaultPrompt = "Create a workout rep counter mini-app with 3 target sets.",
            mockOfflinePayload = """[APP:BUILD:habit_counter]{"title":"Workout Rep Counter","version":"1.0.0","description":"Tracks fitness sets and repetitions offline","icon":"fitness_center","state":{"reps":25,"sets":1},"ui":{"type":"Column","children":[{"type":"Card","title":"Push-Ups Set Tracker","subtitle":"Target: 4 Sets of 25 Reps"},{"type":"Text","text":"Current Completed Sets: 1"},{"type":"Button","label":"Log Completed Set","action":"state.sets += 1; bridge.notify('Workout', 'Set logged!'); bridge.commit(JSON.stringify(state));"}]},"js":"state.sets += 1; bridge.notify('Workout', 'Set logged!'); bridge.commit(JSON.stringify(state));"}"""
        ),
        AiTemplate(
            id = "tpl_metric_dashboard",
            title = "Live Metric Card",
            description = "Server-Driven UI card displaying key KPI metrics and status.",
            iconEmoji = "📊",
            category = TemplateCategory.SDUI_WIDGET,
            schemaPromptPrefix = "[SCHEMA:SDUI] [TEMPLATE:METRIC]",
            defaultPrompt = "Show server uptime, latency, and packet loss metrics.",
            mockOfflinePayload = """
                {
                    "type": "blueprint",
                    "title": "Cellular Gateway Status",
                    "description": "Real-time Node Telemetry",
                    "nodes": [
                        {"type": "metric", "title": "Carrier Latency", "value": "240 ms"},
                        {"type": "progress", "label": "Buffer Health", "progress": 0.92},
                        {"type": "badge", "text": "OPTIMAL", "colorHex": "#70EE9C"},
                        {"type": "key_value", "key": "Active Port", "value": "GSM +16462619684"}
                    ]
                }
            """.trimIndent()
        ),
        AiTemplate(
            id = "tpl_poll_voting",
            title = "Interactive Poll Card",
            description = "Multi-option voting poll with live tally and cellular response callback.",
            iconEmoji = "🗳️",
            category = TemplateCategory.SDUI_WIDGET,
            schemaPromptPrefix = "[TEMPLATE:POLL]",
            defaultPrompt = "Create a team poll: 'Which deployment target should we prioritize?'",
            mockOfflinePayload = """
                {"type":"poll","id":"poll_dep_01","question":"Which deployment target should we prioritize?","options":["Low-Orbit Sat Link","Direct Carrier SMS","Offline Mesh"],"votes":[7,12,4],"userVoteIndex":-1}
            """.trimIndent()
        ),
        AiTemplate(
            id = "tpl_task_checklist",
            title = "Sprint Task Checklist",
            description = "Interactive checklist card with tap-to-toggle completion status.",
            iconEmoji = "✅",
            category = TemplateCategory.DATA_TOOL,
            schemaPromptPrefix = "[TEMPLATE:CHECKLIST]",
            defaultPrompt = "List 4 critical tasks for deploying cellular RPC protocol.",
            mockOfflinePayload = """
                {"type":"task_checklist","id":"chk_sprint","title":"Cellular Protocol Checklist","items":["Verify CRC16 Integrity","Test Sliding Window NACKs","Enforce Carrier Delay","Sync Room Database"],"doneFlags":[true,true,true,false]}
            """.trimIndent()
        ),
        AiTemplate(
            id = "tpl_device_diagnostics",
            title = "Device Health Mini-App",
            description = "Mini-app that inspects battery status, carrier signal, and storage.",
            iconEmoji = "📱",
            category = TemplateCategory.MINI_APP,
            schemaPromptPrefix = "[SCHEMA:MINIAPP] [TEMPLATE:DIAGNOSTICS]",
            defaultPrompt = "Build a device diagnostics mini-app that reads battery and network status.",
            mockOfflinePayload = """[APP:BUILD:device_health]{"title":"Hardware Sensor Audit","version":"1.0.0","description":"Reads battery and cellular diagnostics","icon":"battery_charging_full","state":{"battery":85,"status":"GOOD"},"ui":{"type":"Column","children":[{"type":"Card","title":"Hardware Health","subtitle":"Battery & Carrier Link"},{"type":"Text","text":"Battery Level: 85% • Status: GOOD"},{"type":"Button","label":"Inspect Battery Telemetry","action":"state.battery = 88; bridge.notify('Battery', 'Telemetry refreshed'); bridge.commit(JSON.stringify(state));"}]},"js":"state.battery = 88; bridge.notify('Battery', 'Telemetry refreshed'); bridge.commit(JSON.stringify(state));"}"""
        )
    )

    fun getById(id: String): AiTemplate? {
        return TEMPLATES.find { it.id == id }
    }

    fun getByCategory(category: TemplateCategory): List<AiTemplate> {
        return TEMPLATES.filter { it.category == category }
    }
}
