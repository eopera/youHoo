package com.eopera.youhoo.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eopera.youhoo.core.model.Contact
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
    private val unreadAnchorId = MutableStateFlow<String?>(null)
    private val contactName = MutableStateFlow("")
    private val isLoadingOlder = MutableStateFlow(false)
    private var connectionJob: Job? = null
    private var selectedContactId: String? = null

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
                val isMine = message.isFromCurrentUser ||
                    (message.senderId == null && trimmedSender.isNotEmpty() &&
                        message.sender == trimmedSender)
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

    val uiState = combine(
        baseUiState,
        unreadAnchorId,
        contactName,
        isLoadingOlder,
        chatRepository.hasOlderMessages
    ) { state, anchorId, currentContactName, loadingOlder, hasOlder ->
        state.copy(
            unreadAnchorId = anchorId,
            contactName = currentContactName,
            isLoadingOlder = loadingOlder,
            hasOlderMessages = hasOlder
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
        viewModelScope.launch {
            isChatVisible.collectLatest { visible ->
                if (!visible) return@collectLatest
                chatRepository.messages.collect(::markVisibleUnreadMessages)
            }
        }
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.InputChanged -> input.value = event.value
            ChatEvent.SendMessage -> sendMessage()
            ChatEvent.RetryConnection -> selectedContactId?.let(::connect)
            ChatEvent.LoadOlderMessages -> loadOlderMessages()
            ChatEvent.OpenProfile -> Unit
            ChatEvent.Back -> Unit
            is ChatEvent.ConversationSelected -> selectConversation(event.contact)
            ChatEvent.ChatOpened -> {
                isChatVisible.value = true
                if (connectionJob?.isActive != true) selectedContactId?.let(::connect)
            }
            ChatEvent.ChatClosed -> {
                isChatVisible.value = false
                unreadAnchorId.value = null
                connectionJob?.cancel()
            }
            ChatEvent.UnreadAnchorShown -> unreadAnchorId.value = null
            is ChatEvent.OpenUnreadMessage -> unreadAnchorId.value = event.messageId
        }
    }

    private fun selectConversation(contact: Contact) {
        if (selectedContactId == contact.id && connectionJob?.isActive == true) return
        connectionJob?.cancel()
        selectedContactId = contact.id
        contactName.value = contact.displayName
        unreadAnchorId.value = null
        input.value = ""
        connect(contact.id)
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

    private fun connect(contactId: String) {
        if (connectionJob?.isActive == true && selectedContactId == contactId) return
        connectionJob = viewModelScope.launch {
            chatRepository.connect(contactId)
        }
    }

    private fun loadOlderMessages() {
        if (isLoadingOlder.value || !chatRepository.hasOlderMessages.value) return
        viewModelScope.launch {
            isLoadingOlder.value = true
            chatRepository.loadOlderMessages()
            isLoadingOlder.value = false
        }
    }

    private fun sendMessage() {
        val text = input.value.trim()
        val sender = userProfileRepository.senderName.value.trim()
        if (text.isEmpty() || sender.isEmpty() || isSending.value) return

        viewModelScope.launch {
            isSending.value = true
            val result = chatRepository.sendMessage(text = text, sender = sender)
            if (result.isSuccess) input.value = ""
            isSending.value = false
        }
    }
}
