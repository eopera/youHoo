package com.eopera.youhoo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.feature.chat.ChatEvent
import com.eopera.youhoo.feature.chat.ChatScreen
import com.eopera.youhoo.feature.chat.ChatUiState
import com.eopera.youhoo.feature.chat.CompactChatScreen
import com.eopera.youhoo.feature.chat.MessageUi
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
    fun chatScreenEmitsSendWithoutRepository() {
        val events = mutableListOf<ChatEvent>()
        composeRule.setContent {
            YouHooTheme { ChatScreen(state, events::add) }
        }

        composeRule.onNodeWithContentDescription("Send").assertIsEnabled().performClick()

        assertEquals(listOf<ChatEvent>(ChatEvent.SendMessage), events)
    }

    @Test
    fun profileActionEmitsNavigationEvent() {
        val events = mutableListOf<ChatEvent>()
        composeRule.setContent {
            YouHooTheme { ChatScreen(state, events::add) }
        }

        composeRule.onNodeWithContentDescription("Profile").performClick()

        assertEquals(listOf<ChatEvent>(ChatEvent.OpenProfile), events)
    }

    @Test
    fun consecutiveMessagesAreGrouped() {
        composeRule.setContent {
            YouHooTheme {
                ChatScreen(
                    state = state.copy(
                        messages = listOf(
                            MessageUi(1, "Hello family", "Alex", "12:30", isMine = false),
                            MessageUi(2, "How are you?", "Alex", "12:31", isMine = false),
                            MessageUi(3, "On my way", "Sam", "12:32", isMine = true)
                        )
                    ),
                    onEvent = {}
                )
            }
        }

        composeRule.onAllNodesWithText("Alex").assertCountEquals(1)
        composeRule.onAllNodesWithText("12:30").assertCountEquals(0)
        composeRule.onNodeWithText("12:31").assertIsDisplayed()
        composeRule.onNodeWithText("12:32").assertIsDisplayed()
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
