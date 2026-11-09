package com.eopera.youhoo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp
)

@Immutable
data class AppDimensions(
    val messageMaxWidth: Dp = 300.dp,
    val messageCornerRadius: Dp = 18.dp,
    val compactMessageCornerRadius: Dp = 14.dp
)

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val LocalAppDimensions = staticCompositionLocalOf { AppDimensions() }

object AppTheme {
    val spacing: AppSpacing
        @Composable get() = LocalAppSpacing.current
    val dimensions: AppDimensions
        @Composable get() = LocalAppDimensions.current
}
