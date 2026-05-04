package com.example.wax.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wax.data.local.AlbumHistoryEntity
import com.example.wax.data.repository.AlbumHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the album listening history screen.
 *
 * Exposes the full sorted history list as a [StateFlow] so [HistoryScreen] recomposes
 * reactively whenever an album is added or removed. All mutations are delegated to
 * [AlbumHistoryRepository], which in turn delegates to the Room DAO.
 *
 * @param albumHistoryRepository Repository providing reactive access to the local history database.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val albumHistoryRepository: AlbumHistoryRepository
) : ViewModel() {

    /**
     * Reactive list of all [AlbumHistoryEntity] objects ordered by [AlbumHistoryEntity.savedAt]
     * descending (most recently played first).
     *
     * Converted from a [kotlinx.coroutines.flow.Flow] to a [StateFlow] using [stateIn] so that:
     * - Compose [collectAsStateWithLifecycle] receives an initial value ([emptyList]) immediately,
     *   avoiding a null-check in the UI.
     * - [SharingStarted.WhileSubscribed] with a 5-second timeout keeps the upstream Room Flow
     *   active while the screen is visible and pauses it 5 seconds after the last subscriber
     *   unsubscribes, avoiding unnecessary database queries when the screen is in the back stack.
     */
    val albums: StateFlow<List<AlbumHistoryEntity>> = albumHistoryRepository
        .getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Deletes the history entry with the given [id] from the local database.
     *
     * Launched in [viewModelScope] so the coroutine is cancelled automatically if the ViewModel
     * is cleared before the delete completes (e.g., the user navigates away mid-operation).
     * The [albums] [StateFlow] will automatically emit an updated list once Room commits the delete.
     *
     * @param id The Spotify album ID of the entry to remove.
     */
    fun deleteAlbum(id: String) {
        viewModelScope.launch { albumHistoryRepository.deleteAlbum(id) }
    }
}
