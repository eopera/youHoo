package com.eopera.youhoo.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseRegisterDeviceTokenDto(
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_fcm_token") val fcmToken: String,
    @SerialName("p_sender_name") val senderName: String
)
