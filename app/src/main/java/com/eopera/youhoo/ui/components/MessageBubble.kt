package com.eopera.youhoo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eopera.youhoo.R
import com.eopera.youhoo.core.model.MessageDeliveryStatus
import com.eopera.youhoo.ui.theme.AppTheme
import com.eopera.youhoo.ui.theme.YouHooTheme

@Composable
fun MessageBubble(
    text: String,
    senderName: String,
    time: String,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true,
    deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT
) {
    val spacing = AppTheme.spacing
    val cornerRadius = if (compact) {
        AppTheme.dimensions.compactMessageCornerRadius
    } else {
        AppTheme.dimensions.messageCornerRadius
    }
    val background = if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(
        topStart = cornerRadius,
        topEnd = cornerRadius,
        bottomStart = if (!isMine && isLastInGroup) spacing.extraSmall else cornerRadius,
        bottomEnd = if (isMine && isLastInGroup) spacing.extraSmall else cornerRadius
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = if (isFirstInGroup) spacing.small else 1.dp,
                bottom = if (isLastInGroup) spacing.small else 1.dp
            )
    ) {
        Surface(
            color = background,
            contentColor = contentColor,
            shape = shape,
            border = if (isMine) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
            modifier = Modifier
                .align(if (isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = AppTheme.dimensions.messageMaxWidth)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = spacing.medium,
                    vertical = if (compact) spacing.extraSmall else spacing.small
                )
            ) {
                if (!isMine && isFirstInGroup) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
                if (time.isNotEmpty() && isLastInGroup) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp)
                    ) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.72f)
                        )
                        if (isMine) {
                            MessageStatusIcon(
                                status = deliveryStatus,
                                contentColor = contentColor,
                                modifier = Modifier.padding(start = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageStatusIcon(
    status: MessageDeliveryStatus,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = if (status == MessageDeliveryStatus.SENT) {
        AppCheckIcon
    } else {
        AppDoubleCheckIcon
    }
    val tint = if (status == MessageDeliveryStatus.READ) {
        MaterialTheme.colorScheme.tertiary
    } else {
        contentColor.copy(alpha = 0.72f)
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(
            when (status) {
                MessageDeliveryStatus.SENT -> R.string.message_status_sent
                MessageDeliveryStatus.DELIVERED -> R.string.message_status_delivered
                MessageDeliveryStatus.READ -> R.string.message_status_read
            }
        ),
        tint = tint,
        modifier = modifier.size(14.dp)
    )
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
private fun OutgoingReadMessagePreview() {
    YouHooTheme {
        MessageBubble(
            text = "On my way",
            senderName = "Sam",
            time = "18:31",
            isMine = true,
            deliveryStatus = MessageDeliveryStatus.READ
        )
    }
}
