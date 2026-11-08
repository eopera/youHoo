package com.eopera.youhoo.data.repository

import android.content.Context
import android.util.Log
import com.eopera.youhoo.BuildConfig
import com.eopera.youhoo.data.local.DeviceIdProvider
import com.eopera.youhoo.data.remote.SupabaseRegisterDeviceTokenDto
import com.eopera.youhoo.domain.repository.DevicePushRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseDevicePushRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val deviceIdProvider: DeviceIdProvider,
    private val userProfileRepository: UserProfileRepository
) : DevicePushRepository {
    override suspend fun registerDevice(): Result<Unit> = try {
        val messaging = firebaseMessaging()
            ?: return Result.failure(IllegalStateException("Firebase is not configured"))
        registerToken(messaging.token.await())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "Push registration failed: ${error.javaClass.simpleName}")
        Result.failure(error)
    }

    override suspend fun registerToken(token: String): Result<Unit> = try {
        val senderName = userProfileRepository.senderName.value.trim()
        require(token.isNotBlank()) { "FCM token is blank" }
        require(senderName.isNotEmpty()) { "Display name is blank" }
        supabase.postgrest.rpc(
            function = REGISTER_DEVICE_TOKEN_FUNCTION,
            parameters = SupabaseRegisterDeviceTokenDto(
                deviceId = deviceIdProvider.deviceId,
                fcmToken = token,
                senderName = senderName
            )
        )
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "Push token upload failed: ${error.javaClass.simpleName}")
        Result.failure(error)
    }

    private fun firebaseMessaging(): FirebaseMessaging? {
        FirebaseApp.getApps(context).firstOrNull()?.let {
            return FirebaseMessaging.getInstance()
        }
        if (!hasFirebaseConfiguration) return null
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
        )
        return FirebaseMessaging.getInstance()
    }

    private val hasFirebaseConfiguration: Boolean
        get() = listOf(
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_APPLICATION_ID,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID
        ).all(String::isNotBlank)

    private companion object {
        const val TAG = "FirebaseDevicePush"
        const val REGISTER_DEVICE_TOKEN_FUNCTION = "register_device_token"
    }
}
