package com.eopera.youhoo.feature.registration

import com.eopera.youhoo.domain.repository.AccountRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var profileRepository: FakeRegistrationProfileRepository
    private lateinit var accountRepository: FakeRegistrationAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        profileRepository = FakeRegistrationProfileRepository()
        accountRepository = FakeRegistrationAccountRepository(profileRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank name cannot register`() = runTest {
        val viewModel = RegistrationViewModel(accountRepository, profileRepository)
        collectState(viewModel)

        viewModel.onEvent(RegistrationEvent.NameChanged("   "))
        viewModel.onEvent(RegistrationEvent.Register)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canRegister)
        assertEquals("", profileRepository.senderName.value)
        assertTrue(accountRepository.registeredNames.isEmpty())
    }

    @Test
    fun `registration trims name and persists remote identity`() = runTest {
        val viewModel = RegistrationViewModel(accountRepository, profileRepository)
        collectState(viewModel)

        viewModel.onEvent(RegistrationEvent.NameChanged("  Sam  "))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canRegister)

        viewModel.onEvent(RegistrationEvent.Register)
        advanceUntilIdle()
        assertEquals("Sam", profileRepository.senderName.value)
        assertEquals("user-1", profileRepository.userId.value)
        assertEquals(listOf("Sam"), accountRepository.registeredNames)
    }

    @Test
    fun `failed remote registration does not persist local profile`() = runTest {
        accountRepository.result = Result.failure(IllegalStateException("offline"))
        val viewModel = RegistrationViewModel(accountRepository, profileRepository)
        collectState(viewModel)

        viewModel.onEvent(RegistrationEvent.NameChanged("Sam"))
        viewModel.onEvent(RegistrationEvent.Register)
        advanceUntilIdle()

        assertEquals(null, profileRepository.userId.value)
        assertTrue(viewModel.uiState.value.showError)
        assertTrue(viewModel.uiState.value.canRegister)
    }

    @Test
    fun `name input is length limited`() = runTest {
        val viewModel = RegistrationViewModel(accountRepository, profileRepository)
        collectState(viewModel)

        viewModel.onEvent(RegistrationEvent.NameChanged("a".repeat(60)))
        advanceUntilIdle()

        assertEquals(
            UserProfileRepository.MAX_DISPLAY_NAME_LENGTH,
            viewModel.uiState.value.name.length
        )
    }

    private fun TestScope.collectState(viewModel: RegistrationViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }
}

private class FakeRegistrationProfileRepository : UserProfileRepository {
    private val mutableUserId = MutableStateFlow<String?>(null)
    private val mutableName = MutableStateFlow("")
    override val userId: StateFlow<String?> = mutableUserId.asStateFlow()
    override val senderName: StateFlow<String> = mutableName.asStateFlow()

    override fun updateProfile(userId: String, name: String) {
        mutableUserId.value = userId
        mutableName.value = name
    }
}

private class FakeRegistrationAccountRepository(
    private val profileRepository: UserProfileRepository
) : AccountRepository {
    val registeredNames = mutableListOf<String>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun register(displayName: String): Result<Unit> {
        registeredNames += displayName
        if (result.isSuccess) profileRepository.updateProfile("user-1", displayName)
        return result
    }

    override suspend fun updateDisplayName(displayName: String): Result<Unit> = register(displayName)
}
