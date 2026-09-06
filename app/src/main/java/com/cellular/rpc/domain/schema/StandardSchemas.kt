package com.cellular.rpc.domain.schema

import com.cellular.rpc.engine.WidgetData
import org.json.JSONObject

/**
 * Standard Schema for Weather widget and telemetry payload.
 */
object WeatherSchema : CellularSchema<WidgetData.Weather> {
    override val schemaId: String = "weather"
    override val version: Int = 1
    override val displayName: String = "Weather Forecast"
    override val description: String = "Temperature, condition, and high/low bounds for local weather."
    override val category: SchemaCategory = SchemaCategory.WIDGET

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("temp", SchemaFieldType.INTEGER, true, "Current temperature in degrees", "72"),
        SchemaFieldDefinition("city", SchemaFieldType.STRING, true, "City name or location", "San Francisco"),
        SchemaFieldDefinition("cond", SchemaFieldType.STRING, true, "Weather condition text", "Sunny"),
        SchemaFieldDefinition("high", SchemaFieldType.INTEGER, false, "Forecast high", "76"),
        SchemaFieldDefinition("low", SchemaFieldType.INTEGER, false, "Forecast low", "58")
    )

    override fun serialize(data: WidgetData.Weather): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.Weather = WidgetData.Weather.fromJson(json)

    override fun validate(data: WidgetData.Weather): SchemaValidationResult {
        val errors = mutableListOf<String>()
        if (data.city.isBlank()) errors.add("City name cannot be blank")
        if (data.temp < -100 || data.temp > 150) errors.add("Temperature ${data.temp}° out of physical range")
        return if (errors.isEmpty()) SchemaValidationResult.Valid else SchemaValidationResult.Invalid(errors)
    }

    override fun renderFallbackSummary(data: WidgetData.Weather): String {
        return "${data.city}: ${data.temp}° ${data.cond} (H:${data.high}° L:${data.low}°)"
    }

    override fun sampleJson(): String =
        """{"type":"weather","temp":72,"city":"San Francisco","cond":"Sunny","high":76,"low":58}"""
}

/**
 * Standard Schema for News Digest payload.
 */
object NewsDigestSchema : CellularSchema<WidgetData.NewsDigest> {
    override val schemaId: String = "news_digest"
    override val version: Int = 1
    override val displayName: String = "News Digest"
    override val description: String = "Curated headline and executive summary delivered over SMS."
    override val category: SchemaCategory = SchemaCategory.WIDGET

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("id", SchemaFieldType.STRING, true, "Unique article identifier", "N101"),
        SchemaFieldDefinition("headline", SchemaFieldType.STRING, true, "News headline text", "Next-Gen Lunar Station Announced"),
        SchemaFieldDefinition("summary", SchemaFieldType.STRING, true, "Compact summary under 140 chars", "International consortium reveals new orbit outpost."),
        SchemaFieldDefinition("source", SchemaFieldType.STRING, false, "Publishing source attribution", "Cellular Net")
    )

    override fun serialize(data: WidgetData.NewsDigest): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.NewsDigest = WidgetData.NewsDigest.fromJson(json)

    override fun validate(data: WidgetData.NewsDigest): SchemaValidationResult {
        val errors = mutableListOf<String>()
        if (data.headline.isBlank()) errors.add("Headline cannot be blank")
        return if (errors.isEmpty()) SchemaValidationResult.Valid else SchemaValidationResult.Invalid(errors)
    }

    override fun renderFallbackSummary(data: WidgetData.NewsDigest): String {
        return "NEWS: ${data.headline} - ${data.summary}"
    }

    override fun sampleJson(): String =
        """{"type":"news_digest","id":"N101","headline":"Next-Gen Lunar Station","summary":"International consortium reveals orbit outpost.","source":"Cellular Net"}"""
}

/**
 * Standard Schema for Market Ticker payload.
 */
