package com.tunesort.app.classify

import com.tunesort.app.model.Song

object GenreClassifier {

    /**
     * Scores each genre by keyword hits in the lyrics, nudges the score using
     * the tempo bucket (fast songs get a small boost toward Party/Motivation,
     * slow songs toward Chill/Heartbreak/Worship), then picks the winner.
     * Falls back to a tempo-only genre if there are no lyrics at all.
     */
    fun classify(song: Song) {
        val lyrics = song.lyrics?.lowercase()
        if (lyrics.isNullOrBlank()) {
            song.genre = tempoOnlyGenre(song.tempoBucket)
            song.genreConfidence = 0.0
            return
        }

        val wordCount = max(1, lyrics.split(Regex("\\s+")).size)
        val scores = mutableMapOf<String, Double>()

        for ((genre, keywords) in GenreKeywords.GENRES) {
            var hits = 0
            for (kw in keywords) {
                hits += countOccurrences(lyrics, kw)
            }
            scores[genre] = hits * 100.0 / wordCount
        }

        applyTempoBias(scores, song.tempoBucket)

        val best = scores.maxByOrNull { it.value }
        if (best == null || best.value <= 0.0) {
            song.genre = tempoOnlyGenre(song.tempoBucket)
            song.genreConfidence = 0.0
        } else {
            song.genre = best.key
            song.genreConfidence = best.value
        }
    }

    private fun applyTempoBias(scores: MutableMap<String, Double>, bucket: String?) {
        when (bucket) {
            "fast" -> {
                scores["Party/Dance"] = (scores["Party/Dance"] ?: 0.0) + 1.5
                scores["Motivation/Hype"] = (scores["Motivation/Hype"] ?: 0.0) + 1.0
            }
            "slow" -> {
                scores["Chill/Lofi"] = (scores["Chill/Lofi"] ?: 0.0) + 1.5
                scores["Heartbreak/Sad"] = (scores["Heartbreak/Sad"] ?: 0.0) + 0.5
                scores["Worship"] = (scores["Worship"] ?: 0.0) + 0.5
            }
        }
    }

    private fun tempoOnlyGenre(bucket: String?): String = when (bucket) {
        "fast" -> "Party/Dance"
        "slow" -> "Chill/Lofi"
        "mid" -> "Uncategorized"
        else -> "Uncategorized"
    }

    private fun countOccurrences(text: String, phrase: String): Int {
        var count = 0
        var idx = text.indexOf(phrase)
        while (idx >= 0) {
            count++
            idx = text.indexOf(phrase, idx + phrase.length)
        }
        return count
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b
}
