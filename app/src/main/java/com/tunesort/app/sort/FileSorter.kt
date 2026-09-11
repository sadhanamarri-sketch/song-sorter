package com.tunesort.app.sort

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tunesort.app.model.Song

/**
 * Default, safe sorting action: moves each song into <root>/<Genre>/<filename>.
 * Works for every audio format and never touches file contents, so it can't
 * corrupt a song. Poweramp will see the new folder structure on its next scan.
 */
object FileSorter {

    /** Returns the song's new path relative to the root folder (e.g. "Worship-Praise/song.mp3"), or null on failure. */
    fun moveIntoGenreFolder(context: Context, rootTreeUri: Uri, song: Song): String? {
        val genre = song.genre ?: return null
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return null
        val folderName = sanitizeFolderName(genre)
        val genreDir = root.findFile(folderName) ?: root.createDirectory(folderName) ?: return null

        val source = DocumentFile.fromSingleUri(context, song.uri) ?: return null
        // Avoid clobbering an existing file with the same name.
        var targetName = source.name ?: return null
        if (genreDir.findFile(targetName) != null) {
            val dot = targetName.lastIndexOf('.')
            val stem = if (dot > 0) targetName.substring(0, dot) else targetName
            val ext = if (dot > 0) targetName.substring(dot) else ""
            targetName = "$stem-${System.currentTimeMillis() % 100000}$ext"
        }

        return try {
            val newFile = genreDir.createFile(mimeFor(song.extension), targetName) ?: return null
            context.contentResolver.openInputStream(song.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            source.delete()
            "$folderName/${newFile.name ?: targetName}"
        } catch (e: Exception) {
            null
        }
    }

    /** Genre names like "Worship/Praise" contain '/', which a SAF folder display name can't safely hold. */
    fun sanitizeFolderName(name: String): String = name.replace('/', '-')

    private fun mimeFor(ext: String) = when (ext) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a", "aac" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "audio/*"
    }
}
