package com.eopera.youhoo.feature.profile

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
class ProfileViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    userProfileRepository: UserProfileRepository
) : ViewModel() {
    private val name = MutableStateFlow(userProfileRepository.senderName.value)
    private val savedName = MutableStateFlow(userProfileRepository.senderName.value)
    private val isSaving = MutableStateFlow(false)
    private val showError = MutableStateFlow(false)

    val uiState = combine(
        name,
        savedName,
        isSaving,
        showError
    ) { currentName, currentSavedName, saving, error ->
        val trimmedName = currentName.trim()
        ProfileUiState(
            name = currentName,
            canSave = trimmedName.isNotEmpty() && trimmedName != currentSavedName && !saving,
            isSaving = saving,
            showError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(name = userProfileRepository.senderName.value)
    )

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.NameChanged -> {
                name.value = event.value.take(UserProfileRepository.MAX_DISPLAY_NAME_LENGTH)
                showError.value = false
            }
            ProfileEvent.SaveName -> saveName()
            ProfileEvent.Back -> Unit
        }
    }

    private fun saveName() {
        val trimmedName = name.value.trim()
        if (trimmedName.isEmpty() || trimmedName == savedName.value || isSaving.value) return
        viewModelScope.launch {
            isSaving.value = true
            showError.value = false
            val result = accountRepository.updateDisplayName(trimmedName)
            if (result.isSuccess) {
                savedName.value = trimmedName
                name.value = trimmedName
            } else {
                showError.value = true
            }
            isSaving.value = false
        }
    }
}

data class ProfileUiState(
    val name: String = "",
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val showError: Boolean = false
)

sealed interface ProfileEvent {
    data class NameChanged(val value: String) : ProfileEvent
    data object SaveName : ProfileEvent
    data object Back : ProfileEvent
}
