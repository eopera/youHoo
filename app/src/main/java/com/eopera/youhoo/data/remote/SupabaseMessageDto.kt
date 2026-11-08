package com.eopera.youhoo.data.remote

import com.eopera.youhoo.core.model.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseMessageDto(
    val id: Long,
    val text: String,
    val sender: String,
    @SerialName("created_at") val createdAt: String
) {
    fun toDomain(): Message = Message(
        id = id,
        text = text,
        sender = sender,
        createdAt = createdAt
    )
}

@Serializable
data class NewSupabaseMessageDto(
    val text: String,
    val sender: String
)
