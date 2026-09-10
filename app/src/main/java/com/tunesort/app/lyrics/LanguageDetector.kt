package com.tunesort.app.lyrics

/**
 * Rough language tagging so the classifier can score against the right keyword
 * list. Telugu script in the lyrics text itself outranks any ID3 tag, since
 * taggers frequently leave the language field blank or wrong; the ID3 language
 * code (USLT's own 3-letter code, or TLAN) is the fallback for lyrics written
 * in Latin-script transliteration, where script detection can't tell.
 */
object LanguageDetector {
    private val TELUGU_SCRIPT = Regex("[ఀ-౿]")

    fun detect(lyrics: String?, id3LanguageCode: String? = null): String? {
        if (lyrics != null && TELUGU_SCRIPT.containsMatchIn(lyrics)) return "te"
        return when (id3LanguageCode?.trim()?.lowercase()) {
            "tel", "te" -> "te"
            "eng", "en" -> "en"
            else -> null
        }
    }
}
