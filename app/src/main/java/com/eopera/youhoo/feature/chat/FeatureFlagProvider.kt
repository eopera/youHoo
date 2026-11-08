package com.eopera.youhoo.feature.chat

import com.eopera.youhoo.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

enum class ChatScreenVariant {
    CLASSIC,
    COMPACT
}

interface FeatureFlagProvider {
    val chatScreenVariant: ChatScreenVariant
}

@Singleton
class BuildConfigFeatureFlagProvider @Inject constructor() : FeatureFlagProvider {
    override val chatScreenVariant: ChatScreenVariant =
        runCatching { ChatScreenVariant.valueOf(BuildConfig.CHAT_UI_VARIANT) }
            .getOrDefault(ChatScreenVariant.CLASSIC)
}
