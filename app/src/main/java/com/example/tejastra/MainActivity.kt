package com.example.tejastra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tejastra.ui.screens.*
import com.example.tejastra.ui.theme.TejAstraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TejAstraTheme {
                TejAstraApp()
            }
        }
    }
}

@Composable
fun TejAstraApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        val openRoute = activity?.intent?.getStringExtra("open_route")
        if (openRoute != null) {
            navController.navigate(openRoute)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(200)) },
    ) {
        composable("dashboard") {
            DashboardScreen(
                onNavigateToAddApp = { modeId -> navController.navigate("add_app/$modeId") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToScreenTime = { navController.navigate("screen_time") },
                onNavigateToFocusMode = { modeId -> navController.navigate("focus_mode/$modeId") },
            )
        }

        composable(
            route = "add_app/{modeId}",
            arguments = listOf(navArgument("modeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            AddAppScreen(
                initialModeId = backStackEntry.arguments?.getString("modeId"),
                onBack = { navController.popBackStack() },
            )
        }

        composable("screen_time") {
            ScreenTimeScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToFocusMode = { modeId -> navController.navigate("focus_mode/$modeId") },
                onNavigateToSchedule = { navController.navigate("schedule") }
            )
        }

        composable("schedule") {
            ScheduleSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "focus_mode/{modeId}",
            arguments = listOf(navArgument("modeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            FocusModeDetailScreen(
                modeId = backStackEntry.arguments?.getString("modeId").orEmpty(),
                onBack = { navController.popBackStack() },
                onNavigateToAddApp = { mode -> navController.navigate("add_app/$mode") },
                onNavigateToNotificationFilters = { mode -> navController.navigate("notification_filters/$mode") },
            )
        }

        composable(
            route = "notification_filters/{modeId}",
            arguments = listOf(navArgument("modeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            NotificationFilterScreen(
                modeId = backStackEntry.arguments?.getString("modeId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
