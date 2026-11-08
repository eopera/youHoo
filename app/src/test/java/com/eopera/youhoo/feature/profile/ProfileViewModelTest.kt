package com.eopera.youhoo.feature.profile

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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        profileRepository = FakeProfileRepository("Sam")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `profile starts with saved name`() = runTest {
        val viewModel = ProfileViewModel(profileRepository)
        collectState(viewModel)
        advanceUntilIdle()

        assertEquals("Sam", viewModel.uiState.value.name)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `changed name is trimmed and persisted`() = runTest {
        val viewModel = ProfileViewModel(profileRepository)
        collectState(viewModel)

        viewModel.onEvent(ProfileEvent.NameChanged("  Alex  "))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.onEvent(ProfileEvent.SaveName)
        advanceUntilIdle()
        assertEquals("Alex", profileRepository.senderName.value)
        assertEquals("Alex", viewModel.uiState.value.name)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `blank name cannot replace saved name`() = runTest {
        val viewModel = ProfileViewModel(profileRepository)
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
    private val mutableName = MutableStateFlow(initialName)
    override val senderName: StateFlow<String> = mutableName.asStateFlow()

    override fun updateSenderName(name: String) {
        mutableName.value = name
    }
}
