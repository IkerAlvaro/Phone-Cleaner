package com.ikeralvaro.phonecleaner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Verdes del logo: claro arriba a la izquierda, intenso abajo a la derecha. */
val LogoGreenLight = Color(0xFF2EE880)
val LogoGreen = Color(0xFF00B046)
val LogoGreenDeep = Color(0xFF008F39)

/** Degradado de la cabecera y del botón principal, como el fondo del logo. */
@Composable
@ReadOnlyComposable
fun brandGradient(): Brush = if (LocalDarkTheme.current) {
    Brush.linearGradient(listOf(Color(0xFF0F5C2F), Color(0xFF063D1E)))
} else {
    Brush.linearGradient(listOf(LogoGreenLight, LogoGreen, LogoGreenDeep))
}

@Composable
@ReadOnlyComposable
fun buttonGradient(): Brush = Brush.horizontalGradient(
    if (LocalDarkTheme.current) listOf(Color(0xFF22C55E), Color(0xFF16A34A)) else listOf(LogoGreen, Color(0xFF15A04A)),
)

/** Borde sutil de las tarjetas "de cristal". */
@Composable
@ReadOnlyComposable
fun cardBorderColor(): Color = MaterialTheme.colorScheme.primary.copy(alpha = if (LocalDarkTheme.current) 0.18f else 0.10f)
