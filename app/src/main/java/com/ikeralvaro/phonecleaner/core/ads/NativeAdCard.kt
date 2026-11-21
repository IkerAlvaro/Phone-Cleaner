package com.ikeralvaro.phonecleaner.core.ads

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.ikeralvaro.phonecleaner.LocalAppContainer
import com.ikeralvaro.phonecleaner.R

/** Dónde va la tarjeta: en Inicio es compacta; en las pantallas de resultado muestra imagen o vídeo. */
enum class AdPlacement { HOME, RESULT }

/** En las capturas de pantalla (sin red) se dibuja una tarjeta de ejemplo en lugar del anuncio real. */
val LocalAdPreview = staticCompositionLocalOf { false }

private data class AdColors(val text: Int, val secondary: Int, val primary: Int, val onPrimary: Int, val labelBg: Int)

/**
 * Anuncio nativo de AdMob con el aspecto de las tarjetas de la app (verde sobre blanco o negro). Lleva la etiqueta
 * «Anuncio» y el icono AdChoices. Si no hay consentimiento, el usuario compró «Quitar anuncios» o no llega
 * ningún anuncio, no ocupa espacio.
 */
@Composable
fun NativeAdCard(placement: AdPlacement, modifier: Modifier = Modifier) {
    if (LocalAdPreview.current) {
        PreviewAdCard(placement, modifier)
        return
    }
    val ads = LocalAppContainer.current.adsManager
    val enabled by ads.adsEnabled.collectAsStateWithLifecycle()
    if (!enabled) return

    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(placement) {
        var disposed = false
        var loaded: NativeAd? = null
        val loader = AdLoader.Builder(context, ads.nativeUnitId)
            .forNativeAd { ad ->
                if (disposed) {
                    ad.destroy()
                } else {
                    loaded?.destroy()
                    loaded = ad
                    nativeAd = ad
                }
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .build(),
            )
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            disposed = true
            loaded?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return
    val scheme = MaterialTheme.colorScheme
    val colors = AdColors(
        text = scheme.onSurface.toArgb(),
        secondary = scheme.onSurfaceVariant.toArgb(),
        primary = scheme.primary.toArgb(),
        onPrimary = scheme.onPrimary.toArgb(),
        labelBg = scheme.primaryContainer.toArgb(),
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx -> LayoutInflater.from(ctx).inflate(R.layout.native_ad_card, FrameLayout(ctx), false) as NativeAdView },
            update = { view -> bind(view, ad, placement, colors) },
        )
    }
}

private fun bind(view: NativeAdView, ad: NativeAd, placement: AdPlacement, colors: AdColors) {
    val headline = view.findViewById<TextView>(R.id.ad_headline)
    val body = view.findViewById<TextView>(R.id.ad_body)
    val advertiser = view.findViewById<TextView>(R.id.ad_advertiser)
    val label = view.findViewById<TextView>(R.id.ad_label)
    val icon = view.findViewById<ImageView>(R.id.ad_icon)
    val cta = view.findViewById<TextView>(R.id.ad_cta)
    val media = view.findViewById<MediaView>(R.id.ad_media)

    headline.text = ad.headline
    headline.setTextColor(colors.text)
    body.text = ad.body
    body.setTextColor(colors.secondary)
    body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    advertiser.text = ad.advertiser
    advertiser.setTextColor(colors.secondary)
    advertiser.visibility = if (ad.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
    label.setTextColor(colors.primary)
    label.backgroundTintList = ColorStateList.valueOf(colors.labelBg)
    val drawable = ad.icon?.drawable
    icon.setImageDrawable(drawable)
    icon.visibility = if (drawable == null) View.GONE else View.VISIBLE
    cta.text = ad.callToAction
    cta.setTextColor(colors.onPrimary)
    cta.backgroundTintList = ColorStateList.valueOf(colors.primary)
    cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    val showMedia = placement == AdPlacement.RESULT && ad.mediaContent != null
    media.visibility = if (showMedia) View.VISIBLE else View.GONE

    view.headlineView = headline
    view.bodyView = body
    view.advertiserView = advertiser
    view.iconView = icon
    view.callToActionView = cta
    view.mediaView = if (showMedia) media else null
    view.setNativeAd(ad)
}

/** Réplica en Compose de la tarjeta, solo para las capturas de pantalla. */
@Composable
private fun PreviewAdCard(placement: AdPlacement, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.secondaryContainer),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.ad_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(scheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Anunciante", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Text("Título del anuncio", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Texto del anunciante que describe su producto en una o dos líneas.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (placement == AdPlacement.RESULT) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceContainerHighest),
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("Instalar", color = scheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
