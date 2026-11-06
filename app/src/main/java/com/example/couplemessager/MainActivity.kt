package com.example.couplemessager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.couplemessager.data.ChatRepository
import com.example.couplemessager.ui.ChatScreen
import com.example.couplemessager.ui.theme.CoupleMessagerTheme
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
            CoupleMessagerTheme {
                ChatScreen()
            }
        }
    }
}
