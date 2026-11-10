package com.eopera.youhoo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.eopera.youhoo.notifications.MessageNotificationManager
import com.eopera.youhoo.ui.theme.YouHooTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingMessageId by mutableStateOf<String?>(null)
    private var pendingContactId by mutableStateOf<String?>(null)
    private var pendingContactName by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readPendingDestination(intent)
        enableEdgeToEdge()
        setContent {
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            YouHooTheme {
                AppRoute(
                    pendingMessageId = pendingMessageId,
                    pendingContactId = pendingContactId,
                    pendingContactName = pendingContactName,
                    onPendingMessageHandled = ::clearPendingDestination,
                    onRegistered = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readPendingDestination(intent)
    }

    private fun readPendingDestination(intent: Intent?) {
        pendingMessageId = intent
            ?.getStringExtra(MessageNotificationManager.EXTRA_MESSAGE_ID)
            ?.let(::parseUuid)
        pendingContactId = intent
            ?.getStringExtra(MessageNotificationManager.EXTRA_SENDER_ID)
            ?.let(::parseUuid)
        pendingContactName = intent
            ?.getStringExtra(MessageNotificationManager.EXTRA_SENDER_NAME)
    }

    private fun clearPendingDestination() {
        pendingMessageId = null
        pendingContactId = null
        pendingContactName = null
    }

    private fun parseUuid(value: String): String? = runCatching {
        UUID.fromString(value).toString()
    }.getOrNull()
}
