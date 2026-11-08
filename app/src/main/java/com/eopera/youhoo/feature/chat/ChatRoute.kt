package com.eopera.youhoo.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatRoute(
    onOpenProfile: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (ChatEvent) -> Unit = { event ->
        if (event == ChatEvent.OpenProfile) onOpenProfile() else viewModel.onEvent(event)
    }
    when (viewModel.screenVariant) {
        ChatScreenVariant.CLASSIC -> ChatScreen(state, onEvent)
        ChatScreenVariant.COMPACT -> CompactChatScreen(state, onEvent)
    }
}
