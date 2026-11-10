package com.eopera.youhoo.data.remote

import com.eopera.youhoo.core.model.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseMessageDto(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    val text: String,
    val sender: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_device_id") val senderDeviceId: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
) {
    fun toDomain(currentDeviceId: String, currentUserId: String): Message = Message(
        id = id,
        text = text,
        sender = sender,
        createdAt = createdAt,
        conversationId = conversationId,
        parentId = parentId,
        senderId = senderId,
        senderDeviceId = senderDeviceId,
        deliveredAt = deliveredAt,
        readAt = readAt,
        deletedAt = deletedAt,
        isFromCurrentDevice = senderDeviceId == currentDeviceId,
        isFromCurrentUser = senderId == currentUserId
    )
}

@Serializable
data class NewSupabaseMessageDto(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val text: String,
    val sender: String,
    @SerialName("sender_device_id") val senderDeviceId: String
)

@Serializable
data class DirectConversationRequestDto(
    @SerialName("p_user_id") val userId: String,
    @SerialName("p_other_user_id") val otherUserId: String
)

@Serializable
data class ConversationPageRequestDto(
    @SerialName("p_conversation_id") val conversationId: String,
    @SerialName("p_page_size") val pageSize: Int,
    @SerialName("p_before_created_at") val beforeCreatedAt: String? = null,
    @SerialName("p_before_id") val beforeId: String? = null
)

@Serializable
data class MarkMessagesReceiptDto(
    @SerialName("p_message_ids") val messageIds: List<String>,
    @SerialName("p_device_id") val deviceId: String
)
