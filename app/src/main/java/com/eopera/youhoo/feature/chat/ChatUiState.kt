package com.eopera.youhoo.feature.chat

import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.MessageDeliveryStatus

data class ChatUiState(
    val messages: List<MessageUi> = emptyList(),
    val senderName: String = "",
    val contactName: String = "",
    val input: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING,
    val isSending: Boolean = false,
    val canSend: Boolean = false,
    val unreadAnchorId: String? = null,
    val hasOlderMessages: Boolean = false,
    val isLoadingOlder: Boolean = false
)

data class MessageUi(
    val id: String,
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
    data object LoadOlderMessages : ChatEvent
    data object OpenProfile : ChatEvent
    data object Back : ChatEvent
    data class ConversationSelected(val contact: com.eopera.youhoo.core.model.Contact) : ChatEvent
    data object ChatOpened : ChatEvent
    data object ChatClosed : ChatEvent
    data object UnreadAnchorShown : ChatEvent
    data class OpenUnreadMessage(val messageId: String) : ChatEvent
}
