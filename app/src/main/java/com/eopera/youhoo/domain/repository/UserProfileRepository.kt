package com.eopera.youhoo.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface UserProfileRepository {
    val userId: StateFlow<String?>
    val senderName: StateFlow<String>
    fun updateProfile(userId: String, name: String)

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 50
    }
}
