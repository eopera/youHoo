package com.eopera.youhoo.data.repository

import android.util.Log
import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.Message
import com.eopera.youhoo.data.remote.NewSupabaseMessageDto
import com.eopera.youhoo.data.remote.SupabaseMessageDto
import com.eopera.youhoo.domain.repository.ChatRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@Singleton
class SupabaseChatRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val json: Json
) : ChatRepository {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    override val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    override val connectionStatus: StateFlow<ConnectionStatus> =
        _connectionStatus.asStateFlow()

    override suspend fun connect() {
        try {
            _connectionStatus.value = ConnectionStatus.LOADING_HISTORY
            _messages.value = supabase.postgrest[MESSAGES_TABLE]
                .select { order(CREATED_AT_COLUMN, Order.ASCENDING) }
                .decodeList<SupabaseMessageDto>()
                .map(SupabaseMessageDto::toDomain)

            val channel = supabase.realtime.channel(CHANNEL_ID)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(
                schema = PUBLIC_SCHEMA
            ) {
                table = MESSAGES_TABLE
            }

            supabase.realtime.connect()
            channel.subscribe()
            _connectionStatus.value = ConnectionStatus.CONNECTED

            inserts.collect { action ->
                val newMessage = json.decodeFromJsonElement<SupabaseMessageDto>(
                    action.record
                ).toDomain()
                _messages.update { current ->
                    if (current.any { it.id == newMessage.id }) current
                    else current + newMessage
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "Realtime connection failed: ${error.javaClass.simpleName}")
            _connectionStatus.value = ConnectionStatus.CONNECTION_ERROR
        }
    }

    override suspend fun sendMessage(text: String, sender: String): Result<Unit> =
        try {
            supabase.postgrest[MESSAGES_TABLE].insert(
                NewSupabaseMessageDto(text = text, sender = sender)
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

    private companion object {
        const val TAG = "SupabaseChatRepository"
        const val MESSAGES_TABLE = "messages"
        const val CREATED_AT_COLUMN = "created_at"
        const val CHANNEL_ID = "messages-channel"
        const val PUBLIC_SCHEMA = "public"
    }
}
