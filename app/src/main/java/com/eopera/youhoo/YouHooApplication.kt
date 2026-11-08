package com.eopera.youhoo

import android.app.Application
import com.eopera.youhoo.notifications.MessageNotificationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YouHooApplication : Application() {
    @Inject lateinit var messageNotificationManager: MessageNotificationManager

    override fun onCreate() {
        super.onCreate()
        messageNotificationManager.createChannel()
    }
}
