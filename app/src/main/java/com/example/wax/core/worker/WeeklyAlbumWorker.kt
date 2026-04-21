package com.example.wax.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.wax.R
import com.example.wax.core.auth.TokenManager
import com.example.wax.domain.usecase.GetWeeklyAlbumUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that fires once a week (every Sunday at 09:00) to surface an
 * album-of-the-week notification to the user.
 *
 * **[@HiltWorker] and [@AssistedInject]:**
 * WorkManager cannot use standard constructor injection because it constructs workers
 * via reflection using only [Context] and [WorkerParameters]. [@HiltWorker] generates a
 * Hilt-aware factory; [@AssistedInject] lets Hilt provide [tokenManager] and
 * [getWeeklyAlbumUseCase] while WorkManager supplies the [@Assisted] parameters
 * ([appContext] and [workerParams]). The factory is registered in
 * [com.example.wax.WaxApp.workManagerConfiguration].
 *
 * **[CoroutineWorker] vs [androidx.work.Worker]:**
 * - [androidx.work.Worker] executes synchronously on a background thread managed by
 *   WorkManager's executor. Any asynchronous work (network calls, DataStore reads) must
 *   be wrapped with `runBlocking`, which is wasteful and can starve the thread pool.
 * - [CoroutineWorker] runs [doWork] inside a coroutine on WorkManager's default dispatcher
 *   ([kotlinx.coroutines.Dispatchers.Default]). Suspension points (network calls, token
 *   reads) yield the thread rather than blocking it, making the worker composable with
 *   other concurrent work and much easier to test with `TestCoroutineDispatcher`.
 *
 * **Initial delay calculation (scheduled in [com.example.wax.MainActivity]):**
 * The first firing is delayed to the upcoming Sunday at 09:00. The delay is computed as:
 * `target.timeInMillis - Calendar.getInstance().timeInMillis`. If Sunday 09:00 has
 * already passed this week, one week is added so the worker never fires in the past.
 * Subsequent firings repeat every 7 days (`PeriodicWorkRequestBuilder(7, TimeUnit.DAYS)`).
 */
@HiltWorker
class WeeklyAlbumWorker @AssistedInject constructor(
    /** Application context provided by WorkManager at construction time. */
    @Assisted appContext: Context,
    /** WorkManager parameters (tags, input data, run attempt count, etc.). */
    @Assisted workerParams: WorkerParameters,
    /** Checks token validity and retrieves the stored Spotify access token. */
    private val tokenManager: TokenManager,
    /** Use-case that calls the Spotify API to fetch a featured/weekly album. */
    private val getWeeklyAlbumUseCase: GetWeeklyAlbumUseCase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        /** Notification channel ID shared with [com.example.wax.WaxApp] channel creation. */
        const val CHANNEL_ID      = "weekly_album"

        /** Stable notification ID so repeated weekly firings update the same notification. */
        private const val NOTIFICATION_ID = 2001
    }

    /**
     * Entry point called by WorkManager when the periodic interval elapses.
     *
     * **Return values:**
     * - [Result.success] — work completed (notification posted or gracefully skipped).
     *   WorkManager schedules the next periodic run.
     * - [Result.failure] — work encountered a non-retryable error. WorkManager stops
     *   retrying this run but still schedules the next periodic interval.
     * - [Result.retry] (not used here) — transient failure; WorkManager retries with
     *   exponential back-off before the next scheduled interval.
     *
     * This worker always returns [Result.success] because a missing token or API failure
     * is handled gracefully by showing a generic fallback message. There is no benefit to
     * retrying — if the token is absent now it will still be absent seconds later, and the
     * notification will simply fire again next Sunday.
     *
     * **Why the token check happens before the API call:**
     * [GetWeeklyAlbumUseCase] requires a valid Bearer token. Calling it without one would
     * result in a 401 Unauthorized response and an exception. Checking [TokenManager.isTokenValid]
     * first avoids a wasteful network round-trip and provides a clear, user-friendly fallback
     * message ("Open Wax to discover this week's pick") for users who are logged out or
     * whose token has expired.
     */
    override suspend fun doWork(): Result {
        ensureChannelExists()

        val notifTitle = "Your album of the week is ready 🎵"
        val notifBody: String = try {
            // Only attempt an API call if we have a valid, non-expired access token
            val token = if (tokenManager.isTokenValid()) tokenManager.getAccessToken() else null
            if (token != null) {
                val album = getWeeklyAlbumUseCase(token)
                "${album.name} · ${album.artistNames.joinToString(", ")}"
            } else {
                // No valid token — user is logged out or token expired; show generic prompt
                "Open Wax to discover this week's pick"
            }
        } catch (e: Exception) {
            // Network or API error — fall back gracefully rather than surfacing a crash
            "Open Wax to discover this week's pick"
        }

        showNotification(notifTitle, notifBody)
        return Result.success()
    }

    /**
     * Creates the [CHANNEL_ID] notification channel if it does not already exist.
     *
     * Although the channel is created in [com.example.wax.WaxApp.onCreate], WorkManager
     * may execute this worker in a process where [WaxApp.onCreate] has not run (e.g., in
     * the WorkManager process on older API levels). Re-creating the channel here is safe
     * because [NotificationManager.createNotificationChannel] is idempotent.
     */
    private fun ensureChannelExists() {
        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Weekly Album",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Weekly album of the week reminder" }
            )
        }
    }

    /**
     * Builds and posts the weekly album notification.
     *
     * [NotificationCompat.Builder] is used instead of the platform [android.app.Notification.Builder]
     * to maintain backward compatibility. [setAutoCancel] dismisses the notification when
     * the user taps it, providing the expected swipe-to-dismiss behaviour.
     *
     * @param title The notification title (e.g., "Your album of the week is ready 🎵").
     * @param body  The notification body — either the album name + artist or a generic
     *              fallback string when no token is available.
     */
    private fun showNotification(title: String, body: String) {
        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        mgr.notify(NOTIFICATION_ID, notification)
    }
}
