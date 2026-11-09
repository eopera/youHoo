package com.eopera.youhoo.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eopera.youhoo.core.model.Message
import com.eopera.youhoo.domain.repository.ChatRepository
import com.eopera.youhoo.domain.repository.DevicePushRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userProfileRepository: UserProfileRepository,
    private val devicePushRepository: DevicePushRepository,
    private val timeFormatter: MessageTimeFormatter,
    featureFlagProvider: FeatureFlagProvider
) : ViewModel() {
    val screenVariant: ChatScreenVariant = featureFlagProvider.chatScreenVariant

    private val input = MutableStateFlow("")
    private val isSending = MutableStateFlow(false)
    private val isChatVisible = MutableStateFlow(false)
    private val unreadAnchorId = MutableStateFlow<Long?>(null)
    private var connectionJob: Job? = null

    private val baseUiState = combine(
        chatRepository.messages,
        chatRepository.connectionStatus,
        userProfileRepository.senderName,
        input,
        isSending
    ) { messages, status, senderName, currentInput, sending ->
        val trimmedSender = senderName.trim()
        ChatUiState(
            messages = messages.map { message ->
                val isMine = trimmedSender.isNotEmpty() && message.sender == trimmedSender
                MessageUi(
                    id = message.id,
                    text = message.text,
                    senderName = message.sender,
                    time = timeFormatter.format(message.createdAt),
                    isMine = isMine,
                    deliveryStatus = if (isMine) {
                        message.deliveryStatus
                    } else {
                        com.eopera.youhoo.core.model.MessageDeliveryStatus.SENT
                    },
                    isRead = message.readAt != null
                )
            },
            senderName = senderName,
            input = currentInput,
            connectionStatus = status,
            isSending = sending,
            canSend = currentInput.isNotBlank() && trimmedSender.isNotEmpty() && !sending
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState()
    )

    val uiState = combine(baseUiState, unreadAnchorId) { state, anchorId ->
        state.copy(unreadAnchorId = anchorId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState()
    )

    init {
        viewModelScope.launch {
            devicePushRepository.registerDevice()
        }
        viewModelScope.launch {
            isChatVisible.collectLatest { visible ->
                if (!visible) return@collectLatest
                chatRepository.messages.collect(::markVisibleUnreadMessages)
            }
        }
        connect()
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.InputChanged -> input.value = event.value
            ChatEvent.SendMessage -> sendMessage()
            ChatEvent.RetryConnection -> connect()
            ChatEvent.OpenProfile -> Unit
            ChatEvent.ChatOpened -> isChatVisible.value = true
            ChatEvent.ChatClosed -> {
                isChatVisible.value = false
                unreadAnchorId.value = null
            }
            ChatEvent.UnreadAnchorShown -> unreadAnchorId.value = null
            is ChatEvent.OpenUnreadMessage -> unreadAnchorId.value = event.messageId
        }
    }

    private suspend fun markVisibleUnreadMessages(messages: List<Message>) {
        val unread = messages.filter { !it.isFromCurrentDevice && it.readAt == null }
        if (unreadAnchorId.value == null) {
            unreadAnchorId.value = unread.firstOrNull()?.id
        }
        if (unread.isNotEmpty()) {
            chatRepository.markMessagesRead(unread.map(Message::id))
        }
    }

    private fun connect() {
        if (connectionJob?.isActive == true) return
        connectionJob = viewModelScope.launch {
            chatRepository.connect()
        }
    }

    private fun sendMessage() {
        val text = input.value.trim()
        val sender = userProfileRepository.senderName.value.trim()
        if (text.isEmpty() || sender.isEmpty() || isSending.value) return

        viewModelScope.launch {
            isSending.value = true
            val result = chatRepository.sendMessage(text = text, sender = sender)
            if (result.isSuccess) {
                input.value = ""
            }
            isSending.value = false
        }
    }
}
