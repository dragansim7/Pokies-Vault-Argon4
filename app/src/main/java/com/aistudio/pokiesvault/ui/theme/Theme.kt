package com.aistudio.pokiesvault.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun PokiesVaultTheme(
    themeId: String = "vault",
    content: @Composable () -> Unit
) {
    val colors = getThemeById(themeId)
    val colorScheme = darkColorScheme(
        primary = colors.cyan,
        onPrimary = colors.ink,
        primaryContainer = colors.panel2,
        onPrimaryContainer = colors.fg,
        secondary = colors.violet,
        onSecondary = colors.fg,
        secondaryContainer = colors.panel2,
        onSecondaryContainer = colors.fg,
        tertiary = colors.pink,
        onTertiary = colors.fg,
        background = colors.bg,
        onBackground = colors.fg,
        surface = colors.panel,
        onSurface = colors.fg,
        surfaceVariant = colors.panel2,
        onSurfaceVariant = colors.muted,
        outline = colors.border
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.bg.toArgb()
                window.navigationBarColor = colors.bg.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    CompositionLocalProvider(LocalVaultColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
