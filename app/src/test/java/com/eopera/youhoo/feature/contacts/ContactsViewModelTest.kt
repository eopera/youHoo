package com.eopera.youhoo.feature.contacts

import com.eopera.youhoo.core.model.Contact
import com.eopera.youhoo.domain.repository.ContactRepository
import com.eopera.youhoo.domain.repository.DevicePushRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var contactRepository: FakeContactRepository
    private lateinit var pushRepository: FakeContactsPushRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        contactRepository = FakeContactRepository()
        pushRepository = FakeContactsPushRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `directory loads all registered users except current user`() = runTest {
        contactRepository.mutableContacts.value = listOf(
            Contact("user-1", "Sam"),
            Contact("user-2", "Alex"),
            Contact("user-3", "Maria")
        )
        val viewModel = ContactsViewModel(
            contactRepository,
            pushRepository,
            FakeContactsProfileRepository()
        )
        collectState(viewModel)
        advanceUntilIdle()

        assertEquals(listOf("Alex", "Maria"), viewModel.uiState.value.contacts.map { it.displayName })
        assertEquals(ContactListStatus.READY, viewModel.uiState.value.status)
        assertEquals(1, pushRepository.registrations)
    }

    @Test
    fun `directory refresh failure is explicit`() = runTest {
        contactRepository.result = Result.failure(IllegalStateException("offline"))
        val viewModel = ContactsViewModel(
            contactRepository,
            pushRepository,
            FakeContactsProfileRepository()
        )
        collectState(viewModel)
        advanceUntilIdle()

        assertEquals(ContactListStatus.ERROR, viewModel.uiState.value.status)
        viewModel.onEvent(ContactsEvent.Refresh)
        advanceUntilIdle()
        assertTrue(contactRepository.refreshes >= 2)
    }

    private fun TestScope.collectState(viewModel: ContactsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }
}

private class FakeContactRepository : ContactRepository {
    val mutableContacts = MutableStateFlow<List<Contact>>(emptyList())
    override val contacts: StateFlow<List<Contact>> = mutableContacts.asStateFlow()
    var result: Result<Unit> = Result.success(Unit)
    var refreshes = 0

    override suspend fun refreshContacts(): Result<Unit> {
        refreshes += 1
        return result
    }
}

private class FakeContactsProfileRepository : UserProfileRepository {
    private val mutableUserId = MutableStateFlow<String?>("user-1")
    private val mutableName = MutableStateFlow("Sam")
    override val userId: StateFlow<String?> = mutableUserId.asStateFlow()
    override val senderName: StateFlow<String> = mutableName.asStateFlow()
    override fun updateProfile(userId: String, name: String) = Unit
}

private class FakeContactsPushRepository : DevicePushRepository {
    var registrations = 0
    override suspend fun registerDevice(): Result<Unit> {
        registrations += 1
        return Result.success(Unit)
    }
    override suspend fun registerToken(token: String): Result<Unit> = Result.success(Unit)
}
