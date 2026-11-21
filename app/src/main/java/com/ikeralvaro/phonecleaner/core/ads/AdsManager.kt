package com.ikeralvaro.phonecleaner.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.ikeralvaro.phonecleaner.BuildConfig
import com.ikeralvaro.phonecleaner.feature.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Anuncios de Google AdMob: inicialización tras el consentimiento, tarjetas nativas (ver [NativeAdCard]) y un
 * anuncio a pantalla completa al terminar una tarea, con el límite de frecuencia de [AdFrequencyPolicy].
 */
class AdsManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val consent: ConsentManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false

    /** Hay anuncios si el usuario dio su consentimiento, no compró «Quitar anuncios» y el SDK está listo. */
    val adsEnabled: StateFlow<Boolean> = combine(consent.canRequestAds, settings.settings) { canRequest, s ->
        canRequest && !s.premium
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val nativeUnitId: String get() = BuildConfig.ADMOB_NATIVE_UNIT_ID

    /** Se llama cuando el consentimiento está resuelto. Inicializa el SDK una sola vez. */
    fun initializeIfPossible() {
        if (!consent.canRequestAds.value || !started.compareAndSet(false, true)) return
        scope.launch {
            withContext(Dispatchers.IO) { MobileAds.initialize(context) {} }
            preloadInterstitial()
        }
    }

    private fun preloadInterstitial() {
        if (!adsEnabled.value || !started.get() || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                }
            },
        )
    }

    /**
     * El usuario sale de una pantalla de resultado (limpieza, RAM, compresión). Si la política lo permite y hay
     * un anuncio cargado, se muestra a pantalla completa; [then] se ejecuta al cerrarlo (o enseguida si no toca).
     */
    fun onTaskFinished(activity: Activity, then: () -> Unit) {
        scope.launch {
            val current = settings.settings.first()
            val now = System.currentTimeMillis()
            val offset = TimeZone.getDefault().getOffset(now).toLong()
            val history = AdFrequencyPolicy.afterTask(current.adHistory)
            val ad = interstitial
            val show = ad != null && adsEnabled.value &&
                AdFrequencyPolicy.shouldShow(history, now, offset, current.premium, consent.canRequestAds.value)
            if (!show || ad == null) {
                settings.saveAdHistory(history)
                then()
                preloadInterstitial()
                return@launch
            }
            settings.saveAdHistory(AdFrequencyPolicy.afterShown(history, now, offset))
            interstitial = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    then()
                    preloadInterstitial()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    then()
                    preloadInterstitial()
                }
            }
            ad.show(activity)
        }
    }
}
