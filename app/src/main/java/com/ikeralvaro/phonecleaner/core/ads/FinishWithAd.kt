package com.ikeralvaro.phonecleaner.core.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ikeralvaro.phonecleaner.LocalAppContainer
import com.ikeralvaro.phonecleaner.core.permissions.findActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Para salir de una pantalla de resultado SOLO desde un botón explícito («Volver al inicio», «Hecho»);
 * el botón atrás del sistema debe salir sin anuncio. Para salir de una pantalla de resultado: pasa por [AdsManager.onTaskFinished] (que decide si toca anuncio a
 * pantalla completa) y después ejecuta la acción. Ignora toques repetidos mientras está en curso.
 */
@Composable
fun rememberFinishWithAd(): (then: () -> Unit) -> Unit {
    val ads = LocalAppContainer.current.adsManager
    val context = LocalContext.current
    return remember(ads, context) {
        val busy = AtomicBoolean(false)
        val action: (() -> Unit) -> Unit = { then ->
            if (busy.compareAndSet(false, true)) {
                val activity = context.findActivity()
                if (activity == null) {
                    busy.set(false)
                    then()
                } else {
                    try {
                        ads.onTaskFinished(activity) {
                            busy.set(false)
                            then()
                        }
                    } catch (e: Exception) {
                        // Nunca se queda bloqueado: si algo falla se libera y se sale igualmente.
                        busy.set(false)
                        throw e
                    }
                }
            }
        }
        action
    }
}
