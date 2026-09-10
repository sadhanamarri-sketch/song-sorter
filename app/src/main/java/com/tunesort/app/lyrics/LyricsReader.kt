package com.tunesort.app.lyrics

import android.content.Context
import android.net.Uri
import com.tunesort.app.model.Song

object LyricsReader {

    /** Populates song.lyrics with plain text (LRC timing tags stripped). */
    fun load(context: Context, song: Song) {
        // LibraryScanner stashed the sidecar file's own uri string in song.lyrics if one was found.
        val sidecarUriString = song.lyrics
        if (sidecarUriString != null) {
            val text = readUriText(context, Uri.parse(sidecarUriString))
            song.lyrics = if (song.lyricsSource == "sidecar-lrc") stripLrcTiming(text) else text
            if (!song.lyrics.isNullOrBlank()) return
        }

        if (song.extension == "mp3") {
            val embedded = readEmbeddedUslt(context, song.uri)
            if (!embedded.isNullOrBlank()) {
                song.lyrics = embedded
                song.lyricsSource = "id3-uslt"
                return
            }
        }

        song.lyrics = null
        song.lyricsSource = null
    }

    private fun readUriText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    private fun stripLrcTiming(text: String?): String? =
        text?.lines()?.joinToString("\n") { it.replace(Regex("""\[\d{1,3}:\d{2}(\.\d{1,3})?]"""), "").trim() }
            ?.trim()

    private fun readEmbeddedUslt(context: Context, uri: Uri): String? = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val tag = Id3Tag.parse(bytes) ?: return null
        val uslt = tag.frames.firstOrNull { it.id == "USLT" || it.id == "ULT" } ?: return null
        val idLen = if (tag.version == 2) 3 else 4
        Id3Tag.usltText(uslt, idLen).ifBlank { null }
    } catch (e: Exception) {
        null
    }
}
