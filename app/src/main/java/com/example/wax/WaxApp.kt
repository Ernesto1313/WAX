package com.example.wax

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.wax.core.worker.WeeklyAlbumWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application subclass for the Wax app.
 *
 * Annotated with [@HiltAndroidApp], which triggers Hilt's code generation and installs
 * the application-level dependency injection component. This annotation causes Hilt to
 * generate a base class for [WaxApp] that sets up the DI graph and makes field injection
 * available throughout the entire app — all Activities, Fragments, ViewModels, Workers,
 * and Services can receive injected dependencies from this root component.
 *
 * The [Application] class is the right place for one-time, global initialization because:
 * - It is created before any Activity, Service, or BroadcastReceiver.
 * - It lives for the entire lifetime of the process, so setup done here is never repeated.
 * - System APIs like [NotificationManager] are available as soon as [onCreate] is called.
 *
 * Implements [Configuration.Provider] to supply a custom [HiltWorkerFactory] to WorkManager,
 * which allows Workers to receive Hilt-injected dependencies.
 */
@HiltAndroidApp
class WaxApp : Application(), Configuration.Provider {

    /**
     * Hilt-injected factory used to construct [androidx.work.ListenableWorker] instances
     * with dependency injection support. Without a custom [HiltWorkerFactory], WorkManager
     * would use its default factory and would be unable to inject dependencies into Workers.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Provides the [Configuration] that WorkManager will use when it initialises.
     *
     * Returning a custom [Configuration] built with [workerFactory] replaces WorkManager's
     * default reflection-based worker instantiation, enabling Hilt to inject dependencies
     * into any [androidx.work.ListenableWorker] annotated with [@HiltWorker].
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Called when the application is starting, before any Activity, Service, or
     * BroadcastReceiver has been created.
     *
     * Notification channels **must** be created here, in [Application.onCreate], because:
     * - Channels must exist before the first notification is posted; creating them lazily
     *   inside a Worker or Activity can race against the first posting attempt.
     * - The [Application] is guaranteed to run before any component that might trigger
     *   a notification (e.g., [WeeklyAlbumWorker]).
     * - On Android 8.0 (API 26) and above, posting to a non-existent channel silently
     *   fails, so pre-creating the channel here avoids invisible notifications.
     */
    override fun onCreate() {
        super.onCreate()
        createWeeklyAlbumChannel()
    }

    /**
     * Creates the notification channel used by [WeeklyAlbumWorker] to surface the
     * weekly album-of-the-week reminder on Android 8.0+ (API 26+).
     *
     * [NotificationChannel] creation is idempotent — calling this method on subsequent
     * app launches is safe and simply updates any mutable channel properties if the
     * channel already exists, without duplicating it.
     */
    private fun createWeeklyAlbumChannel() {
        val channel = NotificationChannel(
            WeeklyAlbumWorker.CHANNEL_ID,
            "Weekly Album",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Weekly album of the week reminder"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
