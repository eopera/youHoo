package com.eopera.youhoo.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatRoute(
    onOpenProfile: () -> Unit = {},
    pendingMessageId: Long? = null,
    onPendingMessageHandled: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (ChatEvent) -> Unit = { event ->
        if (event == ChatEvent.OpenProfile) onOpenProfile() else viewModel.onEvent(event)
    }

    LaunchedEffect(pendingMessageId) {
        pendingMessageId?.let { onEvent(ChatEvent.OpenUnreadMessage(it)) }
        onEvent(ChatEvent.ChatOpened)
        if (pendingMessageId != null) onPendingMessageHandled()
    }
    DisposableEffect(Unit) {
        onDispose { onEvent(ChatEvent.ChatClosed) }
    }

    when (viewModel.screenVariant) {
        ChatScreenVariant.CLASSIC -> ChatScreen(state, onEvent)
        ChatScreenVariant.COMPACT -> CompactChatScreen(state, onEvent)
    }
}
