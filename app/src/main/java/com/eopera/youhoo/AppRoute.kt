package com.eopera.youhoo

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eopera.youhoo.core.model.Contact
import com.eopera.youhoo.feature.chat.ChatRoute
import com.eopera.youhoo.feature.contacts.ContactsRoute
import com.eopera.youhoo.feature.profile.ProfileRoute
import com.eopera.youhoo.feature.registration.RegistrationRoute

@Composable
fun AppRoute(
    onRegistered: () -> Unit = {},
    pendingMessageId: String? = null,
    pendingContactId: String? = null,
    pendingContactName: String? = null,
    onPendingMessageHandled: () -> Unit = {},
    viewModel: AppViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isRegistered) {
        if (state.isRegistered) onRegistered()
    }
    LaunchedEffect(state.isRegistered, pendingContactId) {
        if (state.isRegistered && pendingContactId != null && !pendingContactName.isNullOrBlank()) {
            viewModel.onEvent(
                AppEvent.OpenChat(Contact(pendingContactId, pendingContactName))
            )
        }
    }
    BackHandler(enabled = state.isRegistered && state.section != AppSection.CONTACTS) {
        viewModel.onEvent(
            if (state.section == AppSection.PROFILE) AppEvent.CloseProfile else AppEvent.CloseChat
        )
    }
    when {
        !state.isRegistered -> RegistrationRoute()
        state.section == AppSection.PROFILE -> ProfileRoute(
            onBack = { viewModel.onEvent(AppEvent.CloseProfile) }
        )
        state.section == AppSection.CHAT && state.selectedContact != null -> ChatRoute(
            contact = state.selectedContact!!,
            onBack = { viewModel.onEvent(AppEvent.CloseChat) },
            onOpenProfile = { viewModel.onEvent(AppEvent.OpenProfile) },
            pendingMessageId = pendingMessageId,
            onPendingMessageHandled = onPendingMessageHandled
        )
        else -> ContactsRoute(
            onOpenContact = { contact ->
                viewModel.onEvent(
                    AppEvent.OpenChat(Contact(contact.id, contact.displayName))
                )
            },
            onOpenProfile = { viewModel.onEvent(AppEvent.OpenProfile) }
        )
    }
}
