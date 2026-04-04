package com.example.wax.core.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.wax.MainActivity
import com.example.wax.R
import com.example.wax.core.auth.TokenManager
import com.example.wax.data.repository.AlbumHistoryRepository
import com.example.wax.data.repository.SpotifyRepository
import com.example.wax.domain.model.Album
import com.example.wax.domain.usecase.GetWeeklyAlbumUseCase
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeeklyAlbumWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    // ── Hilt EntryPoint (no @HiltWorker needed — avoids manifest/config changes) ──

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun tokenManager(): TokenManager
        fun spotifyRepository(): SpotifyRepository
        fun getWeeklyAlbumUseCase(): GetWeeklyAlbumUseCase
        fun albumHistoryRepository(): AlbumHistoryRepository
    }

    // ── Work ──────────────────────────────────────────────────────────────────

    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(appContext, WorkerEntryPoint::class.java)

        val token = resolveToken(ep.tokenManager(), ep.spotifyRepository())
            ?: return Result.success() // no credentials — skip silently

        return try {
            val album = ep.getWeeklyAlbumUseCase()(token)
            ep.albumHistoryRepository().saveAlbum(album)
            showNotification(album)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    // ── Token resolution (mirrors MainViewModel.getValidToken) ────────────────

    private suspend fun resolveToken(
        tokenManager: TokenManager,
        repository: SpotifyRepository
    ): String? {
        if (tokenManager.isTokenValid()) return tokenManager.getAccessToken()
        val refresh = tokenManager.getRefreshToken() ?: return null
        return try {
            val dto = repository.refreshToken(refresh)
            tokenManager.saveTokens(dto)
            dto.accessToken
        } catch (e: Exception) {
            null
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private suspend fun showNotification(album: Album) {
        val artistText = album.artistNames.joinToString(", ")
        val coverBitmap = loadCoverBitmap(album.coverUrl)

        val tapIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(coverBitmap)
            .setContentTitle("Your album of the week is ready")
            .setContentText("${album.name} by $artistText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${album.name} by $artistText")
            )
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    private suspend fun loadCoverBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val result = ImageLoader(appContext).execute(
                ImageRequest.Builder(appContext)
                    .data(url)
                    .allowHardware(false)
                    .size(256, 256)
                    .build()
            ) as? SuccessResult
            (result?.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val CHANNEL_ID = "weekly_album"
        const val WORK_NAME  = "weekly_album"
        private const val NOTIFICATION_ID = 1002
    }
}
