package com.example.wax.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.wax.presentation.detail.DetailScreen
import com.example.wax.presentation.history.HistoryScreen
import com.example.wax.presentation.main.MainScreen
import com.example.wax.presentation.main.MainViewModel
import com.example.wax.presentation.onboarding.OnboardingScreen
import com.example.wax.presentation.settings.SettingsScreen

// Bottom bar is shown only for top-level tab destinations
private val BOTTOM_BAR_ROUTES = setOf("home", "history", "settings")

@Composable
fun WaxNavGraph() {
    val navController = rememberNavController()
    // Activity-scoped so all screens share the same MainViewModel instance
    val mainViewModel: MainViewModel = hiltViewModel(LocalContext.current as ComponentActivity)

    val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsStateWithLifecycle()
    // Wait until DataStore has resolved the flag — splash is covering us anyway
    if (onboardingCompleted == null) return

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Standard bottom-tab navigation: pop back to start, save/restore state
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val startDestination = if (onboardingCompleted == true) "home" else "onboarding"

    Scaffold(
        containerColor = Color(0xFF0D0D0D),
        bottomBar = {
            if (currentRoute in BOTTOM_BAR_ROUTES) {
                WaxBottomBar(
                    currentRoute         = currentRoute,
                    onNavigateToHome     = { navigateToTab("home") },
                    onNavigateToHistory  = { navigateToTab("history") },
                    onNavigateToSettings = { navigateToTab("settings") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onContinue = {
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("home") {
                MainScreen(
                    onNavigateToDetail = { navController.navigate("detail") },
                    viewModel          = mainViewModel
                )
            }
            composable("detail") {
                DetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel      = mainViewModel
                )
            }
            composable("history") {
                HistoryScreen(
                    onAlbumClick = { album ->
                        mainViewModel.selectAlbum(album)
                        navController.navigate("detail")
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onDisconnected = {
                        mainViewModel.onDisconnected()
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
