package com.example.wax

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.wax.core.work.WeeklyAlbumWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WaxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val weeklyAlbumChannel = NotificationChannel(
            WeeklyAlbumWorker.CHANNEL_ID,
            "Weekly Album",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Weekly curated album recommendation"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(weeklyAlbumChannel)
    }
}
