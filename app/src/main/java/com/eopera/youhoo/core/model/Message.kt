package com.eopera.youhoo.core.model

/** Vendor-independent message used by repositories and the presentation layer. */
data class Message(
    val id: String,
    val text: String,
    val sender: String,
    val createdAt: String,
    val conversationId: String = "",
    val parentId: String? = null,
    val senderId: String? = null,
    val senderDeviceId: String? = null,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val deletedAt: String? = null,
    val isFromCurrentDevice: Boolean = false,
    val isFromCurrentUser: Boolean = false
) {
    val deliveryStatus: MessageDeliveryStatus
        get() = when {
            readAt != null -> MessageDeliveryStatus.READ
            deliveredAt != null -> MessageDeliveryStatus.DELIVERED
            else -> MessageDeliveryStatus.SENT
        }
}

enum class MessageDeliveryStatus {
    SENT,
    DELIVERED,
    READ
}
