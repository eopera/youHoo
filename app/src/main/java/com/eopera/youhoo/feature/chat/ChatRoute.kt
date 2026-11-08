package com.eopera.youhoo.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatRoute(viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (viewModel.screenVariant) {
        ChatScreenVariant.CLASSIC -> ChatScreen(state, viewModel::onEvent)
        ChatScreenVariant.COMPACT -> CompactChatScreen(state, viewModel::onEvent)
    }
}
