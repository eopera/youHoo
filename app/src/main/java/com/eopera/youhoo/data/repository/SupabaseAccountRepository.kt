package com.eopera.youhoo.data.repository

import android.util.Log
import com.eopera.youhoo.data.local.DeviceIdProvider
import com.eopera.youhoo.data.remote.RegisterUserRequestDto
import com.eopera.youhoo.data.remote.SupabaseUserDto
import com.eopera.youhoo.domain.repository.AccountRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class SupabaseAccountRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val deviceIdProvider: DeviceIdProvider,
    private val userProfileRepository: UserProfileRepository
) : AccountRepository {
    override suspend fun register(displayName: String): Result<Unit> = try {
        val user = supabase.postgrest.rpc(
            function = REGISTER_USER_FUNCTION,
            parameters = RegisterUserRequestDto(
                deviceId = deviceIdProvider.deviceId,
                displayName = displayName
            )
        ).decodeAs<SupabaseUserDto>()
        userProfileRepository.updateProfile(user.id, user.displayName)
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.e(TAG, "Account registration failed: ${error.javaClass.simpleName}")
        Result.failure(error)
    }

    override suspend fun updateDisplayName(displayName: String): Result<Unit> =
        register(displayName)

    private companion object {
        const val TAG = "SupabaseAccountRepository"
        const val REGISTER_USER_FUNCTION = "register_user"
    }
}
