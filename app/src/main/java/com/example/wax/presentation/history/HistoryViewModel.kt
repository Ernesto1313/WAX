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

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val albumHistoryRepository: AlbumHistoryRepository
) : ViewModel() {

    val albums: StateFlow<List<AlbumHistoryEntity>> = albumHistoryRepository
        .getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteAlbum(id: String) {
        viewModelScope.launch { albumHistoryRepository.deleteAlbum(id) }
    }
}
