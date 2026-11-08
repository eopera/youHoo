package com.eopera.youhoo.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.eopera.youhoo.ui.theme.YouHooTheme

/** Alternative presentation using the exact same state and event contract. */
@Composable
fun CompactChatScreen(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    ChatContent(state = state, onEvent = onEvent, compact = true, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun CompactChatScreenPreview() {
    YouHooTheme {
        CompactChatScreen(state = previewChatState, onEvent = {})
    }
}
