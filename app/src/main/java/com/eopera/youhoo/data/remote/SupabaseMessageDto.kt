package com.eopera.youhoo.data.remote

import com.eopera.youhoo.core.model.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseMessageDto(
    val id: Long,
    val text: String,
    val sender: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("sender_device_id") val senderDeviceId: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("read_at") val readAt: String? = null
) {
    fun toDomain(currentDeviceId: String): Message = Message(
        id = id,
        text = text,
        sender = sender,
        createdAt = createdAt,
        senderDeviceId = senderDeviceId,
        deliveredAt = deliveredAt,
        readAt = readAt,
        isFromCurrentDevice = senderDeviceId == currentDeviceId
    )
}

@Serializable
data class NewSupabaseMessageDto(
    val text: String,
    val sender: String,
    @SerialName("sender_device_id") val senderDeviceId: String
)

@Serializable
data class MarkMessagesReceiptDto(
    @SerialName("p_message_ids") val messageIds: List<Long>,
    @SerialName("p_device_id") val deviceId: String
)
