package com.tunesort.app.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tunesort.app.model.Song

private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "wav", "opus")

/**
 * Recursively walks a SAF tree (the folder the user grants access to, e.g. their
 * Music/Download folder) and returns every audio file found, paired with any
 * sidecar .lrc/.txt lyrics file sitting next to it (same base name).
 */
object LibraryScanner {

    fun scan(context: Context, treeUri: Uri): List<Song> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<Song>()
        walk(root, results)
        return results
    }

    private fun walk(dir: DocumentFile, out: MutableList<Song>) {
        val children = dir.listFiles()

        // Index sidecar text files in this directory by lowercase base name.
        val sidecars = children.filter {
            it.isFile && (it.name?.lowercase()?.endsWith(".lrc") == true ||
                    it.name?.lowercase()?.endsWith(".txt") == true)
        }.associateBy { baseName(it.name ?: "") }

        for (child in children) {
            if (child.isDirectory) {
                walk(child, out)
                continue
            }
            val name = child.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in AUDIO_EXT) continue

            val song = Song(
                uri = child.uri,
                displayName = name,
                dirDocumentId = dir.uri.toString(),
                extension = ext
            )

            sidecars[baseName(name)]?.let { sidecar ->
                song.lyricsSource = if (sidecar.name!!.lowercase().endsWith(".lrc")) "sidecar-lrc" else "sidecar-txt"
                // actual text content is loaded lazily by LyricsReader with this uri stashed via lyrics field placeholder
                song.lyrics = sidecar.uri.toString() // temporarily store uri; LyricsReader resolves it
            }

            out.add(song)
        }
    }

    private fun baseName(fileName: String) = fileName.substringBeforeLast('.').lowercase()
}