object MarketTickerSchema : CellularSchema<WidgetData.MarketTicker> {
    override val schemaId: String = "market_ticker"
    override val version: Int = 1
    override val displayName: String = "Market Ticker"
    override val description: String = "Financial asset prices, daily percentage change, and sparkline."
    override val category: SchemaCategory = SchemaCategory.FINANCIAL

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("sym", SchemaFieldType.STRING, true, "Asset symbol or ticker", "BTC/USD"),
        SchemaFieldDefinition("price", SchemaFieldType.STRING, true, "Current asset price", "$64,250"),
        SchemaFieldDefinition("chg", SchemaFieldType.STRING, true, "Percentage change", "+3.42%"),
        SchemaFieldDefinition("sparkline", SchemaFieldType.LIST_FLOAT, false, "Normalized mini chart datapoints", "[90.2, 91.0, 91.8]")
    )

    override fun serialize(data: WidgetData.MarketTicker): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.MarketTicker = WidgetData.MarketTicker.fromJson(json)

    override fun renderFallbackSummary(data: WidgetData.MarketTicker): String {
        return "${data.sym}: ${data.price} (${data.chg})"
    }

    override fun sampleJson(): String =
        """{"type":"market_ticker","sym":"BTC/USD","price":"$64,250","chg":"+3.42%","sparkline":[90.2,91.0,91.8]}"""
}

/**
 * Standard Schema for Cellular Transfer / Cash payload.
 */
object CellularTransferSchema : CellularSchema<WidgetData.CellularTransfer> {
    override val schemaId: String = "transfer"
    override val version: Int = 1
    override val displayName: String = "Cellular Cash Transfer"
    override val description: String = "Peer-to-peer balance transfer authorization and confirmation."
    override val category: SchemaCategory = SchemaCategory.TRANSACTION

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("id", SchemaFieldType.STRING, true, "Transfer transaction ID", "TX-902"),
        SchemaFieldDefinition("to", SchemaFieldType.STRING, true, "Recipient identifier or phone number", "Alex Chen"),
        SchemaFieldDefinition("amount", SchemaFieldType.STRING, true, "Fiat amount with currency symbol", "$25.00"),
        SchemaFieldDefinition("memo", SchemaFieldType.STRING, false, "Transaction memo", "Sprint Lunch"),
        SchemaFieldDefinition("status", SchemaFieldType.STRING, true, "PENDING, CONFIRMED, or FAILED", "CONFIRMED")
    )

    override fun serialize(data: WidgetData.CellularTransfer): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.CellularTransfer = WidgetData.CellularTransfer.fromJson(json)

    override fun renderFallbackSummary(data: WidgetData.CellularTransfer): String {
        return "Transfer: ${data.amount} to ${data.to} [${data.status}]"
    }

    override fun sampleJson(): String =
        """{"type":"transfer","id":"TX-902","to":"Alex Chen","amount":"$25.00","memo":"Sprint Lunch","status":"CONFIRMED"}"""
}

/**
 * Standard Schema for Cellular Poll payload.
 */
object CellularPollSchema : CellularSchema<WidgetData.CellularPoll> {
    override val schemaId: String = "poll"
    override val version: Int = 1
    override val displayName: String = "Cellular Poll"
    override val description: String = "Distributed voting card with vote counts and user selection."
    override val category: SchemaCategory = SchemaCategory.SOCIAL

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("id", SchemaFieldType.STRING, true, "Poll identifier", "P01"),
        SchemaFieldDefinition("question", SchemaFieldType.STRING, true, "Poll question", "Sprint Review at 3PM?"),
        SchemaFieldDefinition("options", SchemaFieldType.LIST_STRING, true, "List of ballot options", "[\"Yes\", \"No\"]"),
        SchemaFieldDefinition("votes", SchemaFieldType.LIST_FLOAT, true, "Vote counts per option", "[4, 1]"),
        SchemaFieldDefinition("userVoteIndex", SchemaFieldType.INTEGER, false, "Selected option index or -1", "0")
    )

    override fun serialize(data: WidgetData.CellularPoll): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.CellularPoll = WidgetData.CellularPoll.fromJson(json)

    override fun renderFallbackSummary(data: WidgetData.CellularPoll): String {
        return "Poll: ${data.question} (${data.options.size} options, total votes: ${data.votes.sum()})"
    }

    override fun sampleJson(): String =
        """{"type":"poll","id":"P01","question":"Sprint Review at 3PM?","options":["Yes","No"],"votes":[4,1],"userVoteIndex":-1}"""
}

