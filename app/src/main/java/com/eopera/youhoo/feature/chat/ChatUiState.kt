package com.eopera.youhoo.feature.chat

import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.MessageDeliveryStatus

data class ChatUiState(
    val messages: List<MessageUi> = emptyList(),
    val senderName: String = "",
    val input: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING,
    val isSending: Boolean = false,
    val canSend: Boolean = false,
    val unreadAnchorId: Long? = null
)

data class MessageUi(
    val id: Long,
    val text: String,
    val senderName: String,
    val time: String,
    val isMine: Boolean,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    val isRead: Boolean = false
)

sealed interface ChatEvent {
    data class InputChanged(val value: String) : ChatEvent
    data object SendMessage : ChatEvent
    data object RetryConnection : ChatEvent
    data object OpenProfile : ChatEvent
    data object ChatOpened : ChatEvent
    data object ChatClosed : ChatEvent
    data object UnreadAnchorShown : ChatEvent
    data class OpenUnreadMessage(val messageId: Long) : ChatEvent
}
