package com.tunesort.app.classify

import com.tunesort.app.model.Song

object GenreClassifier {

    /**
     * Scores each genre by keyword hits in the lyrics, nudges the score using
     * the tempo bucket (fast songs get a small boost toward Gospel/Hip-Hop/Rock,
     * slow songs toward Hymns/Worship), then picks the winner.
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
                scores["Gospel"] = (scores["Gospel"] ?: 0.0) + 1.0
                scores["Christian Hip-Hop/Rap"] = (scores["Christian Hip-Hop/Rap"] ?: 0.0) + 1.0
                scores["Christian Rock/Pop"] = (scores["Christian Rock/Pop"] ?: 0.0) + 1.5
            }
            "slow" -> {
                scores["Hymns"] = (scores["Hymns"] ?: 0.0) + 1.5
                scores["Worship/Praise"] = (scores["Worship/Praise"] ?: 0.0) + 0.5
            }
        }
    }

    private fun tempoOnlyGenre(bucket: String?): String = when (bucket) {
        "fast" -> "Christian Rock/Pop"
        "mid" -> "Contemporary Christian"
        "slow" -> "Worship/Praise"
        else -> "Uncategorized" // BPM detection itself failed — no signal at all
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
