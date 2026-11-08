package com.eopera.youhoo.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.eopera.youhoo.R
import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.ui.components.AppButton
import com.eopera.youhoo.ui.components.AppScaffold
import com.eopera.youhoo.ui.components.AppText
import com.eopera.youhoo.ui.components.AppTextField
import com.eopera.youhoo.ui.components.MessageBubble
import com.eopera.youhoo.ui.theme.AppTheme
import com.eopera.youhoo.ui.theme.YouHooTheme

@Composable
fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    ChatContent(state = state, onEvent = onEvent, compact = false, modifier = modifier)
}

@Composable
internal fun ChatContent(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    val listState = rememberLazyListState()
    var hasInitialScroll by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size) {
        val lastIndex = state.messages.lastIndex
        if (lastIndex < 0) return@LaunchedEffect

        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val isNearLatest = lastVisibleIndex >= lastIndex - 2
        if (!hasInitialScroll || isNearLatest || state.messages.last().isMine) {
            listState.animateScrollToItem(lastIndex)
            hasInitialScroll = true
        }
    }

    AppScaffold(title = stringResource(R.string.chat_title), modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = stringResource(state.connectionStatus.textResource),
                    small = true,
                    isError = state.connectionStatus.isError,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.small, vertical = spacing.extraSmall)
                )
                if (state.connectionStatus == ConnectionStatus.CONNECTION_ERROR) {
                    AppButton(
                        text = stringResource(R.string.retry),
                        onClick = { onEvent(ChatEvent.RetryConnection) }
                    )
                }
            }

            AppTextField(
                value = state.senderName,
                onValueChange = { onEvent(ChatEvent.SenderNameChanged(it)) },
                label = stringResource(R.string.your_name),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.small)
            )

            if (state.messages.isEmpty()) {
                AppText(
                    text = stringResource(R.string.empty_chat),
                    modifier = Modifier.padding(spacing.large)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        text = message.text,
                        senderName = message.senderName,
                        time = message.time,
                        isMine = message.isMine,
                        compact = compact
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.small)
            ) {
                AppTextField(
                    value = state.input,
                    onValueChange = { onEvent(ChatEvent.InputChanged(it)) },
                    placeholder = stringResource(R.string.message_hint),
                    modifier = Modifier.weight(1f)
                )
                AppButton(
                    text = stringResource(
                        if (state.isSending) R.string.sending else R.string.send
                    ),
                    onClick = { onEvent(ChatEvent.SendMessage) },
                    enabled = state.canSend
                )
            }
        }
    }
}

private val ConnectionStatus.textResource: Int
    get() = when (this) {
        ConnectionStatus.CONNECTING -> R.string.status_connecting
        ConnectionStatus.LOADING_HISTORY -> R.string.status_loading
        ConnectionStatus.CONNECTED -> R.string.status_connected
        ConnectionStatus.CONNECTION_ERROR -> R.string.status_connection_error
        ConnectionStatus.SEND_ERROR -> R.string.status_send_error
    }

private val ConnectionStatus.isError: Boolean
    get() = this == ConnectionStatus.CONNECTION_ERROR || this == ConnectionStatus.SEND_ERROR

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    YouHooTheme {
        ChatScreen(state = previewChatState, onEvent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatErrorPreview() {
    YouHooTheme {
        ChatScreen(
            state = previewChatState.copy(
                connectionStatus = ConnectionStatus.CONNECTION_ERROR
            ),
            onEvent = {}
        )
    }
}

internal val previewChatState = ChatUiState(
    messages = listOf(
        MessageUi(1, "Dinner is ready", "Alex", "18:30", isMine = false),
        MessageUi(2, "On my way", "Sam", "18:31", isMine = true)
    ),
    senderName = "Sam",
    input = "See you soon",
    connectionStatus = ConnectionStatus.CONNECTED,
    canSend = true
)
