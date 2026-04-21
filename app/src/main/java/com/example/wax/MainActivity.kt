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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.wax.core.auth.SpotifyAuthManager
import com.example.wax.core.media.MediaPlaybackService
import com.example.wax.core.preferences.UserPreferencesRepository
import com.example.wax.core.worker.WeeklyAlbumWorker
import com.example.wax.presentation.WaxNavGraph
import com.example.wax.presentation.main.AuthEvent
import com.example.wax.presentation.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The single Activity that hosts the entire Wax Compose UI.
 *
 * Annotated with [@AndroidEntryPoint], which enables Hilt field injection in this Activity.
 * Hilt generates a base class that sets up a scoped DI component; fields annotated with
 * [@Inject] are populated before [onCreate] is called, so injected dependencies are always
 * available by the time any lifecycle method runs.
 *
 * Responsibilities:
 * - Holds the splash-screen until the app's initial data load is complete.
 * - Requests runtime permissions (notifications, overlay).
 * - Starts [MediaPlaybackService] when the notification listener is enabled.
 * - Schedules the weekly album WorkManager task.
 * - Delegates all navigation to [WaxNavGraph] via Jetpack Compose.
 * - Handles the Spotify OAuth callback URI from both cold-start and singleTop resume paths.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Hilt-injected manager that builds and launches the Spotify authorization Intent
     * and exchanges the returned authorization code for an access token.
     */
    @Inject lateinit var spotifyAuthManager: SpotifyAuthManager

    /**
     * Hilt-injected repository used to read user preferences (e.g., whether the weekly
     * notification is enabled) before scheduling WorkManager tasks.
     */
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    /**
     * Activity-scoped [MainViewModel] shared with all composable screens via [WaxNavGraph].
     * Using [viewModels] ties the ViewModel lifetime to this Activity, ensuring it survives
     * configuration changes and is the same instance across all screens.
     */
    private val viewModel: MainViewModel by viewModels()

    /**
     * Guards against repeatedly sending the user to the system overlay-permission screen.
     * Set to `true` the first time the overlay permission dialog is launched so that
     * subsequent [onResume] calls do not re-open Settings every time.
     */
    // Tracks whether we have already prompted for the overlay permission this session
    // so we don't send the user to Settings on every onResume().
    private var overlayPermissionPrompted = false

    /**
     * Launcher for the [Manifest.permission.POST_NOTIFICATIONS] runtime permission dialog.
     * The result callback is a no-op because the service and worker handle the absence of
     * the permission gracefully — notifications simply won't appear without it.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    /**
     * Entry point for a cold start of the Activity.
     *
     * Key initialization order:
     * 1. [installSplashScreen] **must** be called before [super.onCreate] so the splash
     *    theme is applied to the window before it is first drawn; calling it after would
     *    cause a brief white flash.
     * 2. [setKeepOnScreenCondition] reads [MainViewModel.isReady]: while `false` the
     *    splash stays visible, giving the ViewModel time to load the first album and
     *    resolve the onboarding flag from DataStore before the UI is revealed.
     * 3. [handleCallbackIntent] is called with the launching [Intent] to handle the case
     *    where Spotify's OAuth redirect URI cold-started the app (the user was not already
     *    in the app when authentication completed).
     *
     * @param savedInstanceState Standard Bundle passed by the system on recreation.
     */
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
        maybeScheduleWeeklyNotification()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0D0D0D))) {
                WaxNavGraph()
            }
        }

        // Handle callback URI if the activity was cold-started by the redirect
        handleCallbackIntent(intent)
    }

    /**
     * Called when the Activity is already running at the top of the back stack (singleTop
     * launch mode) and Spotify redirects back to the app via the custom URI scheme.
     *
     * Without this override the callback URI would be silently ignored when the Activity
     * is already running, because the system delivers the new Intent here rather than
     * re-running [onCreate]. Together, [onCreate] and [onNewIntent] cover both the
     * cold-start and the already-running (singleTop resume) paths.
     *
     * @param intent The new Intent delivered by the system, carrying the Spotify callback URI.
     */
    // Called when the activity is already running (singleTop) and Spotify redirects back
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallbackIntent(intent)
    }

    /**
     * Re-evaluates service and permission state every time the Activity returns to the
     * foreground, covering the case where the user just granted notification access or
     * returned from the system overlay-permission screen.
     */
    // Also re-check on resume in case the user just granted notification access
    override fun onResume() {
        super.onResume()
        maybeStartMediaPlaybackService()
        maybeRequestOverlayPermission()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Requests the [Manifest.permission.POST_NOTIFICATIONS] runtime permission on
     * Android 13 (API 33 / TIRAMISU) and above, where it became mandatory.
     * On older API levels the permission is implicitly granted and no dialog is shown.
     */
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
     *
     * The overlay permission ([Settings.ACTION_MANAGE_OVERLAY_PERMISSION]) allows
     * [com.example.wax.presentation.overlay.OverlayPlayerActivity] to draw over other
     * apps and over the keyguard (lock screen). Without it the overlay Activity would
     * be blocked by the system on Android 6.0+ (API 23).
     *
     * Notification listener access is checked first because the lock-screen feature
     * requires both: the listener to detect what is playing, and the overlay to display it.
     * Asking for overlay before notification access would be confusing to the user.
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
     * Starts [MediaPlaybackService] if the app has been granted notification listener access.
     *
     * The service relies on the notification listener API to detect currently playing media,
     * so starting it without that permission would be pointless. The notification listener
     * access is enabled by the user in system settings (not a runtime permission dialog).
     *
     * Safe to call multiple times — [startService] is idempotent for an already-running
     * service; the system simply delivers a new start command which the service ignores.
     */
    private fun maybeStartMediaPlaybackService() {
        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            startService(Intent(this, MediaPlaybackService::class.java))
        }
    }

    /**
     * Schedules the weekly album notification WorkManager task if the user has the
     * feature enabled in preferences.
     *
     * The initial delay is calculated so the first firing lands on the coming Sunday at 09:00.
     * [ExistingPeriodicWorkPolicy.KEEP] ensures that if the work is already enqueued (e.g.,
     * from a previous app launch), the existing schedule is preserved rather than reset —
     * this prevents the reminder from drifting every time the app is opened.
     *
     * If the user has disabled the weekly notification toggle, the work was already
     * cancelled at that point, so we simply skip re-scheduling here.
     */
    private fun maybeScheduleWeeklyNotification() {
        lifecycleScope.launch {
            if (!userPreferencesRepository.isWeeklyNotifEnabled()) return@launch

            val now = Calendar.getInstance()
            // Calculate the delay to the next Sunday at 09:00
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If the target time is in the past this week, advance to next Sunday
                if (!after(now)) add(Calendar.WEEK_OF_YEAR, 1)
            }
            val delayMs = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<WeeklyAlbumWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                "weekly_album_notification",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    /**
     * Observes [MainViewModel.authEvent] and reacts to authentication-related one-shot
     * events emitted by the ViewModel.
     *
     * Uses [repeatOnLifecycle] with [Lifecycle.State.STARTED] so collection is active only
     * while the Activity is visible. This prevents events from being processed in the
     * background (e.g., while the Spotify auth browser tab is open on top), which could
     * cause duplicate Intent launches or ANRs. The coroutine is automatically cancelled
     * when the lifecycle drops below STARTED and restarted when it rises back to STARTED.
     */
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

    /**
     * Parses the Spotify OAuth callback URI from [intent] and forwards the authorization
     * code to the ViewModel for token exchange.
     *
     * The Spotify SDK redirects back to the app using the custom URI scheme
     * `es.uv.adm.wax://callback?code=<auth_code>`. We validate both the scheme and host
     * before extracting the code to avoid processing unrelated deep-link Intents.
     *
     * Called from both [onCreate] (cold start) and [onNewIntent] (singleTop resume) to
     * guarantee the callback is handled regardless of whether the Activity was newly
     * created or was already running when Spotify redirected back.
     *
     * @param intent The Intent to inspect for a Spotify callback URI; may be `null` if
     *               the Activity was started without an Intent.
     */
    private fun handleCallbackIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        // Only handle our own OAuth redirect scheme
        if (uri.scheme == "es.uv.adm.wax" && uri.host == "callback") {
            val code = uri.getQueryParameter("code") ?: return
            viewModel.handleAuthCallback(code)
        }
    }
}
