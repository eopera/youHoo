package com.eopera.youhoo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eopera.youhoo.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    userProfileRepository: UserProfileRepository
) : ViewModel() {
    private val section = MutableStateFlow(AppSection.CHAT)

    val uiState = combine(
        userProfileRepository.senderName,
        section
    ) { name, currentSection ->
        AppUiState(
            isRegistered = name.trim().isNotEmpty(),
            section = if (name.trim().isEmpty()) AppSection.CHAT else currentSection
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(
            isRegistered = userProfileRepository.senderName.value.trim().isNotEmpty()
        )
    )

    fun onEvent(event: AppEvent) {
        section.value = when (event) {
            AppEvent.OpenProfile -> AppSection.PROFILE
            AppEvent.CloseProfile -> AppSection.CHAT
        }
    }
}

data class AppUiState(
    val isRegistered: Boolean = false,
    val section: AppSection = AppSection.CHAT
)

enum class AppSection {
    CHAT,
    PROFILE
}

sealed interface AppEvent {
    data object OpenProfile : AppEvent
    data object CloseProfile : AppEvent
}
