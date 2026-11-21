package com.ikeralvaro.phonecleaner.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ikeralvaro.phonecleaner.ui.theme.cardBorderColor
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ikeralvaro.phonecleaner.R
import com.ikeralvaro.phonecleaner.appViewModel
import com.ikeralvaro.phonecleaner.feature.apps.AppDetailScreen
import com.ikeralvaro.phonecleaner.feature.apps.AppsScreen
import com.ikeralvaro.phonecleaner.feature.battery.BatteryScreen
import com.ikeralvaro.phonecleaner.feature.battery.openBatterySaver
import com.ikeralvaro.phonecleaner.feature.boost.BoostScreen
import com.ikeralvaro.phonecleaner.feature.cleaner.CleanerScreen
import com.ikeralvaro.phonecleaner.feature.compress.CompressScreen
import com.ikeralvaro.phonecleaner.feature.home.HomeScreen
import com.ikeralvaro.phonecleaner.feature.home.HomeTool
import com.ikeralvaro.phonecleaner.feature.media.MediaCategory
import com.ikeralvaro.phonecleaner.feature.media.MediaReviewScreen
import com.ikeralvaro.phonecleaner.feature.media.MediaScreen
import com.ikeralvaro.phonecleaner.feature.media.MediaViewModel
import com.ikeralvaro.phonecleaner.feature.monitor.MonitorScreen
import com.ikeralvaro.phonecleaner.feature.onboarding.OnboardingScreen
import com.ikeralvaro.phonecleaner.feature.settings.SettingsScreen

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
    TopLevel(Routes.APPS, R.string.nav_apps, Icons.Rounded.Apps),
    TopLevel(Routes.FILES, R.string.nav_files, Icons.Rounded.FolderOpen),
    TopLevel(Routes.SYSTEM, R.string.nav_system, Icons.Rounded.Speed),
)

@Composable
fun PhoneCleanerRoot(showOnboarding: Boolean) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val context = LocalContext.current
    // Compartido entre la pestaña Archivos y la revisión de cada categoría (se analiza una sola vez).
    val mediaViewModel = appViewModel { MediaViewModel(it) }
    // El destino inicial se fija una vez: al terminar el onboarding se navega a Inicio sin rehacer el grafo.
    val startDestination = remember { if (showOnboarding) Routes.ONBOARDING else Routes.HOME }

    Scaffold(
        bottomBar = {
            if (currentRoute in Routes.topLevel) BottomBar(navController, currentRoute)
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 12 } },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onQuickScan = { navController.navigate(Routes.cleaner(autoStart = true)) },
                    onTool = { tool ->
                        when (tool) {
                            HomeTool.CLEANER -> navController.navigate(Routes.cleaner(autoStart = false))
                            HomeTool.BOOST -> navController.navigate(Routes.BOOST)
                            HomeTool.BATTERY -> navController.navigate(Routes.BATTERY)
                            HomeTool.COMPRESS -> navController.navigate(Routes.COMPRESS)
                            HomeTool.APPS -> navController.navigateTopLevel(Routes.APPS)
                            HomeTool.FILES -> navController.navigateTopLevel(Routes.FILES)
                        }
                    },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.APPS) {
                AppsScreen(onOpenApp = { navController.navigate(Routes.appDetail(it)) })
            }
            composable(Routes.FILES) {
                MediaScreen(
                    viewModel = mediaViewModel,
                    onOpenCategory = { navController.navigate(Routes.mediaReview(it.name)) },
                    onOpenCompress = { navController.navigate(Routes.COMPRESS) },
                )
            }
            composable(Routes.SYSTEM) {
                MonitorScreen(
                    onOpenBattery = { navController.navigate(Routes.BATTERY) },
                    onOpenBoost = { navController.navigate(Routes.BOOST) },
                    onBatterySaver = { context.openBatterySaver() },
                )
            }
            composable(
                Routes.CLEANER,
                arguments = listOf(navArgument("autoStart") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                CleanerScreen(
                    autoStart = entry.arguments?.getBoolean("autoStart") ?: false,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.BOOST) { BoostScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.BATTERY) {
                BatteryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBoost = { navController.navigate(Routes.BOOST) },
                    onOpenApp = { navController.navigate(Routes.appDetail(it)) },
                )
            }
            composable(Routes.COMPRESS) { CompressScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable(
                Routes.APP_DETAIL,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { entry ->
                AppDetailScreen(
                    packageName = entry.arguments?.getString("packageName").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.MEDIA_REVIEW,
                arguments = listOf(navArgument("category") { type = NavType.StringType }),
            ) { entry ->
                val category = runCatching { MediaCategory.valueOf(entry.arguments?.getString("category").orEmpty()) }
                    .getOrDefault(MediaCategory.DUPLICATES)
                MediaReviewScreen(mediaViewModel, category, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    // Barra tipo "píldora" flotante.
    NavigationBar(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, cardBorderColor(), RoundedCornerShape(28.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0),
    ) {
        TOP_LEVEL.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { navController.navigateTopLevel(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        // Inicio es siempre la raíz (el onboarding se quita de la pila al terminar).
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
