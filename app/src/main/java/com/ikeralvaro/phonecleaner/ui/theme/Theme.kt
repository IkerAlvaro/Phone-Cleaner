package com.ikeralvaro.phonecleaner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Indica si la interfaz está en modo oscuro (puede no coincidir con el sistema si el usuario lo fuerza). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Tema de Phone Cleaner: blanco y verde en claro, negro y verde en oscuro.
 * El color dinámico (Material You) está desactivado a propósito para mantener el verde de la marca.
 */
@Composable
fun PhoneCleanerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