/**
 * Standard Schema for Cellular Tool payload (Calculators, splitters, converters).
 */
object CellularToolSchema : CellularSchema<WidgetData.CellularTool> {
    override val schemaId: String = "tool"
    override val version: Int = 1
    override val displayName: String = "Cellular Tool"
    override val description: String = "Lightweight utility card for calculators, unit conversions, and estimators."
    override val category: SchemaCategory = SchemaCategory.TOOL

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("id", SchemaFieldType.STRING, true, "Tool instance ID", "tool_tip"),
        SchemaFieldDefinition("title", SchemaFieldType.STRING, true, "Tool title", "Tip & Split Calculator"),
        SchemaFieldDefinition("subtitle", SchemaFieldType.STRING, false, "Subheading or inputs", "Bill: $85.00 • 20%"),
        SchemaFieldDefinition("valuePrimary", SchemaFieldType.STRING, true, "Primary calculated result", "$102.00 Total"),
        SchemaFieldDefinition("valueSecondary", SchemaFieldType.STRING, false, "Secondary breakdown result", "$51.00 / person"),
        SchemaFieldDefinition("actionLabel", SchemaFieldType.STRING, false, "Interactive button label", "Recalculate")
    )

    override fun serialize(data: WidgetData.CellularTool): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.CellularTool = WidgetData.CellularTool.fromJson(json)

    override fun renderFallbackSummary(data: WidgetData.CellularTool): String {
        return "${data.title}: ${data.valuePrimary} (${data.subtitle})"
    }

    override fun sampleJson(): String =
        """{"type":"tool","id":"tool_tip","title":"Tip & Split Calculator","subtitle":"Bill: $85.00","valuePrimary":"$102.00 Total","valueSecondary":"$51.00 / Person","actionLabel":"Recalculate"}"""
}

/**
 * Standard Schema for Chat Text payload.
 */
object ChatTextSchema : CellularSchema<WidgetData.ChatText> {
    override val schemaId: String = "chat"
    override val version: Int = 1
    override val displayName: String = "Conversational Message"
    override val description: String = "Plain AI conversation message or assistant explanation."
    override val category: SchemaCategory = SchemaCategory.WIDGET

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("text", SchemaFieldType.STRING, true, "Conversational content", "Hello from Pally AI!")
    )

    override fun serialize(data: WidgetData.ChatText): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.ChatText = WidgetData.ChatText.fromJson(json)

    override fun renderFallbackSummary(data: WidgetData.ChatText): String = data.text

    override fun sampleJson(): String =
        """{"type":"chat","text":"Hello from Pally AI via Cellular Transport!"}"""
}

/**
 * Standard Schema for Calendar Event / Schedule payload.
 */
object CalendarEventSchema : CellularSchema<WidgetData.CalendarEvent> {
    override val schemaId: String = "calendar_event"
    override val version: Int = 1
    override val displayName: String = "Calendar Event"
    override val description: String = "Offline calendar appointment, meeting time, and attendee count."
    override val category: SchemaCategory = SchemaCategory.PRODUCTIVITY

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("id", SchemaFieldType.STRING, true, "Event identifier", "evt_01"),
        SchemaFieldDefinition("title", SchemaFieldType.STRING, true, "Event title", "Sprint Architecture Review"),
        SchemaFieldDefinition("time", SchemaFieldType.STRING, true, "Time span string", "3:00 PM - 3:45 PM"),
        SchemaFieldDefinition("location", SchemaFieldType.STRING, false, "Meeting location or conference", "Room 402 / Cellular Link"),
        SchemaFieldDefinition("attendees", SchemaFieldType.INTEGER, false, "Number of attendees", "4")
    )

    override fun serialize(data: WidgetData.CalendarEvent): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.CalendarEvent = WidgetData.CalendarEvent.fromJson(json)

    override fun renderFallbackSummary(data: WidgetData.CalendarEvent): String {
        return "EVENT: ${data.title} @ ${data.time} (${data.location})"
    }

    override fun sampleJson(): String =
        """{"type":"calendar_event","id":"evt_01","title":"Sprint Architecture Review","time":"3:00 PM - 3:45 PM","location":"Room 402","attendees":4}"""
}

