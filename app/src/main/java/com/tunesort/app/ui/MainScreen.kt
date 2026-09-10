package com.tunesort.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tunesort.app.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.onFolderChosen(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("TuneSort") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { pickFolder.launch(null) }) {
                    Text(if (state.treeUri == null) "Choose music folder" else "Change folder")
                }
                Spacer(Modifier.width(12.dp))
                if (state.treeUri != null) {
                    Text("Folder selected", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.writeId3Genre, onCheckedChange = { viewModel.toggleWriteId3(it) })
                Text("Also write genre into MP3 tags (advanced, keep off if unsure)")
            }

            if (state.isScanning) Text("Scanning folder…")
            if (state.isAnalyzing) {
                Text("Analyzing songs: ${state.progressDone}/${state.progressTotal}")
                LinearProgressIndicator(
                    progress = { if (state.progressTotal > 0) state.progressDone / state.progressTotal.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.isSorting) {
                Text("Sorting: ${state.progressDone}/${state.progressTotal}")
                LinearProgressIndicator(
                    progress = { if (state.progressTotal > 0) state.progressDone / state.progressTotal.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.weight(1f)) {
                items(state.songs) { song -> SongRow(song, onOverride = { viewModel.overrideGenre(song, it) }) }
            }

            if (state.songs.isNotEmpty() && !state.isAnalyzing && !state.isScanning) {
                Button(
                    onClick = { viewModel.applySort() },
                    enabled = !state.isSorting,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text("Apply: move songs into genre folders")
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, onOverride: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val genres = listOf(
        "Worship/Praise", "Hymns", "Gospel", "Contemporary Christian",
        "Christian Hip-Hop/Rap", "Christian Rock/Pop", "Christmas", "Uncategorized"
    )

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(song.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "BPM: ${song.bpm?.toInt() ?: "?"} (${song.tempoBucket ?: "?"})  ·  " +
                        "Lyrics: ${song.lyricsSource ?: "none found"} (${song.language ?: "?"})  ·  " +
                        "Status: ${song.status}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Box {
                AssistChip(onClick = { expanded = true }, label = { Text(song.genre ?: "Uncategorized") })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    genres.forEach { g ->
                        DropdownMenuItem(text = { Text(g) }, onClick = { onOverride(g); expanded = false })
                    }
                }
            }
        }
    }
}
