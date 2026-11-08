package com.eopera.youhoo.domain.repository

interface DevicePushRepository {
    suspend fun registerDevice(): Result<Unit>
    suspend fun registerToken(token: String): Result<Unit>
}
