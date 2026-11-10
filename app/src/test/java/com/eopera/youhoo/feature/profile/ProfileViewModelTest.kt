package com.eopera.youhoo.feature.profile

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
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var accountRepository: FakeProfileAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        profileRepository = FakeProfileRepository("Sam")
        accountRepository = FakeProfileAccountRepository(profileRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `profile starts with saved name`() = runTest {
        val viewModel = ProfileViewModel(accountRepository, profileRepository)
        collectState(viewModel)
        advanceUntilIdle()

        assertEquals("Sam", viewModel.uiState.value.name)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `changed name is trimmed and persisted remotely`() = runTest {
        val viewModel = ProfileViewModel(accountRepository, profileRepository)
        collectState(viewModel)

        viewModel.onEvent(ProfileEvent.NameChanged("  Alex  "))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.onEvent(ProfileEvent.SaveName)
        advanceUntilIdle()
        assertEquals("Alex", profileRepository.senderName.value)
        assertEquals(listOf("Alex"), accountRepository.updatedNames)
        assertEquals("Alex", viewModel.uiState.value.name)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `failed update preserves previous saved name`() = runTest {
        accountRepository.result = Result.failure(IllegalStateException("offline"))
        val viewModel = ProfileViewModel(accountRepository, profileRepository)
        collectState(viewModel)

        viewModel.onEvent(ProfileEvent.NameChanged("Alex"))
        viewModel.onEvent(ProfileEvent.SaveName)
        advanceUntilIdle()

        assertEquals("Sam", profileRepository.senderName.value)
        assertEquals("Alex", viewModel.uiState.value.name)
        assertTrue(viewModel.uiState.value.showError)
    }

    @Test
    fun `blank name cannot replace saved name`() = runTest {
        val viewModel = ProfileViewModel(accountRepository, profileRepository)
        collectState(viewModel)

        viewModel.onEvent(ProfileEvent.NameChanged("   "))
        viewModel.onEvent(ProfileEvent.SaveName)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canSave)
        assertEquals("Sam", profileRepository.senderName.value)
    }

    private fun TestScope.collectState(viewModel: ProfileViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }
}

private class FakeProfileRepository(initialName: String) : UserProfileRepository {
    private val mutableUserId = MutableStateFlow<String?>("user-1")
    private val mutableName = MutableStateFlow(initialName)
    override val userId: StateFlow<String?> = mutableUserId.asStateFlow()
    override val senderName: StateFlow<String> = mutableName.asStateFlow()

    override fun updateProfile(userId: String, name: String) {
        mutableUserId.value = userId
        mutableName.value = name
    }
}

private class FakeProfileAccountRepository(
    private val profileRepository: UserProfileRepository
) : AccountRepository {
    val updatedNames = mutableListOf<String>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun register(displayName: String): Result<Unit> = updateDisplayName(displayName)

    override suspend fun updateDisplayName(displayName: String): Result<Unit> {
        updatedNames += displayName
        if (result.isSuccess) profileRepository.updateProfile("user-1", displayName)
        return result
    }
}
