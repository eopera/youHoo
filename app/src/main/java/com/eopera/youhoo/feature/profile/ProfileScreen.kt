package com.eopera.youhoo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eopera.youhoo.R
import com.eopera.youhoo.ui.components.AppBackIcon
import com.eopera.youhoo.ui.components.AppButton
import com.eopera.youhoo.ui.components.AppIconButton
import com.eopera.youhoo.ui.components.AppScaffold
import com.eopera.youhoo.ui.components.AppTextField
import com.eopera.youhoo.ui.theme.AppTheme
import com.eopera.youhoo.ui.theme.YouHooTheme

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onEvent = { event ->
            if (event == ProfileEvent.Back) onBack() else viewModel.onEvent(event)
        }
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    AppScaffold(
        title = stringResource(R.string.profile_title),
        modifier = modifier,
        navigationIcon = {
            AppIconButton(
                icon = AppBackIcon,
                contentDescription = stringResource(R.string.back),
                onClick = { onEvent(ProfileEvent.Back) }
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(spacing.large)
        ) {
            AppTextField(
                value = state.name,
                onValueChange = { onEvent(ProfileEvent.NameChanged(it)) },
                label = stringResource(R.string.your_name),
                modifier = Modifier.fillMaxWidth()
            )
            AppButton(
                text = stringResource(R.string.profile_save),
                onClick = { onEvent(ProfileEvent.SaveName) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    YouHooTheme {
        ProfileScreen(
            state = ProfileUiState(name = "Sam", canSave = true),
            onEvent = {}
        )
    }
}
