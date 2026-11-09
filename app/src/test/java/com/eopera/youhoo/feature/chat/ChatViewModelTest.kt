package com.eopera.youhoo.feature.chat

import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.Message
import com.eopera.youhoo.core.model.MessageDeliveryStatus
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
    private lateinit var devicePushRepository: FakeDevicePushRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        chatRepository = FakeChatRepository()
        profileRepository = FakeUserProfileRepository()
        devicePushRepository = FakeDevicePushRepository()
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
    fun `delivery timestamps map to outgoing check mark states`() = runTest {
        profileRepository.setName("Sam")
        chatRepository.mutableMessages.value = listOf(
            Message(1, "Sent", "Sam", "2024-01-01T12:30:00+00:00"),
            Message(
                2,
                "Delivered",
                "Sam",
                "2024-01-01T12:31:00+00:00",
                deliveredAt = "2024-01-01T12:31:05+00:00"
            ),
            Message(
                3,
                "Read",
                "Sam",
                "2024-01-01T12:32:00+00:00",
                deliveredAt = "2024-01-01T12:32:05+00:00",
                readAt = "2024-01-01T12:32:10+00:00"
            )
        )
        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        assertEquals(
            listOf(
                MessageDeliveryStatus.SENT,
                MessageDeliveryStatus.DELIVERED,
                MessageDeliveryStatus.READ
            ),
            viewModel.uiState.value.messages.map { it.deliveryStatus }
        )
    }

    @Test
    fun `opening chat anchors and marks unread incoming messages read`() = runTest {
        chatRepository.mutableMessages.value = listOf(
            Message(1, "First", "Alex", "2024-01-01T12:30:00+00:00"),
            Message(2, "Second", "Alex", "2024-01-01T12:31:00+00:00"),
            Message(
                3,
                "Mine",
                "Sam",
                "2024-01-01T12:32:00+00:00",
                isFromCurrentDevice = true
            )
        )
        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(ChatEvent.ChatOpened)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), chatRepository.readMarked.single())
        assertEquals(1L, viewModel.uiState.value.unreadAnchorId)

        viewModel.onEvent(ChatEvent.UnreadAnchorShown)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.unreadAnchorId)
    }

    @Test
    fun `push message id overrides the first unread anchor`() = runTest {
        chatRepository.mutableMessages.value = listOf(
            Message(1, "First", "Alex", "2024-01-01T12:30:00+00:00"),
            Message(2, "Second", "Alex", "2024-01-01T12:31:00+00:00")
        )
        val viewModel = createViewModel()
        collectState(viewModel)
        viewModel.onEvent(ChatEvent.ChatOpened)
        advanceUntilIdle()

        viewModel.onEvent(ChatEvent.OpenUnreadMessage(2))
        advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.unreadAnchorId)
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

    @Test
    fun `chat starts push registration`() = runTest {
        createViewModel()
        advanceUntilIdle()

        assertEquals(1, devicePushRepository.registrations)
    }

    private fun createViewModel() = ChatViewModel(
        chatRepository = chatRepository,
        userProfileRepository = profileRepository,
        devicePushRepository = devicePushRepository,
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
    val deliveredMarked = mutableListOf<List<Long>>()
    val readMarked = mutableListOf<List<Long>>()
    var sendResult: Result<Unit> = Result.success(Unit)

    override val messages: StateFlow<List<Message>> = mutableMessages.asStateFlow()
    override val connectionStatus: StateFlow<ConnectionStatus> = mutableStatus.asStateFlow()

    override suspend fun connect() = Unit

    override suspend fun sendMessage(text: String, sender: String): Result<Unit> {
        sent += text to sender
        if (sendResult.isFailure) mutableStatus.value = ConnectionStatus.SEND_ERROR
        return sendResult
    }

    override suspend fun markMessagesDelivered(messageIds: List<Long>): Result<Unit> {
        deliveredMarked += messageIds
        return Result.success(Unit)
    }

    override suspend fun markMessagesRead(messageIds: List<Long>): Result<Unit> {
        readMarked += messageIds
        return Result.success(Unit)
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

private class FakeDevicePushRepository :
    com.eopera.youhoo.domain.repository.DevicePushRepository {
    var registrations = 0

    override suspend fun registerDevice(): Result<Unit> {
        registrations += 1
        return Result.success(Unit)
    }

    override suspend fun registerToken(token: String): Result<Unit> = Result.success(Unit)
}

private class FakeFeatureFlagProvider : FeatureFlagProvider {
    override val chatScreenVariant = ChatScreenVariant.CLASSIC
}
