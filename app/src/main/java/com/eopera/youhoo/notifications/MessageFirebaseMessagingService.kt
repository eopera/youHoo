package com.eopera.youhoo.notifications

import com.eopera.youhoo.domain.repository.ChatRepository
import com.eopera.youhoo.domain.repository.DevicePushRepository
import com.eopera.youhoo.domain.repository.UserProfileRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessageFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var devicePushRepository: DevicePushRepository
    @Inject lateinit var userProfileRepository: UserProfileRepository
    @Inject lateinit var messageNotificationManager: MessageNotificationManager
    @Inject lateinit var chatRepository: ChatRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            devicePushRepository.registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val sender = message.data["sender"]
            ?: message.notification?.title
            ?: return
        if (sender == userProfileRepository.senderName.value.trim()) return
        val text = message.data["text"] ?: message.notification?.body.orEmpty()
        val messageId = message.data["message_id"] ?: message.messageId ?: return
        val unreadCount = message.data["unread_count"]?.toIntOrNull() ?: 1
        val firstUnreadMessageId = message.data["first_unread_message_id"]
        messageId.toLongOrNull()?.let { id ->
            serviceScope.launch {
                chatRepository.markMessagesDelivered(listOf(id))
            }
        }
        messageNotificationManager.showMessage(
            messageId = messageId,
            sender = sender,
            text = text,
            unreadCount = unreadCount,
            firstUnreadMessageId = firstUnreadMessageId
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
