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

@HiltWorker
class WeeklyAlbumWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager,
    private val getWeeklyAlbumUseCase: GetWeeklyAlbumUseCase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID      = "weekly_album"
        private const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        ensureChannelExists()

        val notifTitle = "Your album of the week is ready \uD83C\uDFB5"
        val notifBody: String = try {
            val token = if (tokenManager.isTokenValid()) tokenManager.getAccessToken() else null
            if (token != null) {
                val album = getWeeklyAlbumUseCase(token)
                "${album.name} · ${album.artistNames.joinToString(", ")}"
            } else {
                "Open Wax to discover this week's pick"
            }
        } catch (e: Exception) {
            "Open Wax to discover this week's pick"
        }

        showNotification(notifTitle, notifBody)
        return Result.success()
    }

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
