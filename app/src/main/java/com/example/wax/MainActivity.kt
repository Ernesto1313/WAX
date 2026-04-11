package com.example.wax

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.wax.core.auth.SpotifyAuthManager
import com.example.wax.core.media.MediaPlaybackService
import com.example.wax.presentation.WaxNavGraph
import com.example.wax.presentation.main.AuthEvent
import com.example.wax.presentation.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var spotifyAuthManager: SpotifyAuthManager

    private val viewModel: MainViewModel by viewModels()

    // Tracks whether we have already prompted for the overlay permission this session
    // so we don't send the user to Settings on every onResume().
    private var overlayPermissionPrompted = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Granted or denied — WorkManager will post silently when ready
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen must be called before super.onCreate so the splash theme
        // is applied before the window is first drawn.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash visible until the first album (or auth event) is ready.
        splashScreen.setKeepOnScreenCondition { !viewModel.isReady.value }
        enableEdgeToEdge()

        requestPostNotificationsIfNeeded()
        observeAuthEvents()
        maybeStartMediaPlaybackService()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0D0D0D))) {
                WaxNavGraph()
            }
        }

        // Handle callback URI if the activity was cold-started by the redirect
        handleCallbackIntent(intent)
    }

    // Called when the activity is already running (singleTop) and Spotify redirects back
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallbackIntent(intent)
    }

    // Also re-check on resume in case the user just granted notification access
    override fun onResume() {
        super.onResume()
        maybeStartMediaPlaybackService()
        maybeRequestOverlayPermission()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Prompts for the SYSTEM_ALERT_WINDOW (overlay) permission once per session,
     * but only after the user has already granted notification listener access —
     * both permissions are needed for the lock screen feature to work on Samsung One UI.
     */
    private fun maybeRequestOverlayPermission() {
        if (overlayPermissionPrompted) return
        if (Settings.canDrawOverlays(this)) return
        val notifGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (!notifGranted) return   // no point asking until notification access is granted
        overlayPermissionPrompted = true
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    /**
     * Starts MediaPlaybackService when the notification listener is enabled.
     * Safe to call multiple times — startService is idempotent for a running service.
     */
    private fun maybeStartMediaPlaybackService() {
        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            startService(Intent(this, MediaPlaybackService::class.java))
        }
    }

    private fun observeAuthEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authEvent.collect { event ->
                    when (event) {
                        is AuthEvent.LaunchAuth -> startActivity(spotifyAuthManager.getAuthIntent())
                    }
                }
            }
        }
    }

    private fun handleCallbackIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        // Only handle our own OAuth redirect scheme
        if (uri.scheme == "es.uv.adm.wax" && uri.host == "callback") {
            val code = uri.getQueryParameter("code") ?: return
            viewModel.handleAuthCallback(code)
        }
    }
}
