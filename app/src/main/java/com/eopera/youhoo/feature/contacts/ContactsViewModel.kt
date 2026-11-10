package com.eopera.youhoo.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eopera.youhoo.domain.repository.ContactRepository
import com.eopera.youhoo.domain.repository.DevicePushRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val devicePushRepository: DevicePushRepository,
    userProfileRepository: UserProfileRepository
) : ViewModel() {
    private val status = MutableStateFlow(ContactListStatus.READY)

    val uiState = combine(
        contactRepository.contacts,
        userProfileRepository.userId,
        status
    ) { contacts, currentUserId, currentStatus ->
        ContactsUiState(
            contacts = contacts
                .filter { it.id != currentUserId }
                .map { ContactUi(id = it.id, displayName = it.displayName) },
            status = currentStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ContactsUiState()
    )

    init {
        refresh()
        viewModelScope.launch { devicePushRepository.registerDevice() }
    }

    fun onEvent(event: ContactsEvent) {
        when (event) {
            ContactsEvent.Refresh -> refresh()
            ContactsEvent.OpenProfile -> Unit
        }
    }

    private fun refresh() {
        if (status.value == ContactListStatus.LOADING) return
        viewModelScope.launch {
            status.value = ContactListStatus.LOADING
            status.value = if (contactRepository.refreshContacts().isSuccess) {
                ContactListStatus.READY
            } else {
                ContactListStatus.ERROR
            }
        }
    }
}

data class ContactsUiState(
    val contacts: List<ContactUi> = emptyList(),
    val status: ContactListStatus = ContactListStatus.LOADING
)

data class ContactUi(
    val id: String,
    val displayName: String
)

enum class ContactListStatus {
    LOADING,
    READY,
    ERROR
}

sealed interface ContactsEvent {
    data object Refresh : ContactsEvent
    data object OpenProfile : ContactsEvent
}
