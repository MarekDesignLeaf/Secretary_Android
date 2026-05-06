package com.example.secretary.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// DesignLeaf brand: zelena zahrada + teplý kontrast
private val Leaf = Color(0xFF2E7D32)       // tmave zelena
private val LeafLight = Color(0xFF66BB6A)  // svetla zelena
private val LeafDark = Color(0xFF1B5E20)   // velmi tmava zelena
private val Earth = Color(0xFF5D4037)      // hneda zeme
private val Sand = Color(0xFFF5F0EB)       // pisek (svetle pozadi)
private val Bark = Color(0xFF3E2723)       // kura stromu
private val Sky = Color(0xFF1565C0)        // modra obloha (akcent)
private val Sunset = Color(0xFFE65100)     // oranžový západ (error)

private val DarkColorScheme = darkColorScheme(
    primary = LeafLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF90A4AE),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFCFD8DC),
    tertiary = Color(0xFF4FC3F7),
    onTertiary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = Color(0xFFEF5350),
    onError = Color.White,
    outline = Color(0xFF4A4A4A),
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF1E1E1E),
    surfaceTint = LeafLight
)

private val LightColorScheme = lightColorScheme(
    primary = Leaf,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = LeafDark,
    secondary = Earth,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7CCC8),
    onSecondaryContainer = Bark,
    tertiary = Sky,
    onTertiary = Color.White,
    background = Sand,
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF0EBE3),
    onSurfaceVariant = Color(0xFF49454F),
    error = Sunset,
    onError = Color.White,
    outline = Color(0xFFBCAAA4),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    surfaceTint = Leaf
)

@Composable
fun SecretaryTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
