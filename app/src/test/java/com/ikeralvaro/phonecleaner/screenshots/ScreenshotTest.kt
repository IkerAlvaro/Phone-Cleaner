package com.ikeralvaro.phonecleaner.screenshots

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import com.github.takahirom.roborazzi.captureRoboImage
import com.ikeralvaro.phonecleaner.core.permissions.PermissionSnapshot
import com.ikeralvaro.phonecleaner.core.util.DAY_MS
import com.ikeralvaro.phonecleaner.core.util.HOUR_MS
import com.ikeralvaro.phonecleaner.core.util.MINUTE_MS
import com.ikeralvaro.phonecleaner.feature.apps.AppDetailContent
import com.ikeralvaro.phonecleaner.feature.apps.AppSort
import com.ikeralvaro.phonecleaner.feature.apps.AppsContent
import com.ikeralvaro.phonecleaner.feature.apps.AppsUiState
import com.ikeralvaro.phonecleaner.feature.battery.BatteryContent
import com.ikeralvaro.phonecleaner.feature.battery.BatteryUiState
import com.ikeralvaro.phonecleaner.feature.boost.BoostContent
import com.ikeralvaro.phonecleaner.feature.boost.BoostPhase
import com.ikeralvaro.phonecleaner.feature.boost.BoostUiState
import com.ikeralvaro.phonecleaner.feature.cleaner.CleanerContent
import com.ikeralvaro.phonecleaner.feature.cleaner.CleanerPhase
import com.ikeralvaro.phonecleaner.feature.cleaner.CleanerUiState
import com.ikeralvaro.phonecleaner.feature.compress.CompressCandidate
import com.ikeralvaro.phonecleaner.feature.compress.CompressContent
import com.ikeralvaro.phonecleaner.feature.compress.CompressUiState
import com.ikeralvaro.phonecleaner.feature.home.HomeContent
import com.ikeralvaro.phonecleaner.feature.home.HomeUiState
import com.ikeralvaro.phonecleaner.feature.media.MediaCategory
import com.ikeralvaro.phonecleaner.feature.media.MediaContent
import com.ikeralvaro.phonecleaner.feature.media.MediaPhase
import com.ikeralvaro.phonecleaner.feature.media.MediaReviewContent
import com.ikeralvaro.phonecleaner.feature.media.MediaUiState
import com.ikeralvaro.phonecleaner.feature.monitor.DeviceInfo
import com.ikeralvaro.phonecleaner.feature.monitor.MonitorContent
import com.ikeralvaro.phonecleaner.feature.monitor.MonitorUiState
import com.ikeralvaro.phonecleaner.feature.onboarding.OnboardingContent
import com.ikeralvaro.phonecleaner.feature.settings.AppSettings
import com.ikeralvaro.phonecleaner.feature.settings.SettingsContent
import com.ikeralvaro.phonecleaner.screenshots.FakeData.GB
import com.ikeralvaro.phonecleaner.screenshots.FakeData.MB
import com.ikeralvaro.phonecleaner.screenshots.FakeData.NOW
import com.ikeralvaro.phonecleaner.ui.theme.PhoneCleanerTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de todas las pantallas en modo claro y oscuro con datos de ejemplo.
 * Se generan con: ./gradlew :app:recordRoborazziDebug  → docs/screenshots/
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "es-rES-w400dp-h880dp-xxhdpi", application = Application::class)
class ScreenshotTest(private val dark: Boolean) {

    @get:Rule
    val compose = createComposeRule()

    @OptIn(DelicateCoilApi::class)
    @Before
    fun fakeImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val palette = listOf(0xFF7CB342, 0xFF4FC3F7, 0xFFFFB74D, 0xFFBA68C8, 0xFF4DB6AC, 0xFFE57373, 0xFF9575CD, 0xFFAED581)
        val engine = FakeImageLoaderEngine.Builder().apply {
            palette.forEachIndexed { i, color ->
                intercept({ data: Any -> data is String && (data.substringAfterLast('/').toLongOrNull() ?: 0L) % palette.size == i.toLong() }, ColorImage(color.toInt()))
            }
        }.default(ColorImage(0xFF90A4AE.toInt())).build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components { add(engine) }.coroutineContext(Dispatchers.Unconfined).build(),
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { PhoneCleanerTheme(darkTheme = dark) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/${name}_${if (dark) "oscuro" else "claro"}.png")
    }

    @Test fun home() = capture("01_inicio") {
        HomeContent(
            HomeUiState(FakeData.storage, FakeData.memory, FakeData.battery, lastCleanAt = NOW - 2 * DAY_MS, totalFreedBytes = 3_400 * MB),
            onQuickScan = {}, onTool = {}, onSettings = {}, now = NOW,
        )
    }

    @Test fun cleanerResults() = capture("02_limpieza") {
        val groups = FakeData.junkGroups
        CleanerContent(
            state = CleanerUiState(
                phase = CleanerPhase.RESULTS,
                groups = groups,
                selectedPaths = groups.filter { it.category.preselected }.flatMap { g -> g.items.map { it.path } }.toSet(),
                appCache = FakeData.appCache,
                hasFilesAccess = true,
                hasUsageAccess = true,
            ),
            hasFilesAccess = true, hasUsageAccess = true, onBack = {}, onScan = {}, onGrantFiles = {}, onGrantUsage = {},
            onToggleCategory = {}, onToggleItem = {}, onToggleExpanded = {}, onClean = {}, onClearAllCaches = {}, onOpenApp = {},
            sdkInt = 35,
        )
    }

