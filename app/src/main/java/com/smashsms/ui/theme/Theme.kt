package com.smashsms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SmashPrimaryDark,
    onPrimary = SmashOnPrimaryDark,
    primaryContainer = SmashPrimaryContainerDark,
    onPrimaryContainer = SmashOnPrimaryContainerDark,
    secondary = SmashSecondaryDark,
    onSecondary = SmashOnSecondaryDark,
    secondaryContainer = SmashSecondaryContainerDark,
    onSecondaryContainer = SmashOnSecondaryContainerDark,
    tertiary = SmashTertiaryDark,
    tertiaryContainer = SmashTertiaryContainerDark,
    onTertiaryContainer = SmashOnTertiaryContainerDark,
    background = SmashBackgroundDark,
    surface = SmashSurfaceDark,
    surfaceVariant = SmashSurfaceVariantDark,
    onSurfaceVariant = SmashOnSurfaceVariantDark,
)

private val LightColorScheme = lightColorScheme(
    primary = SmashPrimaryLight,
    onPrimary = SmashOnPrimaryLight,
    primaryContainer = SmashPrimaryContainerLight,
    onPrimaryContainer = SmashOnPrimaryContainerLight,
    secondary = SmashSecondaryLight,
    onSecondary = SmashOnSecondaryLight,
    secondaryContainer = SmashSecondaryContainerLight,
    onSecondaryContainer = SmashOnSecondaryContainerLight,
    tertiary = SmashTertiaryLight,
    tertiaryContainer = SmashTertiaryContainerLight,
    onTertiaryContainer = SmashOnTertiaryContainerLight,
    background = SmashBackgroundLight,
    surface = SmashSurfaceLight,
    surfaceVariant = SmashSurfaceVariantLight,
    onSurfaceVariant = SmashOnSurfaceVariantLight,
)

@Composable
fun SmashSMSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
