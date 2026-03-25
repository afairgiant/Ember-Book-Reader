package com.ember.reader.ui.theme

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

private val EmberOrange = Color(0xFFE65100)
private val EmberOrangeLight = Color(0xFFFF833A)
private val EmberOrangeDark = Color(0xFFAC1900)

private val LightColorScheme = lightColorScheme(
    primary = EmberOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0A00),
    secondary = Color(0xFF77574D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160E),
    tertiary = Color(0xFF6A5E2F),
    tertiaryContainer = Color(0xFFF3E2A7),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A18),
)

private val DarkColorScheme = darkColorScheme(
    primary = EmberOrangeLight,
    onPrimary = Color(0xFF5F1500),
    primaryContainer = EmberOrangeDark,
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE7BEAF),
    onSecondary = Color(0xFF442A21),
    secondaryContainer = Color(0xFF5D3F36),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD6C68D),
    tertiaryContainer = Color(0xFF51461A),
    surface = Color(0xFF201A18),
    onSurface = Color(0xFFEDE0DB),
)

@Composable
fun EmberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
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
        content = content,
    )
}
