package com.tunesort.app.model

import android.net.Uri

enum class SortStatus { PENDING, ANALYZING, DONE, FAILED, SKIPPED }

data class Song(
    val uri: Uri,          // SAF document uri for the audio file
    val displayName: String,
    val dirDocumentId: String, // parent directory document id, for move/rename
    val extension: String,
    var lyrics: String? = null,
    var lyricsSource: String? = null, // "sidecar-lrc" | "sidecar-txt" | "id3-uslt" | null
    var language: String? = null, // "te" (Telugu) | "en" (English) | null (undetected)
    var bpm: Double? = null,
    var tempoBucket: String? = null, // "slow" | "mid" | "fast"
    var genre: String? = null,
    var genreConfidence: Double = 0.0,
    var status: SortStatus = SortStatus.PENDING,
    var error: String? = null
)
