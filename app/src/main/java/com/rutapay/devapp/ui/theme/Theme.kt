package com.rutapay.devapp.ui.theme

import android.app.Activity
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
import com.rutapay.devapp.ui.theme.RutaPayPrimary

private val DarkColorScheme = darkColorScheme(
    primary = RutaPayPrimary,
    secondary = RutaPaySecondary,
    tertiary = RutaPayAccent,

    secondaryContainer = RutaPaySecondary,
    onSecondaryContainer = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = RutaPayPrimary,
    secondary = RutaPaySecondary,
    tertiary = RutaPayAccent,

    // Colores Monocromáticos
    background = Color(0xFFFFFFFF), // Blanco puro
    surface = Color(0xFFF8F9FA),    // Gris casi blanco
    onBackground = Color(0xFF1A1C1E), // Negro azulado muy oscuro para texto
    onSurface = Color(0xFF1A1C1E),

    // Colores para cuando el fondo es el color primario
    primaryContainer = RutaPayPrimary,
    onPrimaryContainer = Color.White
)

@Composable
fun RutaPayDevAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
        typography = Typography,
        content = content
    )
}