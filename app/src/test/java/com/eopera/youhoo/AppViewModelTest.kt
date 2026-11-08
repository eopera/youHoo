package com.eopera.youhoo

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
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var profileRepository: FakeAppProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        profileRepository = FakeAppProfileRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank saved name requires registration`() = runTest {
        val viewModel = AppViewModel(profileRepository)
        collectState(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRegistered)
    }

    @Test
    fun `saved name opens chat without registration`() = runTest {
        profileRepository.updateSenderName("Sam")
        val viewModel = AppViewModel(profileRepository)
        collectState(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRegistered)
        assertEquals(AppSection.CHAT, viewModel.uiState.value.section)
    }

    @Test
    fun `profile can be opened and closed`() = runTest {
        profileRepository.updateSenderName("Sam")
        val viewModel = AppViewModel(profileRepository)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(AppEvent.OpenProfile)
        advanceUntilIdle()
        assertEquals(AppSection.PROFILE, viewModel.uiState.value.section)

        viewModel.onEvent(AppEvent.CloseProfile)
        advanceUntilIdle()
        assertEquals(AppSection.CHAT, viewModel.uiState.value.section)
    }

    private fun TestScope.collectState(viewModel: AppViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }
}

private class FakeAppProfileRepository : UserProfileRepository {
    private val mutableName = MutableStateFlow("")
    override val senderName: StateFlow<String> = mutableName.asStateFlow()

    override fun updateSenderName(name: String) {
        mutableName.value = name
    }
}
