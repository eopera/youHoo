package com.eopera.youhoo.data.remote

import com.eopera.youhoo.core.model.Contact
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseUserDto(
    val id: String,
    @SerialName("display_name") val displayName: String
) {
    fun toDomain(): Contact = Contact(id = id, displayName = displayName)
}

@Serializable
data class RegisterUserRequestDto(
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_display_name") val displayName: String
)
