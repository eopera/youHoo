package com.eopera.youhoo.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eopera.youhoo.R
import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.MessageDeliveryStatus
import com.eopera.youhoo.ui.components.AppButton
import com.eopera.youhoo.ui.components.AppIconButton
import com.eopera.youhoo.ui.components.AppProfileIcon
import com.eopera.youhoo.ui.components.AppScaffold
import com.eopera.youhoo.ui.components.AppText
import com.eopera.youhoo.ui.components.MessageBubble
import com.eopera.youhoo.ui.components.MessageComposer
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
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(state.messages.size, state.unreadAnchorId) {
        val lastIndex = state.messages.lastIndex
        if (lastIndex < 0) return@LaunchedEffect

        val anchorId = state.unreadAnchorId
        if (!hasInitialScroll && anchorId != null) {
            val anchorIndex = state.messages.indexOfFirst { it.id == anchorId }
            listState.scrollToItem(anchorIndex.coerceAtLeast(0))
            hasInitialScroll = true
            onEvent(ChatEvent.UnreadAnchorShown)
            return@LaunchedEffect
        }

        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val isNearLatest = lastVisibleIndex >= lastIndex - 2
        if (!hasInitialScroll || isNearLatest || state.messages.last().isMine) {
            listState.animateScrollToItem(lastIndex)
            hasInitialScroll = true
        }
    }

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    AppScaffold(
        title = stringResource(R.string.chat_title),
        modifier = modifier,
        subtitle = { ConnectionStatusLabel(state.connectionStatus) },
        actions = {
            AppIconButton(
                icon = AppProfileIcon,
                contentDescription = stringResource(R.string.profile_title),
                onClick = { onEvent(ChatEvent.OpenProfile) },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (state.connectionStatus.isError) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.large, vertical = spacing.small)
                    ) {
                        AppText(
                            text = stringResource(state.connectionStatus.textResource),
                            modifier = Modifier.weight(1f)
                        )
                        if (state.connectionStatus == ConnectionStatus.CONNECTION_ERROR) {
                            AppButton(
                                text = stringResource(R.string.retry),
                                onClick = { onEvent(ChatEvent.RetryConnection) },
                                modifier = Modifier.padding(start = spacing.small)
                            )
                        }
                    }
                }
            }

            if (state.messages.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(spacing.large)
                ) {
                    AppText(
                        text = stringResource(R.string.empty_chat),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        horizontal = spacing.large,
                        vertical = spacing.medium
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
                        val previous = state.messages.getOrNull(index - 1)
                        val next = state.messages.getOrNull(index + 1)
                        MessageBubble(
                            text = message.text,
                            senderName = message.senderName,
                            time = message.time,
                            isMine = message.isMine,
                            deliveryStatus = message.deliveryStatus,
                            compact = compact,
                            isFirstInGroup = previous == null ||
                                previous.senderName != message.senderName ||
                                previous.isMine != message.isMine,
                            isLastInGroup = next == null ||
                                next.senderName != message.senderName ||
                                next.isMine != message.isMine
                        )
                    }
                }
            }

            MessageComposer(
                value = state.input,
                onValueChange = { onEvent(ChatEvent.InputChanged(it)) },
                onSend = { onEvent(ChatEvent.SendMessage) },
                placeholder = stringResource(R.string.message_hint),
                sendContentDescription = stringResource(
                    if (state.isSending) R.string.sending else R.string.send
                ),
                enabled = state.canSend
            )
        }
    }
}

@Composable
private fun ConnectionStatusLabel(status: ConnectionStatus) {
    val indicatorColor = when {
        status.isError -> MaterialTheme.colorScheme.error
        status == ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(6.dp)
                .background(indicatorColor, CircleShape)
        )
        AppText(
            text = stringResource(
                if (status.isError) R.string.status_offline else status.textResource
            ),
            small = true,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        MessageUi(2, "I made your favorite pasta", "Alex", "18:30", isMine = false),
        MessageUi(
            3,
            "On my way",
            "Sam",
            "18:31",
            isMine = true,
            deliveryStatus = MessageDeliveryStatus.READ
        )
    ),
    senderName = "Sam",
    input = "See you soon",
    connectionStatus = ConnectionStatus.CONNECTED,
    canSend = true
)
