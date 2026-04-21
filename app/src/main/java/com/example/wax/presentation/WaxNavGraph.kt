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

/**
 * Routes for which the bottom navigation bar is visible.
 *
 * Only top-level tab destinations show the bar; transient screens like "detail"
 * are intentionally excluded so the bar disappears when drilling into an album.
 */
// Bottom bar is shown only for top-level tab destinations
private val BOTTOM_BAR_ROUTES = setOf("home", "history", "settings")

/**
 * Root composable that owns the [androidx.navigation.NavController] and wires together
 * all navigation destinations in the Wax app.
 *
 * **Destinations:**
 * - `onboarding` — shown on first launch; navigates to `home` and removes itself from
 *   the back stack so the user cannot return to it via the back button.
 * - `home` — [MainScreen]; the vinyl-turntable screen displaying the currently playing album.
 *   Navigates to `detail` when the user taps the album art.
 * - `detail` — [DetailScreen]; shows full metadata for the selected album.
 *   Uses the same [MainViewModel] instance as `home` so no data needs to be re-fetched or
 *   serialized; the ViewModel already holds the selected album state.
 * - `history` — [HistoryScreen]; list of previously played albums. Tapping an album calls
 *   [MainViewModel.selectAlbum] and navigates to `detail`.
 * - `settings` — [SettingsScreen]; app settings including Spotify disconnect.
 *
 * **Shared ViewModel:**
 * [MainViewModel] is obtained via [hiltViewModel] scoped to the [ComponentActivity] rather
 * than to individual composable back-stack entries. This means every screen that calls
 * `hiltViewModel(activity)` gets the **same** instance for the lifetime of the Activity.
 * This is intentional: `home` and `detail` both need to read and mutate the selected album
 * without passing it through navigation arguments (the [com.example.wax.domain.model.Album]
 * object graph is large and not trivially Parcelable).
 *
 * **Onboarding gate:**
 * The `onboardingCompleted` flag is read from DataStore via [MainViewModel]. While the flag
 * is still `null` (DataStore has not emitted yet) the function returns early — the splash
 * screen is still covering the window at this point so no blank frame is shown. Once the
 * flag resolves, [startDestination] is set to either `"home"` or `"onboarding"`.
 *
 * **Bottom navigation bar:**
 * [WaxBottomBar] is embedded in the [Scaffold]'s `bottomBar` slot and rendered only when
 * [currentRoute] belongs to [BOTTOM_BAR_ROUTES]. [currentRoute] is derived from
 * [androidx.navigation.compose.currentBackStackEntryAsState], which recomposes the bar
 * whenever the back stack changes, keeping the selected tab indicator in sync.
 */
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

    /**
     * Navigates to a top-level tab destination using standard bottom-navigation semantics:
     * - Pops the back stack to the graph's start destination so tapping a tab never
     *   accumulates a deep back stack of the same screen.
     * - [saveState] / [restoreState] preserve and restore each tab's scroll position and
     *   UI state when switching between tabs, matching the Material 3 navigation pattern.
     * - [launchSingleTop] prevents duplicate instances of the same destination when the
     *   user taps the currently active tab.
     *
     * @param route The navigation route string for the target tab destination.
     */
    // Standard bottom-tab navigation: pop back to start, save/restore state
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Choose the first screen based on whether the user has completed onboarding
    val startDestination = if (onboardingCompleted == true) "home" else "onboarding"

    Scaffold(
        containerColor = Color(0xFF0D0D0D),
        bottomBar = {
            // Only render the bottom bar for top-level tab routes; hide it on detail screen
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
            // Onboarding — shown only on first launch; pops itself so back cannot return here
            composable("onboarding") {
                OnboardingScreen(
                    onContinue = {
                        navController.navigate("home") {
                            // Remove onboarding from the back stack so the system back
                            // button exits the app rather than returning to onboarding
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            // Home — main turntable screen; passes the shared ViewModel directly to avoid
            // re-fetching state that the ViewModel already holds
            composable("home") {
                MainScreen(
                    onNavigateToDetail = { navController.navigate("detail") },
                    viewModel          = mainViewModel
                )
            }
            // Detail — full album metadata; reuses mainViewModel so the selected album
            // is already available without navigation arguments
            composable("detail") {
                DetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel      = mainViewModel
                )
            }
            // History — list of previously played albums; selecting one stores it in
            // the shared ViewModel and opens the detail screen
            composable("history") {
                HistoryScreen(
                    onAlbumClick = { album ->
                        mainViewModel.selectAlbum(album)
                        navController.navigate("detail")
                    }
                )
            }
            // Settings — app preferences; disconnecting from Spotify resets ViewModel
            // state and navigates back to home, clearing the back stack
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
