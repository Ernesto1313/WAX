package com.example.wax

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.wax.core.worker.WeeklyAlbumWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WaxApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createWeeklyAlbumChannel()
    }

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
