package com.tunesort.app.sort

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tunesort.app.model.Song

/**
 * Writes one M3U8 playlist per (genre, tempo bucket) combination that has at
 * least one song, into <root>/Playlists/ — e.g. "Worship-Praise - Slow.m3u8".
 * Poweramp auto-detects standalone playlist files sitting in a scanned folder,
 * so these show up as playlists on its next library scan. Genre alone or tempo
 * alone is already available via folder sort / manual browsing; combining both
 * is what a flat "all fast songs" or "all Worship songs" playlist can't do.
 */
object PlaylistWriter {

    fun writePlaylists(context: Context, rootTreeUri: Uri, sortedSongs: List<Pair<Song, String>>) {
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return
        val playlistsDir = root.findFile("Playlists") ?: root.createDirectory("Playlists") ?: return

        val groups = sortedSongs
            .filter { (song, _) -> song.tempoBucket != null && song.tempoBucket != "unknown" }
            .groupBy { (song, _) -> song.genre to song.tempoBucket }

        for ((key, entries) in groups) {
            val (genre, tempoBucket) = key
            if (genre == null || tempoBucket == null) continue
            val label = "${FileSorter.sanitizeFolderName(genre)} - ${tempoLabel(tempoBucket)}"
            writePlaylist(context, playlistsDir, label, entries)
        }
    }

    private fun writePlaylist(context: Context, playlistsDir: DocumentFile, label: String, entries: List<Pair<Song, String>>) {
        val fileName = "$label.m3u8"
        playlistsDir.findFile(fileName)?.delete() // regenerate fresh each Apply

        // Generic MIME so the SAF provider doesn't try to infer/append its own extension.
        val playlistFile = playlistsDir.createFile("application/octet-stream", fileName) ?: return

        val body = buildString {
            appendLine("#EXTM3U")
            for ((song, relativePath) in entries) {
                appendLine("#EXTINF:-1,${song.displayName}")
                // Playlists live in <root>/Playlists/; songs live in <root>/<relativePath>.
                appendLine("../$relativePath")
            }
        }

        try {
            context.contentResolver.openOutputStream(playlistFile.uri)?.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // Best-effort: a failed playlist write shouldn't affect the (already-moved) song files.
        }
    }

    private fun tempoLabel(bucket: String) = when (bucket) {
        "slow" -> "Slow"
        "mid" -> "Mid"
        "fast" -> "Fast"
        else -> bucket
    }
}
