package com.cellular.rpc.engine

import java.util.UUID

enum class MessageSender {
    USER,
    AI_GATEWAY
}

enum class MessageDeliveryStatus {
    QUEUED,
    IN_FLIGHT,
    DELIVERED
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String = "th_main",
    val sender: MessageSender,
    val text: String,
    val widgetData: WidgetData? = null,
    val attachment: MessageAttachment? = null,
    val is304NotModified: Boolean = false,
    val wirePacket: String? = null,
    val byteSize: Int = 0,
    val pduCount: Int = 1,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.DELIVERED,
    val timestampMs: Long = System.currentTimeMillis()
)
