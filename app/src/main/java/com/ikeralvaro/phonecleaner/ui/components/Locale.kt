package com.ikeralvaro.phonecleaner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** Idioma actual de la interfaz, observable por Compose (se recompone si cambia). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** "31,5 °C" con el separador decimal del idioma. */
fun formatCelsius(value: Float, locale: Locale): String = String.format(locale, "%.1f °C", value)
