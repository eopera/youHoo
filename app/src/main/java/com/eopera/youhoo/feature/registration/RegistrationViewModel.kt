package com.eopera.youhoo.feature.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eopera.youhoo.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {
    private val name = MutableStateFlow("")

    val uiState = name.map { currentName ->
        RegistrationUiState(
            name = currentName,
            canRegister = currentName.trim().isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RegistrationUiState()
    )

    fun onEvent(event: RegistrationEvent) {
        when (event) {
            is RegistrationEvent.NameChanged -> name.value = event.value
                .take(UserProfileRepository.MAX_DISPLAY_NAME_LENGTH)
            RegistrationEvent.Register -> register()
        }
    }

    private fun register() {
        val trimmedName = name.value.trim()
        if (trimmedName.isEmpty()) return
        userProfileRepository.updateSenderName(trimmedName)
    }
}

data class RegistrationUiState(
    val name: String = "",
    val canRegister: Boolean = false
)

sealed interface RegistrationEvent {
    data class NameChanged(val value: String) : RegistrationEvent
    data object Register : RegistrationEvent
}
