package com.example.wax.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Elevated dark surface used as the bottom navigation bar container background. */
private val BarBackground = Color(0xFF1A1A1A)

/** Spotify brand green applied to the icon and label of the currently selected tab. */
private val BarGreen      = Color(0xFF1DB954)

/** Mid-grey used for unselected tab icons and labels to visually de-emphasize inactive tabs. */
private val BarUnselected = Color(0xFF666666)

/**
 * Bottom navigation bar composable shared across all top-level tab destinations.
 *
 * Renders three [NavigationBarItem]s — Home, History, and Settings — with the active tab
 * highlighted in [BarGreen] and inactive tabs rendered in [BarUnselected]. An indicator pill
 * at `0xFF2A2A2A` appears behind the selected item's icon to subtly mark the active state
 * without overpowering the Spotify-green color.
 *
 * This composable is stateless: [currentRoute] is the single source of truth for which tab
 * is selected, and all navigation is delegated to the provided callbacks. It is embedded in
 * [WaxNavGraph]'s [Scaffold] `bottomBar` slot and shown only when [currentRoute] is one of
 * the top-level tab routes (`"home"`, `"history"`, `"settings"`).
 *
 * @param currentRoute          The route string of the currently visible destination; drives
 *                              the `selected` parameter of each [NavigationBarItem].
 * @param onNavigateToHome      Invoked when the Home tab is tapped.
 * @param onNavigateToHistory   Invoked when the History tab is tapped.
 * @param onNavigateToSettings  Invoked when the Settings tab is tapped.
 */
@Composable
fun WaxBottomBar(
    currentRoute: String?,
    onNavigateToHome: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor   = BarGreen,
        selectedTextColor   = BarGreen,
        unselectedIconColor = BarUnselected,
        unselectedTextColor = BarUnselected,
        indicatorColor      = Color(0xFF2A2A2A)
    )

    NavigationBar(
        containerColor = BarBackground,
        contentColor   = Color.White
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick  = onNavigateToHome,
            icon     = { Icon(Icons.Default.Home,    contentDescription = "Home") },
            label    = { Text("Home") },
            colors   = itemColors
        )
        NavigationBarItem(
            selected = currentRoute == "history",
            onClick  = onNavigateToHistory,
            icon     = { Icon(Icons.Default.History, contentDescription = "History") },
            label    = { Text("History") },
            colors   = itemColors
        )
        NavigationBarItem(
            selected = currentRoute == "settings",
            onClick  = onNavigateToSettings,
            icon     = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label    = { Text("Settings") },
            colors   = itemColors
        )
    }
}
