package com.eopera.youhoo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eopera.youhoo.feature.profile.ProfileEvent
import com.eopera.youhoo.feature.profile.ProfileScreen
import com.eopera.youhoo.feature.profile.ProfileUiState
import com.eopera.youhoo.ui.theme.YouHooTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileEmitsSaveAndBackEvents() {
        val events = mutableListOf<ProfileEvent>()
        composeRule.setContent {
            YouHooTheme {
                ProfileScreen(
                    state = ProfileUiState(name = "Alex", canSave = true),
                    onEvent = events::add
                )
            }
        }

        composeRule.onNodeWithText("Save").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Back").performClick()

        assertEquals(listOf(ProfileEvent.SaveName, ProfileEvent.Back), events)
    }
}
