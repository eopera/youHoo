package com.eopera.youhoo.data.repository

import android.util.Log
import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.Message
import com.eopera.youhoo.data.local.DeviceIdProvider
import com.eopera.youhoo.data.remote.MarkMessagesReceiptDto
import com.eopera.youhoo.data.remote.NewSupabaseMessageDto
import com.eopera.youhoo.data.remote.SupabaseMessageDto
import com.eopera.youhoo.domain.repository.ChatRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
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
    private val deviceIdProvider: DeviceIdProvider
) : ChatRepository {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    override val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    override val connectionStatus: StateFlow<ConnectionStatus> =
        _connectionStatus.asStateFlow()

    override suspend fun connect() {
        try {
            _connectionStatus.value = ConnectionStatus.LOADING_HISTORY
            refreshMessages()

            val channel = supabase.realtime.channel(CHANNEL_ID)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(
                schema = PUBLIC_SCHEMA
            ) {
                table = MESSAGES_TABLE
            }.map { it.record }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(
                schema = PUBLIC_SCHEMA
            ) {
                table = MESSAGES_TABLE
            }.map { it.record }

            supabase.realtime.connect()
            channel.subscribe()
            _connectionStatus.value = ConnectionStatus.CONNECTED

            coroutineScope {
                launch { resyncAfterReconnect() }
                merge(inserts, updates).collect { record ->
                    val message = json.decodeFromJsonElement<SupabaseMessageDto>(record)
                        .toDomain(deviceIdProvider.deviceId)
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
        }
    }

    private suspend fun resyncAfterReconnect() {
        var wasConnected = true
        supabase.realtime.status.collect { status ->
            if (status == Realtime.Status.CONNECTED) {
                if (!wasConnected) {
                    Log.i(TAG, "Realtime reconnected, refreshing history")
                    refreshMessages()
                }
                wasConnected = true
            } else {
                wasConnected = false
            }
        }
    }

    private suspend fun refreshMessages() {
        val loadedMessages = supabase.postgrest[MESSAGES_TABLE]
            .select { order(CREATED_AT_COLUMN, Order.ASCENDING) }
            .decodeList<SupabaseMessageDto>()
            .map { it.toDomain(deviceIdProvider.deviceId) }
        _messages.value = loadedMessages
        markIncomingDelivered(loadedMessages)
    }

    override suspend fun sendMessage(text: String, sender: String): Result<Unit> =
        try {
            supabase.postgrest[MESSAGES_TABLE].insert(
                NewSupabaseMessageDto(
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

    override suspend fun markMessagesDelivered(messageIds: List<Long>): Result<Unit> =
        markReceipts(MARK_DELIVERED_FUNCTION, messageIds)

    override suspend fun markMessagesRead(messageIds: List<Long>): Result<Unit> =
        markReceipts(MARK_READ_FUNCTION, messageIds)

    private suspend fun markIncomingDelivered(messages: List<Message>) {
        val messageIds = messages
            .filter { !it.isFromCurrentDevice && it.deliveredAt == null }
            .map(Message::id)
        markMessagesDelivered(messageIds)
    }

    private suspend fun markReceipts(
        function: String,
        messageIds: List<Long>
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
            val index = current.indexOfFirst { it.id == message.id }
            if (index < 0) {
                current + message
            } else {
                current.toMutableList().apply { set(index, message) }
            }
        }
    }

    private companion object {
        const val TAG = "SupabaseChatRepository"
        const val MESSAGES_TABLE = "messages"
        const val CREATED_AT_COLUMN = "created_at"
        const val CHANNEL_ID = "messages-channel"
        const val PUBLIC_SCHEMA = "public"
        const val MARK_DELIVERED_FUNCTION = "mark_messages_delivered"
        const val MARK_READ_FUNCTION = "mark_messages_read"
    }
}
