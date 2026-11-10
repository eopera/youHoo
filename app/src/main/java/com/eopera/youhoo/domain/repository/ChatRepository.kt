package com.eopera.youhoo.domain.repository

import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.Message
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val messages: StateFlow<List<Message>>
    val connectionStatus: StateFlow<ConnectionStatus>
    val hasOlderMessages: StateFlow<Boolean>

    /** Opens a direct conversation and collects its realtime updates until cancelled. */
    suspend fun connect(contactId: String)

    suspend fun loadOlderMessages(): Result<Unit>

    suspend fun sendMessage(text: String, sender: String): Result<Unit>

    suspend fun markMessagesDelivered(messageIds: List<String>): Result<Unit>

    suspend fun markMessagesRead(messageIds: List<String>): Result<Unit>
}
