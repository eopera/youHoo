package com.eopera.youhoo.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eopera.youhoo.domain.repository.ChatRepository
import com.eopera.youhoo.domain.repository.DevicePushRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    private var connectionJob: Job? = null

    val uiState = combine(
        chatRepository.messages,
        chatRepository.connectionStatus,
        userProfileRepository.senderName,
        input,
        isSending
    ) { messages, status, senderName, currentInput, sending ->
        val trimmedSender = senderName.trim()
        ChatUiState(
            messages = messages.map { message ->
                MessageUi(
                    id = message.id,
                    text = message.text,
                    senderName = message.sender,
                    time = timeFormatter.format(message.createdAt),
                    isMine = trimmedSender.isNotEmpty() && message.sender == trimmedSender
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

    init {
        viewModelScope.launch {
            devicePushRepository.registerDevice()
        }
        connect()
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.InputChanged -> input.value = event.value
            ChatEvent.SendMessage -> sendMessage()
            ChatEvent.RetryConnection -> connect()
            ChatEvent.OpenProfile -> Unit
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
