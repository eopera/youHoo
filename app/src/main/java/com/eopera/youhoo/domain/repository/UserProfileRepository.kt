package com.eopera.youhoo.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface UserProfileRepository {
    val senderName: StateFlow<String>
    fun updateSenderName(name: String)
}