    @Test fun cleanerDone() = capture("03_limpieza_hecha") {
        CleanerContent(
            state = CleanerUiState(phase = CleanerPhase.DONE, freedBytes = 1_240 * MB),
            hasFilesAccess = true, hasUsageAccess = true, onBack = {}, onScan = {}, onGrantFiles = {}, onGrantUsage = {},
            onToggleCategory = {}, onToggleItem = {}, onToggleExpanded = {}, onClean = {}, onClearAllCaches = {}, onOpenApp = {},
        )
    }

    @Test fun boost() = capture("04_liberar_ram") {
        val candidates = FakeData.apps.filter { (it.lastUsedAt ?: 0) > NOW - 3 * HOUR_MS - MINUTE_MS }
        BoostContent(
            BoostUiState(
                phase = BoostPhase.READY, memory = FakeData.memory, candidates = candidates,
                selected = candidates.map { it.packageName }.toSet(), canStopOtherApps = false,
            ),
            onBack = {}, onToggle = {}, onGrantUsage = {}, onBoost = {}, now = NOW,
        )
    }

    @Test fun apps() = capture("05_apps") {
        AppsContent(
            AppsUiState(loading = false, apps = FakeData.apps, sort = AppSort.SIZE, selected = setOf("com.example.game"), now = NOW),
            onBack = null, onSort = {}, onToggle = {}, onOpenApp = {}, onGrantUsage = {}, onStopSelected = {}, onUninstallSelected = {},
        )
    }

    @Test fun appDetail() = capture("06_ficha_app") {
        AppDetailContent(FakeData.apps[1], loading = false, onBack = {}, onOpenSettings = {}, onUninstall = {}, now = NOW)
    }

    @Test fun battery() = capture("07_bateria") {
        BatteryContent(
            BatteryUiState(
                battery = FakeData.battery,
                topApps = FakeData.apps.filter { it.foregroundMs24h > 0 }.sortedByDescending { it.foregroundMs24h }.take(4),
                unrestrictedApps = FakeData.apps.filter { it.ignoresBatteryOptimizations },
                loadingApps = false,
            ),
            onBack = {}, onGrantUsage = {}, onOpenApp = {}, onBatterySaver = {}, onDisplay = {}, onOpenBoost = {}, onUnrestrictedSettings = {},
        )
    }

    @Test fun monitor() = capture("08_sistema") {
        MonitorContent(
            MonitorUiState(
                cpu = FakeData.cpu, thermal = FakeData.thermal, memory = FakeData.memory, storage = FakeData.storage,
                battery = FakeData.battery,
                device = DeviceInfo("Google Pixel 9", "17 (API 37)", 3 * DAY_MS + 4 * HOUR_MS),
            ),
            onOpenBattery = {}, onOpenBoost = {}, onBatterySaver = {},
        )
    }

    private fun mediaState(): MediaUiState {
        val similar = listOf(
            FakeData.group(FakeData.photo(4_100_000, takenAgo = DAY_MS), FakeData.photo(3_900_000, takenAgo = DAY_MS - 2000), FakeData.photo(4_000_000, takenAgo = DAY_MS - 4000)),
            FakeData.group(FakeData.photo(3_200_000, takenAgo = 3 * DAY_MS), FakeData.photo(3_300_000, w = 3000, h = 2000, takenAgo = 3 * DAY_MS - 1000)),
        )
        val duplicates = listOf(FakeData.group(FakeData.photo(2_800_000), FakeData.photo(2_800_000)))
        return MediaUiState(
            phase = MediaPhase.READY,
            duplicates = duplicates,
            similar = similar,
            screenshots = List(23) { FakeData.photo(900_000L + it * 10_000, 1080, 2400, path = "Pictures/Screenshots/") },
            largeVideos = listOf(FakeData.video(420 * MB, 4 * 60_000), FakeData.video(180 * MB, 90_000)),
            largeFiles = listOf(FakeData.video(420 * MB, 4 * 60_000)),
        )
    }

    @Test fun files() = capture("09_archivos") {
        MediaContent(mediaState(), hasAccess = true, onBack = null, onGrantMedia = {}, onRefresh = {}, onOpenCategory = {}, onOpenCompress = {})
    }

    @Test fun similarReview() = capture("10_fotos_similares") {
        val state = mediaState()
        MediaReviewContent(state, MediaCategory.SIMILAR, state.defaultSelection(MediaCategory.SIMILAR), onBack = {}, onToggle = {}, onSelectAll = {}, onDelete = {})
    }

    @Test fun compress() = capture("11_comprimir") {
        val photos = List(9) { i ->
            val p = FakeData.photo(5_800_000L - i * 300_000)
            CompressCandidate(p, 2_640_000)
        }
        CompressContent(
            CompressUiState(loading = false, photos = photos, selected = photos.map { it.item.id }.toSet()),
            onBack = {}, onGrantMedia = {}, onTab = {}, onMode = {}, onToggle = {}, onSelectAll = {}, onStart = {},
            onTrashOriginals = {}, onDismissResult = {}, sdkInt = 35,
        )
    }

    @Test fun settings() = capture("12_ajustes") {
        SettingsContent(
            AppSettings(),
            PermissionSnapshot(allFiles = true, usage = true, media = true, notifications = false),
            FakeData.permissionActions,
            version = "1.0.0",
            onBack = {}, onTheme = {}, onReminder = {},
        )
    }

    @Test fun onboardingWelcome() = capture("13_bienvenida") {
        OnboardingContent(0, PermissionSnapshot(false, false, false, false), FakeData.permissionActions, onNext = {}, onFinish = {})
    }

    @Test fun onboardingPermissions() = capture("14_permisos") {
        OnboardingContent(1, PermissionSnapshot(allFiles = true, usage = false, media = true, notifications = false), FakeData.permissionActions, onNext = {}, onFinish = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "oscuro={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
