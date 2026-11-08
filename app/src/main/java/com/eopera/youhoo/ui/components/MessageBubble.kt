package com.eopera.youhoo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.eopera.youhoo.ui.theme.AppTheme
import com.eopera.youhoo.ui.theme.YouHooTheme

@Composable
fun MessageBubble(
    text: String,
    senderName: String,
    time: String,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val spacing = AppTheme.spacing
    val cornerRadius = if (compact) {
        AppTheme.dimensions.compactMessageCornerRadius
    } else {
        AppTheme.dimensions.messageCornerRadius
    }
    val background = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = background,
            shape = RoundedCornerShape(cornerRadius),
            modifier = Modifier
                .align(if (isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(vertical = spacing.extraSmall)
                .widthIn(max = AppTheme.dimensions.messageMaxWidth)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = spacing.medium,
                    vertical = if (compact) spacing.extraSmall else spacing.small
                )
            ) {
                if (!isMine) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
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
}

@Preview(showBackground = true)
@Composable
private fun IncomingMessagePreview() {
    YouHooTheme {
        MessageBubble(
            text = "Dinner is ready",
            senderName = "Alex",
            time = "18:30",
            isMine = false
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OutgoingMessagePreview() {
    YouHooTheme {
        MessageBubble(
            text = "On my way",
            senderName = "Sam",
            time = "18:31",
            isMine = true,
            compact = true
        )
    }
}
