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
 * Caches album artwork bitmaps on the device's internal storage so they can be loaded
 * instantly without a network round-trip.
 *
 * **Storage location — why [Context.getFilesDir] instead of external storage:**
 * [Context.getFilesDir] returns a directory that is:
 * - Private to the app — no other app can read it without root access.
 * - Always available — not affected by the user unmounting an SD card.
 * - Permission-free — no `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE` manifest
 *   permission is needed on any API level, which avoids the runtime permission dialog and
 *   the storage-scoped access restrictions introduced in Android 10+.
 *
 * All artwork files live in the `artwork/` subdirectory of [Context.getFilesDir].
 *
 * **File naming convention:**
 * Each cached file is named `<sanitized-albumId>.jpg`. Using the Spotify album ID as the
 * file name provides a stable, deterministic cache key — if the same album is encountered
 * again its cached file is found immediately without any index or database lookup.
 * The ID is sanitized ([String.sanitize]) to strip characters that are illegal in file
 * names (e.g., `/`, `?`, spaces) before constructing the path.
 *
 * **Singleton scope:**
 * Annotated with [@Singleton] so all callers share the same instance and [artworkDir]
 * is resolved with the same [Context] throughout the process lifetime.
 */
@Singleton
class ArtworkCacheManager @Inject constructor(
    /** Application context injected by Hilt; used to resolve [Context.getFilesDir]. */
    @ApplicationContext private val context: Context
) {
    /**
     * Lazily resolved directory where artwork files are stored.
     *
     * Computed each time as a property (rather than stored in `init`) so that if the
     * directory is ever deleted at runtime, [mkdirs] recreates it on the next access.
     */
    private val artworkDir: File
        get() = File(context.filesDir, "artwork").also { it.mkdirs() }

    /**
     * Returns the cached [File] for the given [albumId], or `null` if no valid cache
     * entry exists.
     *
     * A file with zero length is treated as a missing entry because it indicates a
     * previous [saveArtwork] call that failed and left an empty file behind.
     *
     * @param albumId The Spotify album ID used as the cache key.
     * @return The cached [File] if it exists and is non-empty, otherwise `null`.
     */
    fun loadArtwork(albumId: String): File? {
        val file = File(artworkDir, "${albumId.sanitize()}.jpg")
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Resolves the best URL to use when loading artwork for [albumId].
     *
     * If a local cache file exists, a `file://` URI pointing to it is returned so that
     * image-loading libraries (e.g., Coil) skip the network entirely. Otherwise the
     * original [networkUrl] is returned as a fallback.
     *
     * @param albumId    The Spotify album ID used to look up the cached file.
     * @param networkUrl The remote artwork URL to fall back to when no cache entry exists.
     * @return A `file://` URI string if cached, otherwise [networkUrl].
     */
    fun resolveUrl(albumId: String, networkUrl: String): String {
        val cached = loadArtwork(albumId)
        return if (cached != null) Uri.fromFile(cached).toString() else networkUrl
    }

    /**
     * Compresses [bitmap] as JPEG and writes it to the cache file for [albumId].
     *
     * **Why JPEG and quality 90:**
     * Album artwork is a photographic image with smooth colour gradients, for which JPEG
     * achieves a much better size/quality ratio than PNG (lossless). Quality 90 is a
     * well-established sweet spot: it reduces file size by ~70 % compared to quality 100
     * while the compression artefacts remain imperceptible at typical album-art display
     * sizes (up to ~500 × 500 dp). Lossless PNG would be 3–5× larger for no visible
     * quality gain on device screens.
     *
     * If writing fails for any reason (e.g., disk full), the partial file is deleted so
     * that a subsequent [loadArtwork] call does not return a corrupt or empty file.
     *
     * @param albumId The Spotify album ID used to derive the cache file name.
     * @param bitmap  The decoded artwork bitmap to persist.
     */
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

    /**
     * Deletes cached artwork files whose last-modified timestamp is older than 30 days.
     *
     * **30-day cleanup rationale:**
     * - Albums that have not been played in 30 days are unlikely to be needed again soon,
     *   so keeping their artwork wastes internal storage.
     * - 30 days gives enough headroom for albums that recur weekly (e.g., the weekly pick)
     *   to remain cached between appearances without being evicted prematurely.
     * - [File.lastModified] is updated by [saveArtwork] each time artwork is written, so
     *   frequently accessed albums naturally reset their 30-day clock.
     *
     * This method is intended to be called periodically (e.g., from a WorkManager task or
     * on app start) and is safe to call at any time — listing an empty or missing directory
     * simply returns `null` / an empty array with no deletions.
     */
    fun clearOldArtwork() {
        // cutoff is the earliest timestamp that should be kept; anything older is deleted
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        artworkDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    /**
     * Replaces any character that is not alphanumeric, an underscore, or a hyphen with
     * an underscore, making the string safe for use as a file name component.
     *
     * Spotify album IDs are already alphanumeric, but sanitizing defensively ensures the
     * cache continues to work if the ID format ever changes or a test value is passed in.
     */
    // Strip characters that are unsafe for file names
    private fun String.sanitize(): String = replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
