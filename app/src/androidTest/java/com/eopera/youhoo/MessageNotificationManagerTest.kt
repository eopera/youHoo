package com.eopera.youhoo

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eopera.youhoo.notifications.MessageNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageNotificationManagerTest {
    @Test
    fun messagesReplaceSingleFamilyNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val manager = MessageNotificationManager(context)
        manager.showMessage("instrumented-test-1", "Alex", "Hello family")
        manager.showMessage(
            messageId = "instrumented-test-2",
            sender = "Sam",
            text = "On my way",
            unreadCount = 3,
            firstUnreadMessageId = "instrumented-test-1"
        )

        val systemManager = context.getSystemService(NotificationManager::class.java)
        var notifications = systemManager.activeNotifications.filter {
            it.tag == MessageNotificationManager.NOTIFICATION_TAG &&
                it.id == MessageNotificationManager.NOTIFICATION_ID
        }
        var latestTitle = notifications.singleOrNull()?.notification?.extras?.getString(
            Notification.EXTRA_TITLE
        )
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (latestTitle != "Sam" && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            notifications = systemManager.activeNotifications.filter {
                it.tag == MessageNotificationManager.NOTIFICATION_TAG &&
                    it.id == MessageNotificationManager.NOTIFICATION_ID
            }
            latestTitle = notifications.singleOrNull()?.notification?.extras?.getString(
                Notification.EXTRA_TITLE
            )
        }
        val notification = notifications.singleOrNull()

        assertEquals(1, notifications.size)
        assertNotNull(notification)
        assertEquals("Sam", latestTitle)
        assertEquals(
            MessageNotificationManager.CHANNEL_ID,
            notification?.notification?.channelId
        )
        assertEquals(3, notification?.notification?.number)
        NotificationManagerCompat.from(context).cancel(
            MessageNotificationManager.NOTIFICATION_TAG,
            MessageNotificationManager.NOTIFICATION_ID
        )
    }
}
