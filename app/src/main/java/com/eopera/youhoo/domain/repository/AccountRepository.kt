package com.eopera.youhoo.domain.repository

interface AccountRepository {
    suspend fun register(displayName: String): Result<Unit>
    suspend fun updateDisplayName(displayName: String): Result<Unit>
}
