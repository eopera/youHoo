package com.eopera.youhoo.feature.chat

import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.Message
import com.eopera.youhoo.domain.repository.ChatRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var chatRepository: FakeChatRepository
    private lateinit var profileRepository: FakeUserProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        chatRepository = FakeChatRepository()
        profileRepository = FakeUserProfileRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state maps messages and ownership without backend types`() = runTest {
        profileRepository.setName("Sam")
        chatRepository.mutableMessages.value = listOf(
            Message(1, "Hi", "Sam", "2024-01-01T12:34:56+00:00"),
            Message(2, "Hello", "Alex", "2024-01-01T12:35:56+00:00")
        )
        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.messages[0].isMine)
        assertFalse(state.messages[1].isMine)
        assertEquals("Sam", state.senderName)
        assertEquals(2, state.messages.size)
    }

    @Test
    fun `successful send trims values and clears input`() = runTest {
        profileRepository.setName(" Sam ")
        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(ChatEvent.InputChanged("  Hello  "))
        viewModel.onEvent(ChatEvent.SendMessage)
        advanceUntilIdle()

        assertEquals("Hello" to "Sam", chatRepository.sent.single())
        assertEquals("", viewModel.uiState.value.input)
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun `failed send preserves draft`() = runTest {
        profileRepository.setName("Sam")
        chatRepository.sendResult = Result.failure(IllegalStateException("offline"))
        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(ChatEvent.InputChanged("Draft"))
        viewModel.onEvent(ChatEvent.SendMessage)
        advanceUntilIdle()

        assertEquals("Draft", viewModel.uiState.value.input)
        assertEquals(ConnectionStatus.SEND_ERROR, viewModel.uiState.value.connectionStatus)
    }

    @Test
    fun `blank message is not sent`() = runTest {
        profileRepository.setName("Sam")
        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(ChatEvent.InputChanged("   "))
        viewModel.onEvent(ChatEvent.SendMessage)
        advanceUntilIdle()

        assertTrue(chatRepository.sent.isEmpty())
        assertFalse(viewModel.uiState.value.canSend)
    }

    private fun createViewModel() = ChatViewModel(
        chatRepository = chatRepository,
        userProfileRepository = profileRepository,
        timeFormatter = MessageTimeFormatter(),
        featureFlagProvider = FakeFeatureFlagProvider()
    )

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: ChatViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }
}

private class FakeChatRepository : ChatRepository {
    val mutableMessages = MutableStateFlow<List<Message>>(emptyList())
    private val mutableStatus = MutableStateFlow(ConnectionStatus.CONNECTED)
    val sent = mutableListOf<Pair<String, String>>()
    var sendResult: Result<Unit> = Result.success(Unit)

    override val messages: StateFlow<List<Message>> = mutableMessages.asStateFlow()
    override val connectionStatus: StateFlow<ConnectionStatus> = mutableStatus.asStateFlow()

    override suspend fun connect() = Unit

    override suspend fun sendMessage(text: String, sender: String): Result<Unit> {
        sent += text to sender
        if (sendResult.isFailure) mutableStatus.value = ConnectionStatus.SEND_ERROR
        return sendResult
    }
}

private class FakeUserProfileRepository : UserProfileRepository {
    private val mutableName = MutableStateFlow("")
    override val senderName: StateFlow<String> = mutableName.asStateFlow()
    override fun updateSenderName(name: String) {
        mutableName.value = name
    }
    fun setName(name: String) = updateSenderName(name)
}

private class FakeFeatureFlagProvider : FeatureFlagProvider {
    override val chatScreenVariant = ChatScreenVariant.CLASSIC
}
