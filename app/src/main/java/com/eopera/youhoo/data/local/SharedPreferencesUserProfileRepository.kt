package com.eopera.youhoo.data.local

import android.content.Context
import com.eopera.youhoo.domain.repository.UserProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SharedPreferencesUserProfileRepository @Inject constructor(
    @ApplicationContext context: Context
) : UserProfileRepository {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val _senderName = MutableStateFlow(
        preferences.getString(KEY_SENDER, "").orEmpty()
    )
    override val senderName: StateFlow<String> = _senderName.asStateFlow()

    override fun updateSenderName(name: String) {
        _senderName.value = name
        preferences.edit().putString(KEY_SENDER, name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "chat_prefs"
        const val KEY_SENDER = "sender"
    }
}
