package fr.calevent.app.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.calevent.app.ui.ScanViewModel
import fr.calevent.app.ui.screens.analysis.AnalysisScreen
import fr.calevent.app.ui.screens.camera.CameraScreen
import fr.calevent.app.ui.screens.home.HomeScreen
import fr.calevent.app.ui.screens.preview.PreviewScreen
import fr.calevent.app.ui.screens.results.ResultsScreen
import fr.calevent.app.ui.screens.send.SendScreen
import fr.calevent.app.ui.screens.settings.SettingsScreen
import fr.calevent.app.ui.screens.success.SuccessScreen

/** Route names. Flow: home > camera > preview > analysis > results > send > success; settings from home. */
object Routes {
    const val Home = "home"
    const val Camera = "camera"
    const val Preview = "preview"
    const val Analysis = "analysis"
    const val Results = "results"
    const val Send = "send"
    const val Success = "success"
    const val Settings = "settings"
}

/**
 * Navigation graph. Screens receive the shared [ScanViewModel] and navigation lambdas only,
 * never the NavController.
 */
@Composable
fun AppNav(navController: NavHostController = rememberNavController()) {
    // Called outside the NavHost: scoped to the activity, shared by every destination.
    val viewModel: ScanViewModel = viewModel()
    val back: () -> Unit = { navController.popBackStack() }

    val state by viewModel.uiState.collectAsState()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    var confirmAbandon by remember { mutableStateOf(false) }
    val goHome: () -> Unit = {
        confirmAbandon = false
        navController.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true }; launchSingleTop = true }
    }
    val unsent = state.slots.isNotEmpty() && state.sendResult == null &&
        (route == Routes.Results || route == Routes.Send)
    val flowClose: (() -> Unit) = { if (unsent) confirmAbandon = true else goHome() }
    val flowRoutes = setOf(Routes.Preview, Routes.Analysis, Routes.Results, Routes.Send)
    val closeFor: (String) -> (() -> Unit)? = { if (it in flowRoutes) flowClose else null }
    if (confirmAbandon) AbandonDialog(onConfirm = goHome, onDismiss = { confirmAbandon = false })

    val fadeMs = 180
    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        enterTransition = { fadeIn(tween(fadeMs)) },
        exitTransition = { fadeOut(tween(fadeMs)) },
        popEnterTransition = { fadeIn(tween(fadeMs)) },
        popExitTransition = { fadeOut(tween(fadeMs)) },
    ) {
        composable(Routes.Home) {
            HomeScreen(
                viewModel = viewModel,
                onTakePhoto = { navController.navigate(Routes.Camera) },
                onImport = { navController.navigate(Routes.Preview) }, // called once the picked photo is imported
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenRecent = { navController.navigate(Routes.Results) },
                onSeeAll = { /* No history screen yet. */ },
            )
        }
        composable(Routes.Camera) { CameraScreen(viewModel, onBack = back, onNext = { navController.navigate(Routes.Preview) }) }
        composable(Routes.Preview) {
            CompositionLocalProvider(LocalFlowClose provides closeFor(Routes.Preview)) {
                PreviewScreen(viewModel, onBack = back, onNext = { navController.navigate(Routes.Analysis) })
            }
        }
        composable(Routes.Analysis) {
            CompositionLocalProvider(LocalFlowClose provides closeFor(Routes.Analysis)) {
            AnalysisScreen(
                viewModel,
                onBack = back,
                // Results replace the analysis: going back from them returns to the preview, not to a new analysis.
                onNext = { navController.navigate(Routes.Results) { popUpTo(Routes.Analysis) { inclusive = true } } },
                onRetake = { if (!navController.popBackStack(Routes.Camera, false)) navController.navigate(Routes.Camera) },
            )
            }
        }
        composable(Routes.Results) {
            CompositionLocalProvider(LocalFlowClose provides closeFor(Routes.Results)) {
            ResultsScreen(
                viewModel,
                onBack = back,
                onNext = { navController.navigate(Routes.Send) },
                onReanalyze = { navController.navigate(Routes.Analysis) { popUpTo(Routes.Results) { inclusive = true } } },
            )
            }
        }
        composable(Routes.Send) {
            CompositionLocalProvider(LocalFlowClose provides closeFor(Routes.Send)) {
                SendScreen(viewModel, onBack = back, onNext = { navController.navigate(Routes.Success) })
            }
        }
        composable(Routes.Success) {
            BackHandler(onBack = goHome)
            SuccessScreen(viewModel, onDone = goHome)
        }
        composable(Routes.Settings) { SettingsScreen(viewModel, onBack = back) }
    }
}
