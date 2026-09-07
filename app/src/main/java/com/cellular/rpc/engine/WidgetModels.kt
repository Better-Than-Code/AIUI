package com.cellular.rpc.engine

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

sealed class WidgetData(val type: String) {
    abstract fun toJson(): String
    abstract fun computeContentHash(): String

    data class Weather(
        val temp: Int,
        val city: String,
        val cond: String,
        val high: Int = 76,
        val low: Int = 58
    ) : WidgetData("weather") {
        override fun toJson(): String {
            return """{"type":"weather","temp":$temp,"city":"$city","cond":"$cond","high":$high,"low":$low}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): Weather {
                return Weather(
                    temp = json.optInt("temp", 0),
                    city = json.optString("city", "Unknown"),
                    cond = json.optString("cond", "Fair"),
                    high = json.optInt("high", 76),
                    low = json.optInt("low", 58)
                )
            }
        }
    }

    data class NewsDigest(
        val id: String,
        val headline: String,
        val summary: String,
        val source: String = "Cellular Net"
    ) : WidgetData("news_digest") {
        override fun toJson(): String {
            val safeHeadline = headline.replace("\"", "\\\"")
            val safeSummary = summary.replace("\"", "\\\"")
            val safeSource = source.replace("\"", "\\\"")
            return """{"type":"news_digest","id":"$id","headline":"$safeHeadline","summary":"$safeSummary","source":"$safeSource"}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): NewsDigest {
                return NewsDigest(
                    id = json.optString("id", "0"),
                    headline = json.optString("headline", ""),
                    summary = json.optString("summary", ""),
                    source = json.optString("source", "Cellular Net")
                )
            }
        }
    }

    data class MarketTicker(
        val sym: String,
        val price: String,
        val chg: String,
        val sparkline: List<Float> = listOf(90.2f, 91.0f, 90.5f, 91.8f, 91.42f)
    ) : WidgetData("market_ticker") {
        override fun toJson(): String {
            val sparklineStr = sparkline.joinToString(",") { "%.2f".format(it) }
            return """{"type":"market_ticker","sym":"$sym","price":"$price","chg":"$chg","sparkline":[$sparklineStr]}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): MarketTicker {
                val sparklineList = mutableListOf<Float>()
                val arr = json.optJSONArray("sparkline")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        sparklineList.add(arr.optDouble(i, 0.0).toFloat())
                    }
                }
                if (sparklineList.isEmpty()) {
                    sparklineList.addAll(listOf(90.2f, 91.0f, 90.5f, 91.8f, 91.42f))
                }
                return MarketTicker(
                    sym = json.optString("sym", "N/A"),
                    price = json.optString("price", "$0.00"),
                    chg = json.optString("chg", "0.0%"),
                    sparkline = sparklineList
                )
            }
        }
    }

    data class CellularTransfer(
        val id: String,
        val to: String,
        val amount: String,
        val memo: String,
        val status: String
    ) : WidgetData("transfer") {
        override fun toJson(): String {
            val safeTo = to.replace("\"", "\\\"")
            val safeMemo = memo.replace("\"", "\\\"")
            return """{"type":"transfer","id":"$id","to":"$safeTo","amount":"$amount","memo":"$safeMemo","status":"$status"}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): CellularTransfer {
                return CellularTransfer(
                    id = json.optString("id", "T001"),
                    to = json.optString("to", "Recipient"),
                    amount = json.optString("amount", "$0.00"),
                    memo = json.optString("memo", ""),
                    status = json.optString("status", "CONFIRMED")
                )
            }
        }
    }

    data class CellularPoll(
        val id: String,
        val question: String,
        val options: List<String>,
        val votes: List<Int>,
        val userVoteIndex: Int = -1
    ) : WidgetData("poll") {
        override fun toJson(): String {
            val safeQ = question.replace("\"", "\\\"")
            val optJson = options.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
            val votesJson = votes.joinToString(",")
            return """{"type":"poll","id":"$id","question":"$safeQ","options":[$optJson],"votes":[$votesJson],"userVoteIndex":$userVoteIndex}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): CellularPoll {
                val opts = mutableListOf<String>()
                val optArr = json.optJSONArray("options")
                if (optArr != null) {
                    for (i in 0 until optArr.length()) {
                        opts.add(optArr.optString(i))
                    }
                }
                val voteList = mutableListOf<Int>()
                val voteArr = json.optJSONArray("votes")
                if (voteArr != null) {
                    for (i in 0 until voteArr.length()) {
                        voteList.add(voteArr.optInt(i, 0))
                    }
                }
                return CellularPoll(
                    id = json.optString("id", "P01"),
                    question = json.optString("question", "Question"),
                    options = if (opts.isEmpty()) listOf("Option A", "Option B") else opts,
                    votes = if (voteList.size == opts.size) voteList else List(opts.size) { 0 },
                    userVoteIndex = json.optInt("userVoteIndex", -1)
                )
            }
        }
    }

    data class ChatText(
        val text: String
    ) : WidgetData("chat") {
        override fun toJson(): String {
            val safeText = text.replace("\"", "\\\"")
            return """{"type":"chat","text":"$safeText"}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): ChatText {
                return ChatText(text = json.optString("text", ""))
            }
        }
    }

    data class CellularTool(
        val id: String,
        val title: String,
        val subtitle: String,
        val valuePrimary: String,
        val valueSecondary: String,
        val actionLabel: String = "Compute"
    ) : WidgetData("tool") {
        override fun toJson(): String {
            val sTitle = title.replace("\"", "\\\"")
            val sSub = subtitle.replace("\"", "\\\"")
            val sV1 = valuePrimary.replace("\"", "\\\"")
            val sV2 = valueSecondary.replace("\"", "\\\"")
            val sAct = actionLabel.replace("\"", "\\\"")
            return """{"type":"tool","id":"$id","title":"$sTitle","subtitle":"$sSub","valuePrimary":"$sV1","valueSecondary":"$sV2","actionLabel":"$sAct"}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): CellularTool {
                return CellularTool(
                    id = json.optString("id", "tool_01"),
                    title = json.optString("title", "Cellular Tool"),
                    subtitle = json.optString("subtitle", "Offline calculation"),
                    valuePrimary = json.optString("valuePrimary", "$0.00"),
                    valueSecondary = json.optString("valueSecondary", ""),
                    actionLabel = json.optString("actionLabel", "Recalculate")
                )
            }
        }
    }

    data class CalendarEvent(
        val id: String,
        val title: String,
        val time: String,
        val location: String,
        val attendees: Int = 1
    ) : WidgetData("calendar_event") {
        override fun toJson(): String {
            val sTitle = title.replace("\"", "\\\"")
            val sTime = time.replace("\"", "\\\"")
            val sLoc = location.replace("\"", "\\\"")
            return """{"type":"calendar_event","id":"$id","title":"$sTitle","time":"$sTime","location":"$sLoc","attendees":$attendees}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): CalendarEvent {
                return CalendarEvent(
                    id = json.optString("id", "evt_01"),
                    title = json.optString("title", "Sprint Meeting"),
                    time = json.optString("time", "3:00 PM - 3:45 PM"),
                    location = json.optString("location", "Room 402 / Cellular Link"),
                    attendees = json.optInt("attendees", 3)
                )
            }
        }
    }

    data class TaskChecklist(
        val id: String,
        val title: String,
        val items: List<String>,
        val doneFlags: List<Boolean>
    ) : WidgetData("task_checklist") {
        override fun toJson(): String {
            val sTitle = title.replace("\"", "\\\"")
            val itemsJson = items.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
            val doneJson = doneFlags.joinToString(",") { "$it" }
            return """{"type":"task_checklist","id":"$id","title":"$sTitle","items":[$itemsJson],"doneFlags":[$doneJson]}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): TaskChecklist {
                val itemsList = mutableListOf<String>()
                val itemsArr = json.optJSONArray("items")
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        itemsList.add(itemsArr.optString(i))
                    }
                }
                val flagsList = mutableListOf<Boolean>()
                val flagsArr = json.optJSONArray("doneFlags")
                if (flagsArr != null) {
                    for (i in 0 until flagsArr.length()) {
                        flagsList.add(flagsArr.optBoolean(i, false))
                    }
                }
                val finalItems = if (itemsList.isEmpty()) listOf("Verify schema", "Test cellular SMS", "Deploy widget") else itemsList
                val finalFlags = if (flagsList.size == finalItems.size) flagsList else List(finalItems.size) { false }
                return TaskChecklist(
                    id = json.optString("id", "task_01"),
                    title = json.optString("title", "Cellular Tasks"),
                    items = finalItems,
                    doneFlags = finalFlags
                )
            }
        }
    }

    data class DynamicSduiNode(
        val type: String, // column, row, card, box, text, button, badge, progress, divider, key_value, chip, metric, list_item, icon
        val id: String = "",
        val text: String = "",
        val title: String = "",
        val subtitle: String = "",
        val value: String = "",
        val secondaryValue: String = "",
        val colorHex: String? = null,
        val iconName: String? = null,
        val action: String? = null,
        val actionPayload: String? = null,
        val progress: Float = 0f,
        val style: String? = null,
        val isBold: Boolean = false,
        val padding: Int = 0,
        val spacing: Int = 8,
        val children: List<DynamicSduiNode> = emptyList()
    ) {
        companion object {
            fun fromJson(json: JSONObject): DynamicSduiNode {
                val nodeType = json.optString("type", json.optString("component", "text")).lowercase()
                val childList = mutableListOf<DynamicSduiNode>()
                val childrenArr = json.optJSONArray("children") ?: json.optJSONArray("components") ?: json.optJSONArray("elements") ?: json.optJSONArray("items")
                if (childrenArr != null) {
                    for (i in 0 until childrenArr.length()) {
                        val childObj = childrenArr.optJSONObject(i)
                        if (childObj != null) {
                            childList.add(fromJson(childObj))
                        } else {
                            val strVal = childrenArr.optString(i)
                            if (strVal.isNotBlank()) {
                                childList.add(DynamicSduiNode(type = "text", text = strVal))
                            }
                        }
                    }
                }

                return DynamicSduiNode(
                    type = nodeType,
                    id = json.optString("id", ""),
                    text = json.optString("text", json.optString("label", json.optString("content", ""))),
                    title = json.optString("title", json.optString("header", "")),
                    subtitle = json.optString("subtitle", json.optString("desc", json.optString("description", ""))),
                    value = json.optString("value", json.optString("val", json.optString("primary", ""))),
                    secondaryValue = json.optString("secondaryValue", json.optString("secondary", json.optString("sub", ""))),
                    colorHex = json.optString("color", json.optString("colorHex", "")).takeIf { it.isNotBlank() },
                    iconName = json.optString("icon", json.optString("iconName", "")).takeIf { it.isNotBlank() },
                    action = json.optString("action", json.optString("onClick", "")).takeIf { it.isNotBlank() },
                    actionPayload = json.optString("actionPayload", json.optString("payload", "")).takeIf { it.isNotBlank() },
                    progress = json.optDouble("progress", 0.0).toFloat(),
                    style = json.optString("style", "body"),
                    isBold = json.optBoolean("isBold", json.optBoolean("bold", false)),
                    padding = json.optInt("padding", 0),
                    spacing = json.optInt("spacing", 8),
                    children = childList
                )
            }
        }
    }

    data class DynamicBlueprint(
        val id: String,
        val title: String,
        val subtitle: String = "",
        val icon: String = "extension",
        val themeColorHex: String = "#00E5FF",
        val rootNode: DynamicSduiNode,
        val rawJson: String = ""
    ) : WidgetData("blueprint") {
        override fun toJson(): String {
            return if (rawJson.isNotBlank()) rawJson else """{"type":"blueprint","id":"$id","title":"$title","subtitle":"$subtitle"}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): DynamicBlueprint {
                val id = json.optString("id", json.optString("featureId", "blueprint_${System.currentTimeMillis() % 10000}"))
                val title = json.optString("title", json.optString("name", "Dynamic Cellular Blueprint"))
                val subtitle = json.optString("subtitle", json.optString("description", "Server-Driven UI"))
                val icon = json.optString("icon", json.optString("iconName", "extension"))
                val color = json.optString("themeColorHex", json.optString("color", "#00E5FF"))

                val rootObj = json.optJSONObject("root") ?: json.optJSONObject("layout") ?: json.optJSONObject("ui")
                val rootNode = if (rootObj != null) {
                    DynamicSduiNode.fromJson(rootObj)
                } else {
                    // Treat the object itself or its components as container root
                    DynamicSduiNode.fromJson(json.apply {
                        if (!has("type") || optString("type") == "blueprint" || optString("type") == "sdui") {
                            put("type", "column")
                        }
                    })
                }

                return DynamicBlueprint(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    icon = icon,
                    themeColorHex = color,
                    rootNode = rootNode,
                    rawJson = json.toString()
                )
            }

            fun fromGenericJson(json: JSONObject): DynamicBlueprint {
                val title = json.optString("title", json.optString("name", json.optString("type", "Cellular Schema").replace("_", " ").capitalize()))
                val id = json.optString("id", "dyn_${System.currentTimeMillis() % 1000}")
                val subtitle = json.optString("subtitle", json.optString("status", ""))
                
                val children = mutableListOf<DynamicSduiNode>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k in listOf("type", "id", "title", "subtitle")) continue
                    val v = json.opt(k)
                    if (v is JSONObject) {
                        children.add(DynamicSduiNode.fromJson(v))
                    } else if (v is JSONArray) {
                        val items = mutableListOf<DynamicSduiNode>()
                        for (i in 0 until v.length()) {
                            val itemObj = v.optJSONObject(i)
                            if (itemObj != null) {
                                items.add(DynamicSduiNode.fromJson(itemObj))
                            } else {
                                items.add(DynamicSduiNode(type = "chip", text = v.optString(i)))
                            }
                        }
                        children.add(DynamicSduiNode(type = "row", title = k.capitalize(), children = items))
                    } else {
                        children.add(DynamicSduiNode(type = "key_value", title = k.replace("_", " ").capitalize(), value = v.toString()))
                    }
                }

                val root = DynamicSduiNode(
                    type = "column",
                    children = children
                )

                return DynamicBlueprint(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    rootNode = root,
                    rawJson = json.toString()
                )
            }
        }
    }

    data class MiniAppPreview(
        val appId: String,
        val version: Int = 1,
        val title: String,
        val icon: String = "checklist",
        val description: String = "",
        val category: String = "productivity",
        val rawBlueprintJson: String
    ) : WidgetData("mini_app_blueprint") {
        override fun toJson(): String = rawBlueprintJson

        override fun computeContentHash(): String = hashString(rawBlueprintJson)

        companion object {
            fun fromJson(json: JSONObject): MiniAppPreview {
                val appId = json.optString("appId", "app_${System.currentTimeMillis()}")
                val version = json.optInt("version", 1)
                val meta = json.optJSONObject("metadata") ?: JSONObject()
                val title = meta.optString("title", json.optString("title", "Mini App"))
                val icon = meta.optString("icon", "checklist")
                val description = meta.optString("description", "")
                val category = meta.optString("category", "productivity")

                return MiniAppPreview(
                    appId = appId,
                    version = version,
                    title = title,
                    icon = icon,
                    description = description,
                    category = category,
                    rawBlueprintJson = json.toString()
                )
            }
        }
    }

    data class SystemStatus(
        val batteryPct: Int,
        val signalDbm: Int,
        val freeStorageMb: Long,
        val queuedPackets: Int,
        val linkQuality: String = "EXCELLENT"
    ) : WidgetData("system_status") {
        override fun toJson(): String {
            return """{"type":"system_status","batteryPct":$batteryPct,"signalDbm":$signalDbm,"freeStorageMb":$freeStorageMb,"queuedPackets":$queuedPackets,"linkQuality":"$linkQuality"}"""
        }

        override fun computeContentHash(): String {
            return hashString(toJson())
        }

        companion object {
            fun fromJson(json: JSONObject): SystemStatus {
                return SystemStatus(
                    batteryPct = json.optInt("batteryPct", 88),
                    signalDbm = json.optInt("signalDbm", -72),
                    freeStorageMb = json.optLong("freeStorageMb", 4096L),
                    queuedPackets = json.optInt("queuedPackets", 0),
                    linkQuality = json.optString("linkQuality", "EXCELLENT")
                )
            }
        }
    }

    companion object {
        fun hashString(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return digest.take(4).joinToString("") { "%02x".format(it) }
        }

        fun parse(input: String): WidgetData? {
            var trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            // Strip thread tag e.g. [TID:xyz] if present at the start
            val tidRegex = Regex("""^\[TID:([a-zA-Z0-9_]+)\]\s*""")
            val match = tidRegex.find(trimmed)
            if (match != null) {
                trimmed = trimmed.replace(tidRegex, "").trim()
            }

            // 1. Direct JSON parse
            parseJsonInternal(trimmed)?.let { return it }

            // 2. Embedded JSON block extraction (e.g. conversational text with ```json ... ``` or embedded {...})
            val startIdx = trimmed.indexOf('{')
            val endIdx = trimmed.lastIndexOf('}')
            if (startIdx != -1 && endIdx > startIdx) {
                val candidate = trimmed.substring(startIdx, endIdx + 1)
                parseJsonInternal(candidate)?.let { return it }
            }

            return null
        }

        private fun parseJsonInternal(jsonString: String): WidgetData? {
            return try {
                val obj = JSONObject(jsonString)
                val type = obj.optString("type")
                when (type) {
                    "weather" -> Weather.fromJson(obj)
                    "news_digest" -> NewsDigest.fromJson(obj)
                    "market_ticker" -> MarketTicker.fromJson(obj)
                    "transfer" -> CellularTransfer.fromJson(obj)
                    "poll" -> CellularPoll.fromJson(obj)
                    "chat" -> ChatText.fromJson(obj)
                    "tool" -> CellularTool.fromJson(obj)
                    "calendar_event" -> CalendarEvent.fromJson(obj)
                    "task_checklist" -> TaskChecklist.fromJson(obj)
                    "mini_app_blueprint", "mini_app", "miniapp" -> MiniAppPreview.fromJson(obj)
                    "system_status" -> SystemStatus.fromJson(obj)
                    "blueprint", "sdui", "dynamic", "custom", "layout", "screen", "widget" -> DynamicBlueprint.fromJson(obj)
                    else -> {
                        if (obj.has("children") || obj.has("components") || obj.has("elements") || obj.has("root") || obj.has("layout") || obj.has("title")) {
                            DynamicBlueprint.fromJson(obj)
                        } else if (obj.length() > 0) {
                            DynamicBlueprint.fromGenericJson(obj)
                        } else {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
