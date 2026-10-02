package com.houseofsilver.cardscanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.houseofsilver.cardscanner.CardScannerApp
import com.houseofsilver.cardscanner.ui.capture.CaptureScreen
import com.houseofsilver.cardscanner.ui.confirmation.ConfirmationScreen
import com.houseofsilver.cardscanner.ui.help.HelpScreen
import com.houseofsilver.cardscanner.ui.history.HistoryScreen
import com.houseofsilver.cardscanner.ui.mycard.MyCardScreen
import com.houseofsilver.cardscanner.ui.mycard.ShareCardScreen
import com.houseofsilver.cardscanner.ui.review.ReviewScreen
import com.houseofsilver.cardscanner.ui.settings.SettingsScreen
import com.houseofsilver.cardscanner.ui.settings.TemplateEditorScreen

sealed class Screen(val route: String) {
    object Capture : Screen("capture")
    object Review : Screen("review/{scanId}") {
        fun createRoute(scanId: String) = "review/$scanId"
    }
    object Confirmation : Screen("confirmation/{scanId}") {
        fun createRoute(scanId: String) = "confirmation/$scanId"
    }
    object History : Screen("history")
    object Settings : Screen("settings")
    object TemplateEditor : Screen("template_editor")
    object Help : Screen("help")
    object MyCard : Screen("my_card")
    object ShareCard : Screen("share_card")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val appModule = (context.applicationContext as CardScannerApp).appModule

    NavHost(
        navController = navController,
        startDestination = Screen.Capture.route
    ) {
        composable(Screen.Capture.route) {
            CaptureScreen(
                appModule = appModule,
                onNavigateToReview = { scanId ->
                    navController.navigate(Screen.Review.createRoute(scanId))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToShareCard = {
                    navController.navigate(Screen.ShareCard.route)
                }
            )
        }

        composable(
            route = Screen.Review.route,
            arguments = listOf(navArgument("scanId") { type = NavType.StringType })
        ) { backStack ->
            val scanId = backStack.arguments?.getString("scanId") ?: return@composable
            ReviewScreen(
                scanId = scanId,
                appModule = appModule,
                onNavigateToConfirmation = { id ->
                    navController.navigate(Screen.Confirmation.createRoute(id)) {
                        popUpTo(Screen.Capture.route)
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Confirmation.route,
            arguments = listOf(navArgument("scanId") { type = NavType.StringType })
        ) { backStack ->
            val scanId = backStack.arguments?.getString("scanId") ?: return@composable
            ConfirmationScreen(
                scanId = scanId,
                appModule = appModule,
                onScanNext = {
                    navController.navigate(Screen.Capture.route) {
                        popUpTo(Screen.Capture.route) { inclusive = true }
                    }
                },
                onViewHistory = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.Capture.route)
                    }
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                appModule = appModule,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                appModule = appModule,
                onNavigateToTemplateEditor = {
                    navController.navigate(Screen.TemplateEditor.route)
                },
                onNavigateToHelp = {
                    navController.navigate(Screen.Help.route)
                },
                onNavigateToMyCard = {
                    navController.navigate(Screen.MyCard.route)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TemplateEditor.route) {
            TemplateEditorScreen(
                appModule = appModule,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.MyCard.route) {
            MyCardScreen(
                appModule = appModule,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ShareCard.route) {
            ShareCardScreen(
                appModule = appModule,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMyCard = {
                    navController.navigate(Screen.MyCard.route) {
                        popUpTo(Screen.Capture.route)
                    }
                }
            )
        }
    }
}
