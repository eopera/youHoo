package com.eopera.youhoo.di

import com.eopera.youhoo.BuildConfig
import com.eopera.youhoo.data.local.SharedPreferencesUserProfileRepository
import com.eopera.youhoo.data.repository.SupabaseChatRepository
import com.eopera.youhoo.domain.repository.ChatRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import com.eopera.youhoo.feature.chat.BuildConfigFeatureFlagProvider
import com.eopera.youhoo.feature.chat.FeatureFlagProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL is missing. Add it to local.properties."
        }
        require(BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "SUPABASE_ANON_KEY is missing. Add it to local.properties."
        }
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Realtime)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindChatRepository(
        implementation: SupabaseChatRepository
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(
        implementation: SharedPreferencesUserProfileRepository
    ): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindFeatureFlagProvider(
        implementation: BuildConfigFeatureFlagProvider
    ): FeatureFlagProvider
}
