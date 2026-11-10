package com.eopera.youhoo.feature.registration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eopera.youhoo.R
import com.eopera.youhoo.ui.components.AppButton
import com.eopera.youhoo.ui.components.AppScaffold
import com.eopera.youhoo.ui.components.AppText
import com.eopera.youhoo.ui.components.AppTextField
import com.eopera.youhoo.ui.theme.AppTheme
import com.eopera.youhoo.ui.theme.YouHooTheme

@Composable
fun RegistrationRoute(viewModel: RegistrationViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RegistrationScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun RegistrationScreen(
    state: RegistrationUiState,
    onEvent: (RegistrationEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    AppScaffold(title = stringResource(R.string.app_name), modifier = modifier) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.medium, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(spacing.large)
        ) {
            AppText(text = stringResource(R.string.registration_title))
            AppText(text = stringResource(R.string.registration_description))
            AppTextField(
                value = state.name,
                onValueChange = { onEvent(RegistrationEvent.NameChanged(it)) },
                label = stringResource(R.string.your_name),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.showError) {
                AppText(
                    text = stringResource(R.string.registration_error),
                    isError = true
                )
            }
            AppButton(
                text = stringResource(
                    if (state.isRegistering) R.string.registration_registering
                    else R.string.registration_continue
                ),
                onClick = { onEvent(RegistrationEvent.Register) },
                enabled = state.canRegister,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegistrationScreenPreview() {
    YouHooTheme {
        RegistrationScreen(
            state = RegistrationUiState(name = "Sam", canRegister = true),
            onEvent = {}
        )
    }
}
