package com.eopera.youhoo.domain.repository

import com.eopera.youhoo.core.model.ConnectionStatus
import com.eopera.youhoo.core.model.Message
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val messages: StateFlow<List<Message>>
    val connectionStatus: StateFlow<ConnectionStatus>

    /** Loads history and collects realtime updates until the caller is cancelled. */
    suspend fun connect()

    suspend fun sendMessage(text: String, sender: String): Result<Unit>

    suspend fun markMessagesDelivered(messageIds: List<Long>): Result<Unit>

    suspend fun markMessagesRead(messageIds: List<Long>): Result<Unit>
}
