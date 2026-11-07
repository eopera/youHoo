package com.eopera.youhoo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.eopera.youhoo.data.ChatRepository
import com.eopera.youhoo.ui.ChatScreen
import com.eopera.youhoo.ui.theme.YouHooTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Подключаемся к Supabase Realtime (корутина отменится при уничтожении Activity)
        lifecycleScope.launch {
            ChatRepository.connect()
        }

        setContent {
            YouHooTheme {
                ChatScreen()
            }
        }
    }
}
