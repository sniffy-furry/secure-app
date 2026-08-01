package com.nulchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette lifted directly from the brief's color spec.
val BackgroundDeep = Color(0xFF1E1E2E)
val BackgroundPanel = Color(0xFF2B2D3A)
val BackgroundChat = Color(0xFF36393F)
val AccentPurple = Color(0xFF7C3AED)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFDCDDDE)
val OnlineGreen = Color(0xFF3BA55D)

private val NulChatDarkColors = darkColorScheme(
    background = BackgroundDeep,
    surface = BackgroundPanel,
    surfaceVariant = BackgroundChat,
    primary = AccentPurple,
    onBackground = TextPrimary,
    onSurface = TextSecondary,
)

@Composable
fun NulChatTheme(content: @Composable () -> Unit) {
    // Discord-style dark theme is the only theme for MVP, regardless of system setting.
    MaterialTheme(
        colorScheme = NulChatDarkColors,
        content = content
    )
}
