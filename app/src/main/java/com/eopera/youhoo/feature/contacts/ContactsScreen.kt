package com.eopera.youhoo.feature.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eopera.youhoo.R
import com.eopera.youhoo.ui.components.AppButton
import com.eopera.youhoo.ui.components.AppIconButton
import com.eopera.youhoo.ui.components.AppProfileIcon
import com.eopera.youhoo.ui.components.AppScaffold
import com.eopera.youhoo.ui.components.AppText
import com.eopera.youhoo.ui.theme.AppTheme
import com.eopera.youhoo.ui.theme.YouHooTheme

@Composable
fun ContactsRoute(
    onOpenContact: (ContactUi) -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onEvent(ContactsEvent.Refresh) }
    ContactsScreen(
        state = state,
        onEvent = { event ->
            if (event == ContactsEvent.OpenProfile) onOpenProfile() else viewModel.onEvent(event)
        },
        onOpenContact = onOpenContact
    )
}

@Composable
fun ContactsScreen(
    state: ContactsUiState,
    onEvent: (ContactsEvent) -> Unit,
    onOpenContact: (ContactUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    AppScaffold(
        title = stringResource(R.string.contacts_title),
        modifier = modifier,
        actions = {
            AppIconButton(
                icon = AppProfileIcon,
                contentDescription = stringResource(R.string.profile_title),
                onClick = { onEvent(ContactsEvent.OpenProfile) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(spacing.large)
        ) {
            when (state.status) {
                ContactListStatus.LOADING -> CenteredContactsMessage(
                    text = stringResource(R.string.contacts_loading)
                )
                ContactListStatus.ERROR -> {
                    CenteredContactsMessage(text = stringResource(R.string.contacts_error))
                    AppButton(
                        text = stringResource(R.string.retry),
                        onClick = { onEvent(ContactsEvent.Refresh) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ContactListStatus.READY -> if (state.contacts.isEmpty()) {
                    CenteredContactsMessage(text = stringResource(R.string.contacts_empty))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        items(state.contacts, key = ContactUi::id) { contact ->
                            AppButton(
                                text = contact.displayName,
                                onClick = { onOpenContact(contact) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredContactsMessage(text: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        AppText(text = text)
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactsScreenPreview() {
    YouHooTheme {
        ContactsScreen(
            state = ContactsUiState(
                contacts = listOf(
                    ContactUi("1", "Alex"),
                    ContactUi("2", "Sam")
                ),
                status = ContactListStatus.READY
            ),
            onEvent = {},
            onOpenContact = {}
        )
    }
}
