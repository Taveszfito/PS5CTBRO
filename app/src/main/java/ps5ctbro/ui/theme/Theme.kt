package com.DueBoysenberry1226.ps5ctbro.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Midnight900,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceContainer = Midnight700,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceVariant = Midnight600,
    onSurfaceVariant = TextSecondaryDark,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    outline = PanelStroke,
    outlineVariant = PanelStroke.copy(alpha = 0.55f),
    error = DangerRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    background = LightBackground,
    surface = LightSurface,
    surfaceContainer = ColorTokens.LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceVariant = ColorTokens.LightSurfaceVariant,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer
)

private object ColorTokens {
    val LightSurfaceContainer = LightSurfaceHigh
    val LightSurfaceVariant = Color(0xFFE6EEFF)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun PS5CTBroTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}