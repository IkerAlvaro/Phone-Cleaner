package com.ikeralvaro.phonecleaner.core.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.ikeralvaro.phonecleaner.BuildConfig
import com.ikeralvaro.phonecleaner.core.util.MINUTE_MS
import com.ikeralvaro.phonecleaner.feature.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *
 * Los anuncios nativos se guardan en caché (uno por [AdPlacement]) hasta [AD_EXPIRY_MS]: al girar la pantalla o
 * volver a una pantalla no se pide otro. Uso desde la UI: llamar a [requestNative] al mostrarse y observar
 * [nativeAd]; la UI no debe destruir el anuncio (lo hace este gestor al caducar o con [releaseNative]).
 */
class AdsManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val consent: ConsentManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)
    private val consentStarted = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var interstitialLoadedAt = 0L
    private var loadingInterstitial = false

    private val nativeFlows = AdPlacement.entries.associateWith { MutableStateFlow<NativeAd?>(null) }
    private val nativeLoadedAt = mutableMapOf<AdPlacement, Long>()
    private val nativeLoading = mutableSetOf<AdPlacement>()
    /** Ubicaciones que pidieron anuncio antes de que el SDK estuviera listo. */
    private val nativeWanted = mutableSetOf<AdPlacement>()

    /** Hay anuncios si el usuario dio su consentimiento, no compró «Quitar anuncios» y el SDK está listo. */
    val adsEnabled: StateFlow<Boolean> = combine(consent.canRequestAds, settings.settings) { canRequest, s ->
        canRequest && !s.premium
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val nativeUnitId: String get() = BuildConfig.ADMOB_NATIVE_UNIT_ID

    init {
        // Si se compra «Quitar anuncios» o se retira el consentimiento, se liberan los anuncios guardados.
        scope.launch {
            adsEnabled.collect { enabled ->
                if (!enabled) {
                    AdPlacement.entries.forEach(::releaseNative)
                    interstitial = null
                }
            }
        }
    }

    /**
     * Consentimiento (UMP) e inicialización del SDK, una sola vez por proceso. Si el usuario es premium no se
     * pide consentimiento ni se inicializa MobileAds.
     */
    fun startOnce(activity: Activity) {
        if (!consentStarted.compareAndSet(false, true)) return
        scope.launch {
            if (settings.settings.first().premium || activity.isFinishing || activity.isDestroyed) {
                consentStarted.set(false) // se reintentará en el siguiente arranque de la actividad
                return@launch
            }
            consent.gather(activity) { initializeIfPossible() }
        }
    }

    /** Se llama cuando el consentimiento está resuelto. Inicializa el SDK una sola vez. */
    fun initializeIfPossible() {
        if (!consent.canRequestAds.value || !started.compareAndSet(false, true)) return
        scope.launch {
            if (settings.settings.first().premium) {
                started.set(false)
                return@launch
            }
            withContext(Dispatchers.IO) { MobileAds.initialize(context) {} }
            preloadInterstitial()
            AdPlacement.entries.filter { it in nativeWanted }.forEach(::requestNative)
        }
    }

    // ---- Anuncios nativos ----

    /** Anuncio nativo en caché para [placement] (null si no hay o caducó). */
    fun nativeAd(placement: AdPlacement): StateFlow<NativeAd?> = nativeFlows.getValue(placement).asStateFlow()

    /** Pide un anuncio nativo para [placement] si no hay uno válido en caché. Se puede llamar en cada composición. */
    fun requestNative(placement: AdPlacement) {
        nativeWanted += placement
        if (!adsEnabled.value || !started.get()) return
        val flow = nativeFlows.getValue(placement)
        if (flow.value != null && !isExpired(nativeLoadedAt[placement] ?: 0L)) return
        if (flow.value != null) releaseNative(placement)
        if (!nativeLoading.add(placement)) return
        val loader = AdLoader.Builder(context, nativeUnitId)
            .forNativeAd { ad ->
                nativeLoading -= placement
                if (!adsEnabled.value) {
                    ad.destroy()
                } else {
                    flow.value?.destroy()
                    nativeLoadedAt[placement] = SystemClock.elapsedRealtime()
                    flow.value = ad
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeLoading -= placement
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .build(),
            )
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    /** Destruye el anuncio nativo guardado para [placement] (p. ej. para forzar uno nuevo). */
    fun releaseNative(placement: AdPlacement) {
        val flow = nativeFlows.getValue(placement)
        flow.value?.destroy()
        flow.value = null
        nativeLoadedAt.remove(placement)
    }

    // ---- Pantalla completa ----

    private fun preloadInterstitial() {
        if (!adsEnabled.value || !started.get() || loadingInterstitial) return
        if (interstitial != null && !isExpired(interstitialLoadedAt)) return
        interstitial = null
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    interstitialLoadedAt = SystemClock.elapsedRealtime()
                    loadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                }
            },
        )
    }

    /**
     * El usuario sale de una pantalla de resultado (limpieza, RAM, compresión) con un botón explícito
     * («Volver al inicio», «Hecho»); no se debe llamar desde el botón atrás del sistema. Si la política lo
     * permite y hay un anuncio válido, se muestra a pantalla completa. [then] se ejecuta siempre exactamente
     * una vez: al cerrarlo, si falla o enseguida si no toca.
     */
    fun onTaskFinished(activity: Activity, then: () -> Unit) {
        val once = AtomicBoolean(false)
        val finish = { if (once.compareAndSet(false, true)) then() }
        scope.launch {
            try {
                val current = settings.settings.first()
                val now = System.currentTimeMillis()
                val offset = TimeZone.getDefault().getOffset(now).toLong()
                val history = AdFrequencyPolicy.afterTask(current.adHistory)
                if (interstitial != null && isExpired(interstitialLoadedAt)) interstitial = null
                val ad = interstitial
                val activityAlive = !activity.isFinishing && !activity.isDestroyed
                val show = ad != null && activityAlive && adsEnabled.value &&
                    AdFrequencyPolicy.shouldShow(history, now, offset, current.premium, consent.canRequestAds.value)
                if (!show || ad == null) {
                    settings.saveAdHistory(history)
                    finish()
                    preloadInterstitial()
                    return@launch
                }
                settings.saveAdHistory(AdFrequencyPolicy.afterShown(history, now, offset))
                interstitial = null
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        finish()
                        preloadInterstitial()
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        finish()
                        preloadInterstitial()
                    }
                }
                ad.show(activity)
            } catch (e: CancellationException) {
                finish()
                throw e
            } catch (_: Exception) {
                finish()
            }
        }
    }

    private fun isExpired(loadedAt: Long) = loadedAt == 0L || SystemClock.elapsedRealtime() - loadedAt > AD_EXPIRY_MS

    companion object {
        /** AdMob invalida los anuncios cargados pasada una hora; se descartan un poco antes. */
        const val AD_EXPIRY_MS = 55 * MINUTE_MS
    }
}
