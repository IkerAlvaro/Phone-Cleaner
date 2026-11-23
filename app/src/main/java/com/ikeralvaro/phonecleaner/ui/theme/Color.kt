package com.ikeralvaro.phonecleaner.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Modo claro: blanco y verde.
val Green600 = Color(0xFF16A34A)
val Green700 = Color(0xFF15803D)
val Green100 = Color(0xFFDCFCE7)
val Green950 = Color(0xFF052E16)
val InkLight = Color(0xFF0F1A14)

// Modo oscuro: negro puro (AMOLED) y verde.
val Green500 = Color(0xFF22C55E)
val Green400 = Color(0xFF4ADE80)
val Green900 = Color(0xFF14532D)
val InkDark = Color(0xFFE8F5EC)

val Green800 = Color(0xFF166534)

// primary Green700 (y no Green600) para que el texto verde y el blanco sobre verde pasen WCAG AA.
val LightColors = lightColorScheme(
    primary = Green700,
    onPrimary = Color.White,
    primaryContainer = Green100,
    onPrimaryContainer = Green950,
    inversePrimary = Green400,
    secondary = Green800,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBBF7D0),
    onSecondaryContainer = Green950,
    // "tertiary" se usa como color de aviso (temperatura alta, apps sin restricción).
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color.White,
    onBackground = InkLight,
    surface = Color.White,
    onSurface = InkLight,
    surfaceVariant = Color(0xFFF1F8F3),
    onSurfaceVariant = Color(0xFF4B5B51),
    surfaceTint = Green700,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE3EDE6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6FBF7),
    surfaceContainer = Color(0xFFF1F8F3),
    surfaceContainerHigh = Color(0xFFE8F3EB),
    surfaceContainerHighest = Color(0xFFDDEBE1),
    outline = Color(0xFFB9CFC0),
    outlineVariant = Color(0xFFDCEBE0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A),
)

val DarkColors = darkColorScheme(
    primary = Green500,
    onPrimary = Color(0xFF00210E),
    primaryContainer = Green900,
    onPrimaryContainer = Color(0xFFBBF7D0),
    inversePrimary = Green600,
    secondary = Green400,
    onSecondary = Color(0xFF00210E),
    secondaryContainer = Color(0xFF0F3D22),
    onSecondaryContainer = Color(0xFFBBF7D0),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF261A00),
    tertiaryContainer = Color(0xFF3D2E00),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color.Black,
    onBackground = InkDark,
    surface = Color.Black,
    onSurface = InkDark,
    surfaceVariant = Color(0xFF0D1410),
    onSurfaceVariant = Color(0xFF9DB3A5),
    surfaceTint = Green500,
    surfaceBright = Color(0xFF1A251E),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF070C09),
    surfaceContainer = Color(0xFF0D1410),
    surfaceContainerHigh = Color(0xFF131C16),
    surfaceContainerHighest = Color(0xFF1B2620),
    outline = Color(0xFF34473B),
    outlineVariant = Color(0xFF1E2B23),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFEE2E2),
)
