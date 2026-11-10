package com.eopera.youhoo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eopera.youhoo.core.model.Contact
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
    private val section = MutableStateFlow(AppSection.CONTACTS)
    private val selectedContact = MutableStateFlow<Contact?>(null)
    private var profileReturnSection = AppSection.CONTACTS

    val uiState = combine(
        userProfileRepository.userId,
        userProfileRepository.senderName,
        section,
        selectedContact
    ) { userId, name, currentSection, contact ->
        val isRegistered = !userId.isNullOrBlank() && name.trim().isNotEmpty()
        AppUiState(
            isRegistered = isRegistered,
            section = if (isRegistered) currentSection else AppSection.CONTACTS,
            selectedContact = contact
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(
            isRegistered = !userProfileRepository.userId.value.isNullOrBlank() &&
                userProfileRepository.senderName.value.trim().isNotEmpty()
        )
    )

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.OpenChat -> {
                selectedContact.value = event.contact
                section.value = AppSection.CHAT
            }
            AppEvent.CloseChat -> {
                selectedContact.value = null
                section.value = AppSection.CONTACTS
            }
            AppEvent.OpenProfile -> {
                profileReturnSection = section.value
                section.value = AppSection.PROFILE
            }
            AppEvent.CloseProfile -> section.value = profileReturnSection
        }
    }
}

data class AppUiState(
    val isRegistered: Boolean = false,
    val section: AppSection = AppSection.CONTACTS,
    val selectedContact: Contact? = null
)

enum class AppSection {
    CONTACTS,
    CHAT,
    PROFILE
}

sealed interface AppEvent {
    data class OpenChat(val contact: Contact) : AppEvent
    data object CloseChat : AppEvent
    data object OpenProfile : AppEvent
    data object CloseProfile : AppEvent
}
