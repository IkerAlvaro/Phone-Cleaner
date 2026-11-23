package com.ikeralvaro.phonecleaner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.ikeralvaro.phonecleaner.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Idioma actual de la interfaz, observable por Compose (se recompone si cambia). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** "31,5 °C" con el separador decimal del idioma. */
fun formatCelsius(value: Float, locale: Locale): String = String.format(locale, "%.1f °C", value)

/** Porcentaje con el formato del idioma ("45 %" en español, "45%" en inglés). */
@Composable
@ReadOnlyComposable
fun percent(fraction: Float): String = percentOf((fraction.coerceIn(0f, 1f) * 100).roundToInt())

@Composable
@ReadOnlyComposable
fun percentOf(value: Int): String = stringResource(R.string.percent_value, value)

/** Número entero con separador de miles del idioma (p. ej. "4.500" / "4,500"). */
fun formatNumber(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)
