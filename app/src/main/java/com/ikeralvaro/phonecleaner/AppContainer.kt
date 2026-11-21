package com.ikeralvaro.phonecleaner

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val permissionManager = PermissionManager(appContext)
    val memoryReader = MemoryReader(appContext)
    val storageReader = StorageReader(appContext)
    val batteryReader = BatteryReader(appContext)
    val cpuReader = CpuReader()
    val thermalReader = ThermalReader(appContext)
    val appRepository = AppRepository(appContext)
    val junkScanner = JunkScanner(appContext, appRepository)
    val memoryBooster = MemoryPressureBooster(appContext, memoryReader, appRepository)
    val mediaRepository = MediaRepository(appContext, permissionManager)
    private val mediaSaver = MediaSaver(appContext)
    val photoCompressor = PhotoCompressor(appContext, mediaSaver)
    val videoCompressor = VideoCompressor(appContext, mediaSaver)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer no disponible") }

/** Crea un ViewModel con acceso al contenedor. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
