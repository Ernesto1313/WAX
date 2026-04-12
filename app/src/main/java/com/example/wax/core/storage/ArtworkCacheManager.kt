package com.example.wax.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches album artwork bitmaps in the app's internal storage (filesDir/artwork/).
 * No external-storage permission is required — filesDir is private to the app.
 */
@Singleton
class ArtworkCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val artworkDir: File
        get() = File(context.filesDir, "artwork").also { it.mkdirs() }

    /** Returns the cached File for [albumId], or null if not cached yet. */
    fun loadArtwork(albumId: String): File? {
        val file = File(artworkDir, "${albumId.sanitize()}.jpg")
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Returns the best URL to use for [albumId]:
     * - A `file://` URI pointing to the cached file if it exists
     * - Otherwise the original [networkUrl]
     */
    fun resolveUrl(albumId: String, networkUrl: String): String {
        val cached = loadArtwork(albumId)
        return if (cached != null) Uri.fromFile(cached).toString() else networkUrl
    }

    /** Saves [bitmap] to internal storage as JPEG 90. Cleans up on failure. */
    fun saveArtwork(albumId: String, bitmap: Bitmap) {
        val file = File(artworkDir, "${albumId.sanitize()}.jpg")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            file.delete()   // don't leave a partial file
        }
    }

    /** Deletes cached artwork files that are older than 30 days. */
    fun clearOldArtwork() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        artworkDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    // Strip characters that are unsafe for file names
    private fun String.sanitize(): String = replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
