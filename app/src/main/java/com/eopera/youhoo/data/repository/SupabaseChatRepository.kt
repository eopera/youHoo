package com.eopera.youhoo.data.repository

import android.util.Log
import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.Message
import com.eopera.youhoo.data.local.DeviceIdProvider
import com.eopera.youhoo.data.remote.ConversationPageRequestDto
import com.eopera.youhoo.data.remote.DirectConversationRequestDto
import com.eopera.youhoo.data.remote.MarkMessagesReceiptDto
import com.eopera.youhoo.data.remote.NewSupabaseMessageDto
import com.eopera.youhoo.data.remote.SupabaseMessageDto
import com.eopera.youhoo.domain.repository.ChatRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@Singleton
class SupabaseChatRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val json: Json,
    private val deviceIdProvider: DeviceIdProvider,
    private val userProfileRepository: UserProfileRepository
) : ChatRepository {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    override val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _hasOlderMessages = MutableStateFlow(false)
    override val hasOlderMessages: StateFlow<Boolean> = _hasOlderMessages.asStateFlow()

    private var activeConversationId: String? = null
    private var oldestMessage: Message? = null

    override suspend fun connect(contactId: String) {
        var channel: io.github.jan.supabase.realtime.RealtimeChannel? = null
        try {
            _connectionStatus.value = ConnectionStatus.LOADING_HISTORY
            _messages.value = emptyList()
            _hasOlderMessages.value = false
            oldestMessage = null

            val currentUserId = requireNotNull(userProfileRepository.userId.value) {
                "A registered user is required"
            }
            val conversationId = supabase.postgrest.rpc(
                function = DIRECT_CONVERSATION_FUNCTION,
                parameters = DirectConversationRequestDto(
                    userId = currentUserId,
                    otherUserId = contactId
                )
            ).decodeAs<String>()
            activeConversationId = conversationId
            refreshMessages(conversationId)

            val realtimeChannel = supabase.realtime.channel("conversation-$conversationId")
            channel = realtimeChannel
            val inserts = realtimeChannel.postgresChangeFlow<PostgresAction.Insert>(
                schema = PUBLIC_SCHEMA
            ) {
                table = MESSAGES_TABLE
                filter = "conversation_id=eq.$conversationId"
            }.map { it.record }
            val updates = realtimeChannel.postgresChangeFlow<PostgresAction.Update>(
                schema = PUBLIC_SCHEMA
            ) {
                table = MESSAGES_TABLE
                filter = "conversation_id=eq.$conversationId"
            }.map { it.record }

            supabase.realtime.connect()
            realtimeChannel.subscribe()
            _connectionStatus.value = ConnectionStatus.CONNECTED

            coroutineScope {
                launch { resyncAfterReconnect(conversationId) }
                merge(inserts, updates).collect { record ->
                    val message = json.decodeFromJsonElement<SupabaseMessageDto>(record)
                        .toDomain(deviceIdProvider.deviceId, currentUserId)
                    if (message.conversationId != activeConversationId) return@collect
                    upsert(message)
                    if (!message.isFromCurrentDevice && message.deliveredAt == null) {
                        markMessagesDelivered(listOf(message.id))
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Realtime connection failed: ${error.javaClass.simpleName}")
            _connectionStatus.value = ConnectionStatus.CONNECTION_ERROR
        } finally {
            channel?.let {
                runCatching { it.unsubscribe() }
                supabase.realtime.removeChannel(it)
            }
        }
    }

    private suspend fun resyncAfterReconnect(conversationId: String) {
        var wasConnected = true
        supabase.realtime.status.collect { status ->
            if (status == Realtime.Status.CONNECTED) {
                if (!wasConnected && activeConversationId == conversationId) {
                    Log.i(TAG, "Realtime reconnected, refreshing the latest page")
                    refreshMessages(conversationId)
                }
                wasConnected = true
            } else {
                wasConnected = false
            }
        }
    }

    private suspend fun refreshMessages(conversationId: String) {
        val loadedMessages = fetchPage(conversationId)
        if (activeConversationId != conversationId) return
        _messages.value = loadedMessages
        oldestMessage = loadedMessages.firstOrNull()
        _hasOlderMessages.value = loadedMessages.size == PAGE_SIZE
        markIncomingDelivered(loadedMessages)
    }

    override suspend fun loadOlderMessages(): Result<Unit> = try {
        val conversationId = activeConversationId ?: return Result.success(Unit)
        val cursor = oldestMessage ?: return Result.success(Unit)
        val olderMessages = fetchPage(conversationId, cursor)
        if (activeConversationId == conversationId && oldestMessage?.id == cursor.id) {
            olderMessages.forEach(::upsert)
            oldestMessage = _messages.value.firstOrNull()
            _hasOlderMessages.value = olderMessages.size == PAGE_SIZE
            markIncomingDelivered(olderMessages)
        }
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "History pagination failed: ${error.javaClass.simpleName}")
        Result.failure(error)
    }

    private suspend fun fetchPage(
        conversationId: String,
        before: Message? = null
    ): List<Message> = supabase.postgrest.rpc(
        function = CONVERSATION_MESSAGES_FUNCTION,
        parameters = ConversationPageRequestDto(
            conversationId = conversationId,
            pageSize = PAGE_SIZE,
            beforeCreatedAt = before?.createdAt,
            beforeId = before?.id
        )
    ).decodeList<SupabaseMessageDto>()
        .asReversed()
        .map {
            it.toDomain(
                currentDeviceId = deviceIdProvider.deviceId,
                currentUserId = userProfileRepository.userId.value.orEmpty()
            )
        }

    override suspend fun sendMessage(text: String, sender: String): Result<Unit> = try {
        val conversationId = requireNotNull(activeConversationId) { "No active conversation" }
        val senderId = requireNotNull(userProfileRepository.userId.value) {
            "A registered user is required"
        }
        supabase.postgrest[MESSAGES_TABLE].insert(
            NewSupabaseMessageDto(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = senderId,
                text = text,
                sender = sender,
                senderDeviceId = deviceIdProvider.deviceId
            )
        )
        _connectionStatus.value = ConnectionStatus.CONNECTED
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.e(TAG, "Message send failed: ${error.javaClass.simpleName}")
        _connectionStatus.value = ConnectionStatus.SEND_ERROR
        Result.failure(error)
    }

    override suspend fun markMessagesDelivered(messageIds: List<String>): Result<Unit> =
        markReceipts(MARK_DELIVERED_FUNCTION, messageIds)

    override suspend fun markMessagesRead(messageIds: List<String>): Result<Unit> =
        markReceipts(MARK_READ_FUNCTION, messageIds)

    private suspend fun markIncomingDelivered(messages: List<Message>) {
        val messageIds = messages
            .filter { !it.isFromCurrentDevice && it.deliveredAt == null }
            .map(Message::id)
        markMessagesDelivered(messageIds)
    }

    private suspend fun markReceipts(
        function: String,
        messageIds: List<String>
    ): Result<Unit> = try {
        if (messageIds.isNotEmpty()) {
            supabase.postgrest.rpc(
                function = function,
                parameters = MarkMessagesReceiptDto(
                    messageIds = messageIds.distinct(),
                    deviceId = deviceIdProvider.deviceId
                )
            )
        }
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "Receipt update failed: ${error.javaClass.simpleName}")
        Result.failure(error)
    }

    private fun upsert(message: Message) {
        _messages.update { current ->
            val withoutMessage = current.filterNot { it.id == message.id }
            if (message.deletedAt != null) {
                withoutMessage
            } else {
                (withoutMessage + message).sortedWith(
                    compareBy(Message::createdAt, Message::id)
                )
            }
        }
    }

    private companion object {
        const val TAG = "SupabaseChatRepository"
        const val MESSAGES_TABLE = "messages"
        const val PUBLIC_SCHEMA = "public"
        const val DIRECT_CONVERSATION_FUNCTION = "get_or_create_direct_conversation"
        const val CONVERSATION_MESSAGES_FUNCTION = "get_conversation_messages"
        const val MARK_DELIVERED_FUNCTION = "mark_messages_delivered"
        const val MARK_READ_FUNCTION = "mark_messages_read"
        const val PAGE_SIZE = 50
    }
}
