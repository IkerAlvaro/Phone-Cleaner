package com.ikeralvaro.phonecleaner.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Consentimiento de anuncios con la plataforma de Google (UMP), obligatorio en el Espacio Económico Europeo.
 * El formulario se configura en AdMob > Privacidad y mensajes; aquí solo se pide y se muestra si hace falta.
 */
class ConsentManager(context: Context) {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)

    private val _canRequestAds = MutableStateFlow(info.canRequestAds())
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** En cada arranque: actualiza el estado y, si hace falta, muestra el formulario. Después llama a [onDone]. */
    fun gather(activity: Activity, onDone: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    refresh()
                    onDone()
                }
            },
            {
                refresh()
                onDone()
            },
        )
        // Con el consentimiento de una sesión anterior se puede empezar a cargar ya.
        refresh()
        if (info.canRequestAds()) onDone()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { refresh() }
    }

    private fun refresh() {
        _canRequestAds.value = info.canRequestAds()
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
}
