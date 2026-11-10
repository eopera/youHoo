package com.eopera.youhoo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eopera.youhoo.feature.contacts.ContactListStatus
import com.eopera.youhoo.feature.contacts.ContactUi
import com.eopera.youhoo.feature.contacts.ContactsEvent
import com.eopera.youhoo.feature.contacts.ContactsScreen
import com.eopera.youhoo.feature.contacts.ContactsUiState
import com.eopera.youhoo.ui.theme.YouHooTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ContactsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val contact = ContactUi("user-2", "Alex")
    private val state = ContactsUiState(
        contacts = listOf(contact),
        status = ContactListStatus.READY
    )

    @Test
    fun registeredContactOpensPersonalChat() {
        val opened = mutableListOf<ContactUi>()
        composeRule.setContent {
            YouHooTheme {
                ContactsScreen(state = state, onEvent = {}, onOpenContact = opened::add)
            }
        }

        composeRule.onNodeWithText("Alex").performClick()

        assertEquals(listOf(contact), opened)
    }

    @Test
    fun profileActionEmitsNavigationEvent() {
        val events = mutableListOf<ContactsEvent>()
        composeRule.setContent {
            YouHooTheme {
                ContactsScreen(state = state, onEvent = events::add, onOpenContact = {})
            }
        }

        composeRule.onNodeWithContentDescription("Profile").performClick()

        assertEquals(listOf<ContactsEvent>(ContactsEvent.OpenProfile), events)
    }
}
