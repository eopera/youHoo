package com.eopera.youhoo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eopera.youhoo.feature.registration.RegistrationEvent
import com.eopera.youhoo.feature.registration.RegistrationScreen
import com.eopera.youhoo.feature.registration.RegistrationUiState
import com.eopera.youhoo.ui.theme.YouHooTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegistrationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun blankNameDisablesRegistration() {
        composeRule.setContent {
            YouHooTheme { RegistrationScreen(RegistrationUiState(), onEvent = {}) }
        }

        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun validNameEmitsRegistration() {
        val events = mutableListOf<RegistrationEvent>()
        composeRule.setContent {
            YouHooTheme {
                RegistrationScreen(
                    state = RegistrationUiState(name = "Sam", canRegister = true),
                    onEvent = events::add
                )
            }
        }

        composeRule.onNodeWithText("Continue").assertIsEnabled().performClick()

        assertEquals(listOf<RegistrationEvent>(RegistrationEvent.Register), events)
    }
}
