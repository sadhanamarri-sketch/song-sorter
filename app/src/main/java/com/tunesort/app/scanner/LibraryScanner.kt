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

    /**
     * @param excludeDirNames top-level folder names to skip entirely (e.g. the app's own
     * genre folders from a previous sort), so re-scanning doesn't re-process already-sorted
     * songs. Only checked at the root — a deeper folder that happens to share a name is
     * never skipped.
     */
    fun scan(context: Context, treeUri: Uri, excludeDirNames: Set<String> = emptySet()): List<Song> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<Song>()
        walk(root, results, excludeDirNames, isTopLevel = true)
        return results
    }

    private fun walk(dir: DocumentFile, out: MutableList<Song>, excludeDirNames: Set<String>, isTopLevel: Boolean) {
        val children = dir.listFiles()

        // Index sidecar text files in this directory by lowercase base name.
        val sidecars = children.filter {
            it.isFile && (it.name?.lowercase()?.endsWith(".lrc") == true ||
                    it.name?.lowercase()?.endsWith(".txt") == true)
        }.associateBy { baseName(it.name ?: "") }

        for (child in children) {
            if (child.isDirectory) {
                if (isTopLevel && child.name in excludeDirNames) continue
                walk(child, out, excludeDirNames, isTopLevel = false)
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
