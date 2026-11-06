package com.example.couplemessager.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.couplemessager.data.ChatRepository
import com.example.couplemessager.data.Message
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val messages by ChatRepository.messages.collectAsState()
    val status by ChatRepository.status.collectAsState()
    val scope = rememberCoroutineScope()

    // Имя отправителя храним локально, чтобы отличать свои сообщения от сообщений партнера
    val prefs = LocalContext.current.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    var sender by remember { mutableStateOf(prefs.getString("sender", "").orEmpty()) }
    LaunchedEffect(sender) {
        prefs.edit().putString("sender", sender).apply()
    }

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Автопрокрутка к последнему сообщению
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Couple Messager") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            OutlinedTextField(
                value = sender,
                onValueChange = { sender = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message, myName = sender)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isNotEmpty() && sender.isNotBlank()) {
                            scope.launch { ChatRepository.sendMessage(text, sender.trim()) }
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank() && sender.isNotBlank()
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, myName: String) {
    val isMine = myName.isNotBlank() && message.sender == myName
    val background = if (isMine) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(if (isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(vertical = 4.dp)
                .widthIn(max = 280.dp)
                .background(background, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!isMine) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = message.text)
            // created_at приходит в формате "2024-01-01T12:34:56..." — берем только "12:34"
            val time = if (message.createdAt.length >= 16) message.createdAt.substring(11, 16) else ""
            if (time.isNotEmpty()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
