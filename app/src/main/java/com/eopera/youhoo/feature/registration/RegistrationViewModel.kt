package com.eopera.youhoo.feature.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eopera.youhoo.domain.repository.AccountRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    userProfileRepository: UserProfileRepository
) : ViewModel() {
    private val name = MutableStateFlow(userProfileRepository.senderName.value)
    private val isRegistering = MutableStateFlow(false)
    private val showError = MutableStateFlow(false)

    val uiState = combine(name, isRegistering, showError) { currentName, registering, error ->
        RegistrationUiState(
            name = currentName,
            canRegister = currentName.trim().isNotEmpty() && !registering,
            isRegistering = registering,
            showError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RegistrationUiState()
    )

    fun onEvent(event: RegistrationEvent) {
        when (event) {
            is RegistrationEvent.NameChanged -> {
                name.value = event.value.take(UserProfileRepository.MAX_DISPLAY_NAME_LENGTH)
                showError.value = false
            }
            RegistrationEvent.Register -> register()
        }
    }

    private fun register() {
        val trimmedName = name.value.trim()
        if (trimmedName.isEmpty() || isRegistering.value) return
        viewModelScope.launch {
            isRegistering.value = true
            showError.value = false
            val result = accountRepository.register(trimmedName)
            if (result.isFailure) showError.value = true
            isRegistering.value = false
        }
    }
}

data class RegistrationUiState(
    val name: String = "",
    val canRegister: Boolean = false,
    val isRegistering: Boolean = false,
    val showError: Boolean = false
)

sealed interface RegistrationEvent {
    data class NameChanged(val value: String) : RegistrationEvent
    data object Register : RegistrationEvent
}
