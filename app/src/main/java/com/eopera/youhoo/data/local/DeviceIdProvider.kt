package com.eopera.youhoo.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    val deviceId: String
        get() = preferences.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { id ->
                preferences.edit().putString(KEY_DEVICE_ID, id).apply()
            }

    private companion object {
        const val PREFERENCES_NAME = "notification_prefs"
        const val KEY_DEVICE_ID = "device_id"
    }
}
