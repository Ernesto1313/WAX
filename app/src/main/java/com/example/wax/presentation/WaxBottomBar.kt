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

private val BarBackground = Color(0xFF1A1A1A)
private val BarGreen      = Color(0xFF1DB954)
private val BarUnselected = Color(0xFF666666)

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
