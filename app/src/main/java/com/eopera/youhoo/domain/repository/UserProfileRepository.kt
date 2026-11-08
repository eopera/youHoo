package com.eopera.youhoo.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface UserProfileRepository {
    val senderName: StateFlow<String>
    fun updateSenderName(name: String)

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 50
    }
}
