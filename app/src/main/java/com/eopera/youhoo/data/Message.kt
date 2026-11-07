package com.eopera.youhoo.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long,
    val text: String,
    val sender: String,
    @SerialName("created_at") val createdAt: String
)

// Отдельная модель для отправки: без id и created_at,
// чтобы Postgres сам проставил значения по умолчанию
@Serializable
data class NewMessage(
    val text: String,
    val sender: String
)
