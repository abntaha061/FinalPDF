package com.example.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.screens.*

sealed class Screen(val route: String) {
    object Splash    : Screen("splash")
    object Onboarding: Screen("onboarding")
    object Home      : Screen("home")
    object PdfReader : Screen("pdf_reader?uri={uri}") {
        fun createRoute(encodedUri: String) = "pdf_reader?uri=$encodedUri"
    }
    object Settings  : Screen("settings")
    object GestureSettings: Screen("gesture_settings")
    object Statistics: Screen("statistics")
    object MergePdfs  : Screen("merge_pdfs")
    object MultiFileSearch : Screen("multi_file_search")
    object About     : Screen("about")
    object Language  : Screen("language")
    object Signature : Screen("signature")
    object WebView   : Screen("webview?url={url}") {
        fun createRoute(encodedUrl: String) = "webview?url=$encodedUrl"
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,  // always start at splash
        enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
        exitTransition  = { slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300)) },
        popEnterTransition  = { slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)) },
        popExitTransition   = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)) },
        modifier = modifier
    ) {

        // SPLASH — no animation (instant)
        composable(
            route = Screen.Splash.route,
            enterTransition = { fadeIn(tween(0)) },
            exitTransition  = { fadeOut(tween(400)) }
        ) { SplashScreen(navController) }

        // ONBOARDING — slide from right
        composable(Screen.Onboarding.route) { OnboardingScreen(navController) }

        // HOME
        composable(Screen.Home.route) { HomeScreen(navController) }

        // PDF READER — slide from bottom (sheet feel)
        composable(
            route = Screen.PdfReader.route,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
            enterTransition = { slideInVertically(tween(350)) { it } + fadeIn(tween(350)) },
            exitTransition  = { slideOutVertically(tween(350)) { it } + fadeOut(tween(350)) }
        ) { backStack ->
            val encodedUri = backStack.arguments?.getString("uri") ?: return@composable
            val uri = Uri.decode(encodedUri)
            PdfReaderScreen(navController, uri)
        }

        // SETTINGS
        composable(Screen.Settings.route) { SettingsScreen(navController) }

        // STATISTICS
        composable(Screen.Statistics.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val activity = context as? androidx.activity.ComponentActivity
                ?: throw IllegalStateException("Context must be ComponentActivity")
            val viewModel = androidx.compose.runtime.remember(context) {
                androidx.lifecycle.ViewModelProvider(activity)[com.example.ui.PdfViewModel::class.java]
            }
            StatisticsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // ABOUT
        composable(Screen.About.route) { AboutScreen(navController) }

        // MERGE PDFS
        composable(Screen.MergePdfs.route) {
            MergePdfScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReader = { encodedUri ->
                    navController.navigate(Screen.PdfReader.createRoute(encodedUri)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // MULTI FILE SEARCH
        composable(Screen.MultiFileSearch.route) {
            MultiFileSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReader = { encodedUri ->
                    navController.navigate(Screen.PdfReader.createRoute(encodedUri))
                }
            )
        }

        // LANGUAGE
        composable(Screen.Language.route) { LanguageScreen(navController) }

        // GESTURE SETTINGS
        composable(Screen.GestureSettings.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val activity = context as? androidx.activity.ComponentActivity
                ?: throw IllegalStateException("Context must be ComponentActivity")
            val viewModel = androidx.compose.runtime.remember(context) {
                androidx.lifecycle.ViewModelProvider(activity)[com.example.ui.PdfViewModel::class.java]
            }
            GestureSettingsScreen(navController, viewModel)
        }

        // SIGNATURE — slide from bottom
        composable(
            route = Screen.Signature.route,
            enterTransition = { slideInVertically(tween(350)) { it } + fadeIn(tween(350)) },
            exitTransition  = { slideOutVertically(tween(350)) { it } + fadeOut(tween(350)) }
        ) {
            SignatureScreen(navController = navController)
        }

        // WEBVIEW — slide from bottom
        composable(
            route = Screen.WebView.route,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
            enterTransition = { slideInVertically(tween(350)) { it } + fadeIn(tween(350)) },
            exitTransition  = { slideOutVertically(tween(350)) { it } + fadeOut(tween(350)) }
        ) { backStack ->
            val encodedUrl = backStack.arguments?.getString("url") ?: return@composable
            val url = Uri.decode(encodedUrl)
            WebViewScreen(navController, url)
        }
    }
}
