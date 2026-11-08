package com.eopera.youhoo

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eopera.youhoo.notifications.MessageNotificationManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageNotificationManagerTest {
    @Test
    fun messageIsPostedToFamilyChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val manager = MessageNotificationManager(context)
        val messageId = "instrumented-test-message"
        manager.showMessage(messageId, "Alex", "Hello family")

        val systemManager = context.getSystemService(NotificationManager::class.java)
        val notification = systemManager.activeNotifications.firstOrNull {
            it.notification.channelId == MessageNotificationManager.CHANNEL_ID &&
                it.notification.extras.getString(Notification.EXTRA_TITLE) == "Alex"
        }

        assertTrue(notification != null)
        NotificationManagerCompat.from(context).cancel(messageId.hashCode())
    }
}
