package com.naturasonic.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = NaturaTeal,
    onPrimary = Color.White,
    primaryContainer = NaturaTealLight,
    onPrimaryContainer = Color(0xFF002019),
    secondary = NaturaGreen,
    onSecondary = Color.White,
    tertiary = NaturaAmber,
    onTertiary = Color.White,
    error = NaturaRed,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F)
)

private val DarkColorScheme = darkColorScheme(
    primary = NaturaTealLight,
    onPrimary = Color(0xFF003731),
    primaryContainer = NaturaTealDark,
    onPrimaryContainer = NaturaTealLight,
    secondary = NaturaGreen,
    onSecondary = Color(0xFF003910),
    tertiary = NaturaAmber,
    onTertiary = Color(0xFF462B00),
    error = NaturaRed,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

@Composable
fun NaturaSonicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NaturaSonicTypography,
        content = content
    )
}
