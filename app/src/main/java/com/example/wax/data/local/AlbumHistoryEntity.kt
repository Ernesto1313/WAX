package com.example.wax.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity representing a single album entry in the listening history.
 *
 * The [@Entity] annotation tells Room to generate a corresponding SQLite table named
 * `album_history`. Each field becomes a column; the column names default to the
 * property names unless overridden with a `@ColumnInfo` annotation.
 *
 * **Why [savedAt] uses [System.currentTimeMillis]:**
 * - [System.currentTimeMillis] returns milliseconds since the Unix epoch as a [Long],
 *   which maps directly to SQLite's `INTEGER` type — no serialization overhead.
 * - Storing epoch millis makes sorting chronologically trivial (`ORDER BY savedAt DESC`)
 *   without any date-parsing cost at query time.
 * - The default value is evaluated at object construction time, so every inserted entity
 *   automatically records when it was saved without the caller needing to supply a timestamp.
 *
 * **Primary key choice:**
 * [id] is the Spotify album ID, which is globally unique. Using it as the [@PrimaryKey]
 * means that inserting the same album twice (handled by [AlbumHistoryDao]'s
 * [androidx.room.OnConflictStrategy.REPLACE]) updates the existing row and refreshes
 * [savedAt] rather than duplicating the entry.
 */
@Entity(tableName = "album_history")
data class AlbumHistoryEntity(
    /** Spotify album ID; used as the primary key to de-duplicate history entries. */
    @PrimaryKey val id: String,

    /** Human-readable album title as returned by the Spotify API. */
    val title: String,

    /** Primary artist name(s) as a display string. */
    val artist: String,

    /** URL of the album's cover artwork image on Spotify's CDN. */
    val coverUrl: String,

    /** Deep-link URL to open the album in the Spotify app or web player. */
    val spotifyUrl: String,

    /** Release year of the album (e.g., `"2019"`). */
    val year: String,

    /** Record label that published the album. */
    val label: String,

    /**
     * Unix epoch timestamp (milliseconds) recording when this entry was saved to history.
     * Defaults to the current time at insertion so the caller never needs to supply it.
     */
    val savedAt: Long = System.currentTimeMillis()
)
