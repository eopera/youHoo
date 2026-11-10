package com.eopera.youhoo.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eopera.youhoo.core.model.Contact

@Composable
fun ChatRoute(
    contact: Contact,
    onBack: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    pendingMessageId: String? = null,
    onPendingMessageHandled: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (ChatEvent) -> Unit = { event ->
        when (event) {
            ChatEvent.OpenProfile -> onOpenProfile()
            ChatEvent.Back -> onBack()
            else -> viewModel.onEvent(event)
        }
    }

    LaunchedEffect(contact.id) {
        viewModel.onEvent(ChatEvent.ConversationSelected(contact))
    }
    LaunchedEffect(pendingMessageId) {
        pendingMessageId?.let { onEvent(ChatEvent.OpenUnreadMessage(it)) }
        if (pendingMessageId != null) onPendingMessageHandled()
    }
    LaunchedEffect(Unit) { onEvent(ChatEvent.ChatOpened) }
    DisposableEffect(Unit) {
        onDispose { onEvent(ChatEvent.ChatClosed) }
    }

    key(contact.id) {
        when (viewModel.screenVariant) {
            ChatScreenVariant.CLASSIC -> ChatScreen(state, onEvent)
            ChatScreenVariant.COMPACT -> CompactChatScreen(state, onEvent)
        }
    }
}
