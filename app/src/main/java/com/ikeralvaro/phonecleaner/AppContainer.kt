package com.ikeralvaro.phonecleaner

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ikeralvaro.phonecleaner.core.ads.AdsManager
import com.ikeralvaro.phonecleaner.core.ads.ConsentManager
import com.ikeralvaro.phonecleaner.core.billing.BillingManager
import com.ikeralvaro.phonecleaner.core.permissions.PermissionManager
import com.ikeralvaro.phonecleaner.core.system.BatteryReader
import com.ikeralvaro.phonecleaner.core.system.CpuReader
import com.ikeralvaro.phonecleaner.core.system.MemoryReader
import com.ikeralvaro.phonecleaner.core.system.StorageReader
import com.ikeralvaro.phonecleaner.core.system.ThermalReader
import com.ikeralvaro.phonecleaner.feature.apps.AppRepository
import com.ikeralvaro.phonecleaner.feature.boost.MemoryPressureBooster
import com.ikeralvaro.phonecleaner.feature.cleaner.JunkScanner
import com.ikeralvaro.phonecleaner.feature.compress.MediaSaver
import com.ikeralvaro.phonecleaner.feature.compress.PhotoCompressor
import com.ikeralvaro.phonecleaner.feature.compress.VideoCompressor
import com.ikeralvaro.phonecleaner.feature.media.MediaRepository
import com.ikeralvaro.phonecleaner.feature.settings.SettingsRepository

/** Inyección de dependencias manual: un único contenedor por proceso. */
/** Inyección de dependencias manual. Todo es perezoso: se crea al usarse por primera vez (arranque rápido). */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val permissionManager by lazy { PermissionManager(appContext) }
    val memoryReader by lazy { MemoryReader(appContext) }
    val storageReader by lazy { StorageReader(appContext) }
    val batteryReader by lazy { BatteryReader(appContext) }
    val cpuReader by lazy { CpuReader() }
    val thermalReader by lazy { ThermalReader(appContext) }
    val appRepository by lazy { AppRepository(appContext) }
    val junkScanner by lazy { JunkScanner(appContext, appRepository) }
    val memoryBooster by lazy { MemoryPressureBooster(appContext, memoryReader, appRepository) }
    val mediaRepository by lazy { MediaRepository(appContext, permissionManager) }
    private val mediaSaver by lazy { MediaSaver(appContext) }
    val photoCompressor by lazy { PhotoCompressor(appContext, mediaSaver) }
    val videoCompressor by lazy { VideoCompressor(appContext, mediaSaver) }
    val consentManager by lazy { ConsentManager(appContext) }
    val adsManager by lazy { AdsManager(appContext, settingsRepository, consentManager) }
    val billingManager by lazy { BillingManager(appContext, settingsRepository) }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer no disponible") }

/** Crea un ViewModel con acceso al contenedor. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
