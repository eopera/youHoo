package com.example.couplemessager.data

import android.util.Log
import io.github.jan_tennert.supabase.createSupabaseClient
import io.github.jan_tennert.supabase.postgrest.Postgrest
import io.github.jan_tennert.supabase.postgrest.postgrest
import io.github.jan_tennert.supabase.postgrest.query.Order
import io.github.jan_tennert.supabase.realtime.PostgresAction
import io.github.jan_tennert.supabase.realtime.Realtime
import io.github.jan_tennert.supabase.realtime.createChannel
import io.github.jan_tennert.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

object ChatRepository {

    // TODO: вставьте данные вашего проекта (Supabase Dashboard -> Settings -> API)
    private const val SUPABASE_URL = "https://YOUR_PROJECT.supabase.co"
    private const val SUPABASE_ANON_KEY = "YOUR_ANON_KEY"

    private val json = Json { ignoreUnknownKeys = true }

    private val supabase = createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
        install(Postgrest)
        install(Realtime)
    }

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _status = MutableStateFlow("Connecting…")
    val status: StateFlow<String> = _status.asStateFlow()

    suspend fun connect() {
        try {
            // 1. Загружаем историю переписки
            _status.value = "Loading history…"
            _messages.value = supabase.postgrest["messages"]
                .select { order("created_at", Order.ASCENDING) }
                .decodeList<Message>()

            // 2. Подписываемся на новые строки в таблице messages
            val channel = supabase.realtime.createChannel("messages-channel")
            val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
            }

            supabase.realtime.connect()
            channel.subscribe()

            _status.value = "Connected"

            // 3. Как только в БД упадет новая строка, collect сработает мгновенно
            flow.collect { action ->
                val newMessage = json.decodeFromJsonElement<Message>(action.record)
                _messages.update { current ->
                    if (current.any { it.id == newMessage.id }) current
                    else current + newMessage
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Realtime connection failed", e)
            _status.value = "Connection error: ${e.message}"
        }
    }

    suspend fun sendMessage(text: String, sender: String) {
        supabase.postgrest["messages"].insert(NewMessage(text, sender))
        // Своё сообщение вернется обратно через Realtime, отдельно добавлять не нужно
    }
}
