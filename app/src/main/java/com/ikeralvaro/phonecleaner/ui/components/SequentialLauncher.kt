package com.ikeralvaro.phonecleaner.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Abre pantallas del sistema una tras otra (p. ej. los Ajustes de varias apps).
 * Cuando el usuario vuelve, [onReturned] decide cuál es la siguiente (o null para terminar).
 * Devuelve la función que inicia la secuencia con el primer elemento.
 */
@Composable
fun rememberSequentialLauncher(intentFor: (String) -> Intent, onReturned: () -> String?): (String) -> Unit {
    val currentIntentFor by rememberUpdatedState(intentFor)
    val currentOnReturned by rememberUpdatedState(onReturned)
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pending = currentOnReturned()
    }
    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        pending = null
        try {
            launcher.launch(currentIntentFor(target))
        } catch (_: ActivityNotFoundException) {
            pending = currentOnReturned()
        }
    }
    return remember { { target: String -> pending = target } }
}
