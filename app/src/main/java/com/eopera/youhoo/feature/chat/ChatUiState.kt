package com.eopera.youhoo.feature.chat

import com.eopera.youhoo.core.model.ConnectionStatus

data class ChatUiState(
    val messages: List<MessageUi> = emptyList(),
    val senderName: String = "",
    val input: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTING,
    val isSending: Boolean = false,
    val canSend: Boolean = false
)

data class MessageUi(
    val id: Long,
    val text: String,
    val senderName: String,
    val time: String,
    val isMine: Boolean
)

sealed interface ChatEvent {
    data class SenderNameChanged(val value: String) : ChatEvent
    data class InputChanged(val value: String) : ChatEvent
    data object SendMessage : ChatEvent
    data object RetryConnection : ChatEvent
}
