package com.eopera.youhoo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.feature.chat.ChatEvent
import com.eopera.youhoo.feature.chat.ChatUiState
import com.eopera.youhoo.feature.chat.CompactChatScreen
import com.eopera.youhoo.feature.chat.MessageUi
import com.eopera.youhoo.feature.chat.ChatScreen
import com.eopera.youhoo.ui.theme.YouHooTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = ChatUiState(
        messages = listOf(
            MessageUi(1, "Hello family", "Alex", "12:30", isMine = false),
            MessageUi(2, "On my way", "Sam", "12:31", isMine = true)
        ),
        senderName = "Sam",
        input = "Draft",
        connectionStatus = ConnectionStatus.CONNECTED,
        canSend = true
    )

    @Test
    fun classicScreenEmitsSendWithoutRepository() {
        val events = mutableListOf<ChatEvent>()
        composeRule.setContent {
            YouHooTheme { ChatScreen(state, events::add) }
        }

        composeRule.onNodeWithText("Send").assertIsEnabled().performClick()

        assertEquals(listOf<ChatEvent>(ChatEvent.SendMessage), events)
    }

    @Test
    fun bothVariantsRenderTheSameState() {
        composeRule.setContent {
            YouHooTheme { CompactChatScreen(state, onEvent = {}) }
        }

        composeRule.onNodeWithText("Hello family").assertIsDisplayed()
        composeRule.onNodeWithText("On my way").assertIsDisplayed()
    }
}
