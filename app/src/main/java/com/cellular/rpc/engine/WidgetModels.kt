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
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

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
                when (obj.optString("type")) {
                    "weather" -> Weather.fromJson(obj)
                    "news_digest" -> NewsDigest.fromJson(obj)
                    "market_ticker" -> MarketTicker.fromJson(obj)
                    "transfer" -> CellularTransfer.fromJson(obj)
                    "poll" -> CellularPoll.fromJson(obj)
                    "chat" -> ChatText.fromJson(obj)
                    "tool" -> CellularTool.fromJson(obj)
                    "calendar_event" -> CalendarEvent.fromJson(obj)
                    "task_checklist" -> TaskChecklist.fromJson(obj)
                    "system_status" -> SystemStatus.fromJson(obj)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