/**
 * Standard Schema for Task Checklist / To-do items.
 */
object TaskChecklistSchema : CellularSchema<WidgetData.TaskChecklist> {
    override val schemaId: String = "task_checklist"
    override val version: Int = 1
    override val displayName: String = "Task Checklist"
    override val description: String = "Interactive task items with completion checkboxes."
    override val category: SchemaCategory = SchemaCategory.PRODUCTIVITY

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("id", SchemaFieldType.STRING, true, "Checklist identifier", "task_01"),
        SchemaFieldDefinition("title", SchemaFieldType.STRING, true, "Checklist title", "Sprint Priorities"),
        SchemaFieldDefinition("items", SchemaFieldType.LIST_STRING, true, "Checklist item names", "[\"Deploy RPC\",\"Test SMS\"]"),
        SchemaFieldDefinition("doneFlags", SchemaFieldType.LIST_FLOAT, true, "Completion status booleans", "[true, false]")
    )

    override fun serialize(data: WidgetData.TaskChecklist): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.TaskChecklist = WidgetData.TaskChecklist.fromJson(json)

    override fun renderFallbackSummary(data: WidgetData.TaskChecklist): String {
        val completed = data.doneFlags.count { it }
        return "${data.title}: $completed/${data.items.size} completed"
    }

    override fun sampleJson(): String =
        """{"type":"task_checklist","id":"task_01","title":"Sprint Priorities","items":["Deploy RPC","Test SMS"],"doneFlags":[true,false]}"""
}

/**
 * Standard Schema for System Telemetry and Diagnostics.
 */
object SystemStatusSchema : CellularSchema<WidgetData.SystemStatus> {
    override val schemaId: String = "system_status"
    override val version: Int = 1
    override val displayName: String = "System Telemetry"
    override val description: String = "Device battery, cellular link dBm, free storage, and outbox queue size."
    override val category: SchemaCategory = SchemaCategory.SYSTEM

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("batteryPct", SchemaFieldType.INTEGER, true, "Battery percentage 0-100", "88"),
        SchemaFieldDefinition("signalDbm", SchemaFieldType.INTEGER, true, "Cellular signal strength in dBm", "-75"),
        SchemaFieldDefinition("freeStorageMb", SchemaFieldType.INTEGER, true, "Free internal storage in MB", "4096"),
        SchemaFieldDefinition("queuedPackets", SchemaFieldType.INTEGER, true, "Outbox queue pending count", "2"),
        SchemaFieldDefinition("linkQuality", SchemaFieldType.STRING, false, "POOR, FAIR, GOOD, EXCELLENT", "EXCELLENT")
    )

    override fun serialize(data: WidgetData.SystemStatus): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.SystemStatus = WidgetData.SystemStatus.fromJson(json)

    override fun renderFallbackSummary(data: WidgetData.SystemStatus): String {
        return "SYS: Battery ${data.batteryPct}% | ${data.signalDbm}dBm | Queue: ${data.queuedPackets} | Link: ${data.linkQuality}"
    }

    override fun sampleJson(): String =
        """{"type":"system_status","batteryPct":88,"signalDbm":-75,"freeStorageMb":4096,"queuedPackets":0,"linkQuality":"EXCELLENT"}"""
}
