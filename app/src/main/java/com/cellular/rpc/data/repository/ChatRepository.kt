package com.cellular.rpc.data.repository

import com.cellular.rpc.data.local.ChatMessageDao
import com.cellular.rpc.data.local.ChatMessageEntity
import com.cellular.rpc.engine.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    val messages: Flow<List<ChatMessage>> = chatMessageDao.getAllMessages().map { entities ->
        entities.map { entity -> entity.toDomainModel() }
    }

    fun getMessagesForThread(threadId: String): Flow<List<ChatMessage>> =
        chatMessageDao.getMessagesForThread(threadId).map { entities ->
            entities.map { entity -> entity.toDomainModel() }
        }

    suspend fun saveMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(message.toEntity())
    }

    suspend fun saveMessages(messages: List<ChatMessage>) {
        chatMessageDao.insertMessages(messages.map { it.toEntity() })
    }

    suspend fun deleteMessage(id: String) {
        chatMessageDao.deleteMessageById(id)
    }

    suspend fun deleteMessagesForThread(threadId: String) {
        chatMessageDao.deleteMessagesForThread(threadId)
    }

    suspend fun clearChat() {
        chatMessageDao.clearAllMessages()
    }

    private fun ChatMessageEntity.toDomainModel(): ChatMessage {
        val attachment = if (attachmentId != null && attachmentType != null && attachmentUri != null) {
            val typeEnum = try {
                AttachmentType.valueOf(attachmentType)
            } catch (e: Exception) {
                AttachmentType.FILE
            }
            val amps = attachmentAmplitudes?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()
            MessageAttachment(
                id = attachmentId,
                type = typeEnum,
                uri = attachmentUri,
                fileName = attachmentFileName ?: "attachment",
                fileSizeBytes = attachmentSizeBytes,
                mimeType = attachmentMimeType ?: "*/*",
                durationMs = attachmentDurationMs,
                voiceAmplitudes = amps
            )
        } else null

        val widget = widgetDataJson?.let {
            try {
                WidgetData.parse(it)
            } catch (e: Exception) {
                null
            }
        }

        val parsedSender = when (sender) {
            "USER" -> MessageSender.USER
            else -> MessageSender.AI_GATEWAY
        }

        val parsedStatus = try {
            MessageDeliveryStatus.valueOf(deliveryStatus)
        } catch (e: Exception) {
            MessageDeliveryStatus.DELIVERED
        }

        return ChatMessage(
            id = id,
            threadId = threadId,
            sender = parsedSender,
            text = text,
            widgetData = widget,
            attachment = attachment,
            is304NotModified = is304NotModified,
            wirePacket = wirePacket,
            byteSize = byteSize,
            pduCount = pduCount,
            deliveryStatus = parsedStatus,
            timestampMs = timestampMs
        )
    }

    private fun ChatMessage.toEntity(): ChatMessageEntity {
        return ChatMessageEntity(
            id = id,
            threadId = threadId,
            sender = sender.name,
            text = text,
            widgetDataJson = widgetData?.toJson(),
            is304NotModified = is304NotModified,
            wirePacket = wirePacket,
            byteSize = byteSize,
            pduCount = pduCount,
            deliveryStatus = deliveryStatus.name,
            timestampMs = timestampMs,
            attachmentId = attachment?.id,
            attachmentType = attachment?.type?.name,
            attachmentUri = attachment?.uri,
            attachmentFileName = attachment?.fileName,
            attachmentSizeBytes = attachment?.fileSizeBytes ?: 0L,
            attachmentMimeType = attachment?.mimeType,
            attachmentDurationMs = attachment?.durationMs ?: 0L,
            attachmentAmplitudes = attachment?.voiceAmplitudes?.joinToString(",")
        )
    }
}
