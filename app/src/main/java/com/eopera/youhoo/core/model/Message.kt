package com.eopera.youhoo.core.model

/** Vendor-independent message used by repositories and the presentation layer. */
data class Message(
    val id: Long,
    val text: String,
    val sender: String,
    val createdAt: String,
    val senderDeviceId: String? = null,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val isFromCurrentDevice: Boolean = false
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
