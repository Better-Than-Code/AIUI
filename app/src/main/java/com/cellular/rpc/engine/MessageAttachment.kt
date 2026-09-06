package com.cellular.rpc.engine

enum class AttachmentType {
    IMAGE,
    FILE,
    VOICE_NOTE
}

data class MessageAttachment(
    val id: String,
    val type: AttachmentType,
    val uri: String,
    val fileName: String,
    val fileSizeBytes: Long = 0,
    val mimeType: String = "*/*",
    val durationMs: Long = 0,
    val voiceAmplitudes: List<Float> = emptyList()
)
