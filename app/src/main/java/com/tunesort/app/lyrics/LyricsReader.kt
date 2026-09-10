package com.tunesort.app.lyrics

import android.content.Context
import android.net.Uri
import com.tunesort.app.model.Song

object LyricsReader {

    private data class EmbeddedLyrics(val text: String, val languageCode: String?)

    /** Populates song.lyrics with plain text (LRC timing tags stripped) and detects song.language. */
    fun load(context: Context, song: Song) {
        // LibraryScanner stashed the sidecar file's own uri string in song.lyrics if one was found.
        val sidecarUriString = song.lyrics
        if (sidecarUriString != null) {
            val text = readUriText(context, Uri.parse(sidecarUriString))
            song.lyrics = if (song.lyricsSource == "sidecar-lrc") stripLrcTiming(text) else text
            if (!song.lyrics.isNullOrBlank()) {
                song.language = LanguageDetector.detect(song.lyrics)
                return
            }
        }

        if (song.extension == "mp3") {
            val embedded = readEmbeddedUslt(context, song.uri)
            if (embedded != null) {
                song.lyrics = embedded.text
                song.lyricsSource = "id3-uslt"
                song.language = LanguageDetector.detect(embedded.text, embedded.languageCode)
                return
            }
        }

        song.lyrics = null
        song.lyricsSource = null
        song.language = null
    }

    private fun readUriText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    private fun stripLrcTiming(text: String?): String? =
        text?.lines()?.joinToString("\n") { it.replace(Regex("""\[\d{1,3}:\d{2}(\.\d{1,3})?]"""), "").trim() }
            ?.trim()

    private fun readEmbeddedUslt(context: Context, uri: Uri): EmbeddedLyrics? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val tag = Id3Tag.parse(bytes) ?: return null
            val uslt = tag.frames.firstOrNull { it.id == "USLT" || it.id == "ULT" } ?: return null
            val idLen = if (tag.version == 2) 3 else 4
            val text = Id3Tag.usltText(uslt, idLen).ifBlank { null } ?: return null

            // Prefer USLT's own language code (tied directly to this lyrics block);
            // fall back to the track-level TLAN frame if USLT's is blank/unknown.
            val usltLang = Id3Tag.usltLanguage(uslt, idLen)
            val tlan = tag.frames.firstOrNull { it.id == "TLAN" || it.id == "TLA" }
            val tlanLang = tlan?.let { Id3Tag.frameText(it, idLen) }
            val languageCode = listOf(usltLang, tlanLang)
                .firstOrNull { !it.isNullOrBlank() && !it.equals("xxx", ignoreCase = true) && !it.equals("und", ignoreCase = true) }

            EmbeddedLyrics(text, languageCode)
        } catch (e: Exception) {
            null
        }
    }
}
