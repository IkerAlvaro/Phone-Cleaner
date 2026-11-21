package com.ikeralvaro.phonecleaner.screenshots

import com.ikeralvaro.phonecleaner.core.permissions.PermissionActions
import com.ikeralvaro.phonecleaner.core.system.BatteryHealth
import com.ikeralvaro.phonecleaner.core.system.BatterySnapshot
import com.ikeralvaro.phonecleaner.core.system.ChargeStatus
import com.ikeralvaro.phonecleaner.core.system.CoreFreq
import com.ikeralvaro.phonecleaner.core.system.CpuSnapshot
import com.ikeralvaro.phonecleaner.core.system.MemorySnapshot
import com.ikeralvaro.phonecleaner.core.system.PowerSource
import com.ikeralvaro.phonecleaner.core.system.StorageSnapshot
import com.ikeralvaro.phonecleaner.core.system.ThermalLevel
import com.ikeralvaro.phonecleaner.core.system.ThermalSnapshot
import com.ikeralvaro.phonecleaner.core.util.DAY_MS
import com.ikeralvaro.phonecleaner.feature.apps.AppEntry
import com.ikeralvaro.phonecleaner.feature.cleaner.AppCacheState
import com.ikeralvaro.phonecleaner.feature.cleaner.JunkCategory
import com.ikeralvaro.phonecleaner.feature.cleaner.JunkGroup
import com.ikeralvaro.phonecleaner.feature.cleaner.JunkItem
import com.ikeralvaro.phonecleaner.feature.media.MediaGroup
import com.ikeralvaro.phonecleaner.feature.media.MediaItem

/** Datos de ejemplo para las capturas de pantalla. */
object FakeData {
    const val NOW = 1_795_000_000_000L
    const val GB = 1_000_000_000L
    const val MB = 1_000_000L

    val storage = StorageSnapshot(totalBytes = 128 * GB, freeBytes = 41 * GB)
    val memory = MemorySnapshot(totalBytes = 8 * GB, availableBytes = 2_900 * MB, thresholdBytes = 500 * MB, lowMemory = false)
    val battery = BatterySnapshot(
        levelPercent = 76, status = ChargeStatus.CHARGING, source = PowerSource.AC, health = BatteryHealth.GOOD,
        temperatureC = 31.5f, voltageMv = 4120, currentMa = 1250, estimatedCapacityMah = 4820, cycleCount = 312,
        technology = "Li-ion",
    )
    val cpu = CpuSnapshot(
        cores = 8,
        socName = "Qualcomm SM8550",
        freqs = listOf(
            CoreFreq(0, 1_200_000, 300_000, 2_016_000), CoreFreq(1, 960_000, 300_000, 2_016_000),
            CoreFreq(2, 1_804_000, 300_000, 2_016_000), CoreFreq(3, 300_000, 300_000, 2_016_000),
            CoreFreq(4, 2_400_000, 499_000, 2_803_000), CoreFreq(5, 1_100_000, 499_000, 2_803_000),
            CoreFreq(6, 2_803_000, 499_000, 2_803_000), CoreFreq(7, 1_500_000, 595_000, 3_187_000),
        ),
        estimatedLoad = 0.46f,
    )
    val thermal = ThermalSnapshot(ThermalLevel.NONE, cpuTempC = 38.4f)

    fun app(pkg: String, label: String, size: Long, installedDaysAgo: Int, updatedDaysAgo: Int, boot: Boolean = false, system: Boolean = false, unrestricted: Boolean = false) =
        AppEntry(
            packageName = pkg, label = label, isSystem = system, versionName = "5.2.1",
            installedAt = NOW - installedDaysAgo * DAY_MS, updatedAt = NOW - updatedDaysAgo * DAY_MS,
            apkBytes = size, startsAtBoot = boot, ignoresBatteryOptimizations = unrestricted,
        )

    val apps = listOf(
        app("com.whatsapp", "WhatsApp", 182 * MB, 900, 2, boot = true, unrestricted = true),
        app("com.instagram.android", "Instagram", 164 * MB, 700, 5, boot = true),
        app("com.zhiliaoapp.musically", "TikTok", 410 * MB, 300, 1, boot = true),
        app("com.spotify.music", "Spotify", 96 * MB, 500, 9, boot = true, unrestricted = true),
        app("com.google.android.youtube", "YouTube", 140 * MB, 1000, 3, boot = true, system = true),
        app("com.example.game", "Puzzle Quest", 1_250 * MB, 60, 60),
        app("com.example.maps", "Mapas Offline", 88 * MB, 45, 45),
        app("com.example.notes", "Notas", 12 * MB, 3, 3),
    )

    private fun junk(category: JunkCategory, vararg items: Pair<String, Long>) =
        JunkGroup(category, items.map { (path, size) -> JunkItem("/storage/emulated/0/$path", path, size, isDirectory = false) })

    val junkGroups = listOf(
        junk(JunkCategory.THUMBNAILS, "DCIM/.thumbnails" to 312 * MB),
        junk(JunkCategory.TEMP_FILES, "WhatsApp/log.tmp" to 30 * MB, "Telegram/cache.log" to 18 * MB),
        junk(JunkCategory.EMPTY_FOLDERS, "OldApp/Backups" to 0L, "Pictures/Viaje 2019" to 0L),
        junk(JunkCategory.APK_INSTALLED, "Download/whatsapp.apk" to 120 * MB, "Download/game.apk" to 292 * MB),
        junk(JunkCategory.APK_OTHER, "Bluetooth/tool.apk" to 96 * MB),
        junk(JunkCategory.OLD_DOWNLOADS, "Download/video.mp4" to 980 * MB, "Download/manual.pdf" to 22 * MB),
        junk(JunkCategory.RESIDUAL_FOLDERS, "Android/media/com.oldapp" to 230 * MB),
    )
    val appCache = AppCacheState()

    private var nextId = 1L
    fun photo(size: Long, w: Int = 4000, h: Int = 3000, takenAgo: Long = DAY_MS, path: String = "DCIM/Camera/") =
        (nextId++).let { id -> MediaItem(id, "content://media/external/images/media/$id", "IMG_$id.jpg", size, w, h, NOW - takenAgo, "image/jpeg", path) }

    fun video(size: Long, duration: Long) = (nextId++).let { id ->
        MediaItem(id, "content://media/external/video/media/$id", "VID_$id.mp4", size, 1920, 1080, NOW - DAY_MS, "video/mp4", "DCIM/Camera/", durationMs = duration)
    }

    fun group(vararg items: MediaItem) = MediaGroup(items.toList(), items.maxBy { it.pixels }.id)

    val permissionActions = PermissionActions({}, {}, {})
}
