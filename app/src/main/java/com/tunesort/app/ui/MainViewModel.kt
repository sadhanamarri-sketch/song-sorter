package com.tunesort.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tunesort.app.classify.GenreClassifier
import com.tunesort.app.lyrics.LyricsReader
import com.tunesort.app.model.Song
import com.tunesort.app.model.SortStatus
import com.tunesort.app.scanner.LibraryScanner
import com.tunesort.app.sort.FileSorter
import com.tunesort.app.sort.PlaylistWriter
import com.tunesort.app.sort.TagWriter
import com.tunesort.app.tempo.BpmAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val treeUri: Uri? = null,
    val songs: List<Song> = emptyList(),
    val isScanning: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isSorting: Boolean = false,
    val writeId3Genre: Boolean = false, // off by default: folder-sort is the safe default
    val progressDone: Int = 0,
    val progressTotal: Int = 0
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onFolderChosen(uri: Uri) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        _state.update { it.copy(treeUri = uri) }
        scanAndAnalyze(uri)
    }

    fun toggleWriteId3(enabled: Boolean) {
        _state.update { it.copy(writeId3Genre = enabled) }
    }

    private fun scanAndAnalyze(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true) }
            val songs = withContext(Dispatchers.IO) { LibraryScanner.scan(context, uri) }
            _state.update { it.copy(isScanning = false, songs = songs, progressTotal = songs.size, progressDone = 0) }

            _state.update { it.copy(isAnalyzing = true) }
            withContext(Dispatchers.Default) {
                for (song in songs) {
                    song.status = SortStatus.ANALYZING
                    try {
                        withContext(Dispatchers.IO) { LyricsReader.load(context, song) }
                        song.bpm = withContext(Dispatchers.IO) { BpmAnalyzer.estimateBpm(context, song.uri) }
                        song.tempoBucket = BpmAnalyzer.bucket(song.bpm)
                        GenreClassifier.classify(song)
                        song.status = SortStatus.PENDING // ready, awaiting user's Apply
                    } catch (e: Exception) {
                        song.status = SortStatus.FAILED
                        song.error = e.message
                    }
                    _state.update { it.copy(progressDone = it.progressDone + 1) }
                }
            }
            _state.update { it.copy(isAnalyzing = false) }
        }
    }

    /** Overrides the genre a song was auto-assigned, e.g. from a dropdown in the UI. */
    fun overrideGenre(song: Song, genre: String) {
        song.genre = genre
        _state.update { it.copy(songs = it.songs.toList()) } // trigger recompose
    }

    fun applySort() {
        val context = getApplication<Application>()
        val treeUri = _state.value.treeUri ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSorting = true, progressDone = 0, progressTotal = it.songs.size) }
            withContext(Dispatchers.IO) {
                val sortedSongs = mutableListOf<Pair<Song, String>>()
                for (song in _state.value.songs) {
                    if (song.genre == null) { _state.update { it.copy(progressDone = it.progressDone + 1) }; continue }
                    if (_state.value.writeId3Genre) {
                        TagWriter.writeGenreTag(context, song) // best-effort, ignore failure
                    }
                    val relativePath = FileSorter.moveIntoGenreFolder(context, treeUri, song)
                    song.status = if (relativePath != null) SortStatus.DONE else SortStatus.FAILED
                    if (relativePath != null) sortedSongs.add(song to relativePath)
                    _state.update { it.copy(progressDone = it.progressDone + 1) }
                }
                // Poweramp playlists combining genre + tempo, e.g. "Worship-Praise - Fast.m3u8".
                if (sortedSongs.isNotEmpty()) {
                    PlaylistWriter.writePlaylists(context, treeUri, sortedSongs)
                }
            }
            _state.update { it.copy(isSorting = false, songs = it.songs.toList()) }
        }
    }
}
