package com.eopera.youhoo

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eopera.youhoo.feature.chat.ChatRoute
import com.eopera.youhoo.feature.profile.ProfileRoute
import com.eopera.youhoo.feature.registration.RegistrationRoute

@Composable
fun AppRoute(
    onRegistered: () -> Unit = {},
    pendingMessageId: Long? = null,
    onPendingMessageHandled: () -> Unit = {},
    viewModel: AppViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isRegistered) {
        if (state.isRegistered) onRegistered()
    }
    BackHandler(enabled = state.isRegistered && state.section == AppSection.PROFILE) {
        viewModel.onEvent(AppEvent.CloseProfile)
    }
    when {
        !state.isRegistered -> RegistrationRoute()
        state.section == AppSection.PROFILE -> ProfileRoute(
            onBack = { viewModel.onEvent(AppEvent.CloseProfile) }
        )
        else -> ChatRoute(
            onOpenProfile = { viewModel.onEvent(AppEvent.OpenProfile) },
            pendingMessageId = pendingMessageId,
            onPendingMessageHandled = onPendingMessageHandled
        )
    }
}
