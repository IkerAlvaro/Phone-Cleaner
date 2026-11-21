package com.ikeralvaro.phonecleaner

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.ikeralvaro.phonecleaner.core.notifications.Notifications
import com.ikeralvaro.phonecleaner.feature.settings.CleanReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PhoneCleanerApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        appScope.launch {
            container.settingsRepository.settings
                .map { it.reminderEnabled }
                .distinctUntilChanged()
                .collect { CleanReminderWorker.schedule(this@PhoneCleanerApp, it) }
        }
    }

    /** Miniaturas de fotos y fotogramas de vídeo en las cuadrículas. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
