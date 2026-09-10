package com.tunesort.app.sort

import android.content.Context
import android.net.Uri
import com.tunesort.app.lyrics.Id3Tag
import com.tunesort.app.model.Song

/**
 * Optional, opt-in: rewrites the ID3v2 TCON (genre) frame in place for MP3 files,
 * preserving every other frame (title/artist/album art/etc.). Only MP3 is
 * supported out of the box — FLAC/M4A/OGG need a real tag library (see the
 * commented Kyant0/taglib dependency in app/build.gradle.kts) if you want
 * in-place tag writing for those too. Non-MP3 files are simply skipped here;
 * use FileSorter's folder-based sort for them instead.
 */
object TagWriter {

    fun writeGenreTag(context: Context, song: Song): Boolean {
        if (song.extension != "mp3") return false
        val genre = song.genre ?: return false
        return try {
            val original = context.contentResolver.openInputStream(song.uri)?.use { it.readBytes() }
                ?: return false
            val updated = Id3Tag.withGenre(original, genre)
            context.contentResolver.openOutputStream(song.uri, "wt")?.use { out ->
                out.write(updated)
            } ?: return false
            true
        } catch (e: Exception) {
            false
        }
    }
}
