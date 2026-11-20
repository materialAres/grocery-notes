package com.example.stickers.ui.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stickers.data.NoteRepository
import com.example.stickers.data.NoteWithBlocks
import com.example.stickers.widget.WidgetPinner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State and actions for the app's home screen and the Recently deleted screen. */
class BinViewModel(
    private val repo: NoteRepository,
    private val pinner: WidgetPinner,
) : ViewModel() {

    /** Bin notes, newest first; null until loaded. */
    val bin: StateFlow<List<NoteWithBlocks>?> =
        repo.observeBin().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val binCount: StateFlow<Int> =
        repo.observeBinCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The launcher can't pin widgets: tell the user to add one by hand. */
    var showPinUnsupported by mutableStateOf(false)
        private set

    init {
        // Keep the bin accurate even if the daily worker has been delayed (DESIGN.md §5.6).
        viewModelScope.launch { repo.purgeExpiredBin() }
    }

    fun addNoteToHomeScreen() {
        if (!pinner.pin()) showPinUnsupported = true
    }

    /** Pins a new widget; the note leaves the bin once the user confirms the launcher's dialog. */
    fun restore(noteId: Long) {
        if (!pinner.pin(restoreNoteId = noteId)) showPinUnsupported = true
    }

    fun dismissPinUnsupported() {
        showPinUnsupported = false
    }

    fun deleteForever(noteId: Long) {
        viewModelScope.launch { repo.deleteForever(noteId) }
    }

    fun emptyBin() {
        viewModelScope.launch { repo.emptyBin() }
    }
}
