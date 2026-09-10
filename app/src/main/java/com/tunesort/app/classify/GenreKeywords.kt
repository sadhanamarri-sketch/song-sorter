package com.tunesort.app.classify

/**
 * Tune this freely — it's the whole "model". Each genre gets a weighted word list;
 * the classifier scores lyrics by counted hits per 100 words. Add/remove genres
 * and words to match your own library.
 *
 * Tuned for a Christian music library, English and Telugu. Each genre's list
 * mixes English, native Telugu script, and common Romanized-Telugu terms
 * together rather than keeping a separate per-language dictionary: Song.language
 * (see LanguageDetector) only catches native Telugu script reliably, so a
 * Romanized-Telugu .lrc file or a blank ID3 language tag would otherwise fall
 * through to English-only matching. Scoring against the union handles that,
 * plus lyrics that code-mix Telugu and English within the same song, without
 * needing a language guess to be right. The Telugu terms are a best-effort
 * starting point (not a native speaker's word list) — edit freely to match
 * your own library's actual vocabulary.
 */
object GenreKeywords {

    val GENRES: Map<String, List<String>> = mapOf(
        "Worship/Praise" to listOf(
            "worship", "praise", "holy", "holy holy holy", "hallelujah", "exalt",
            "magnify", "adore", "adoration", "bow down", "lift high", "glorify",
            "how great is our god", "how great thou art", "highest praise", "hosanna",
            "ఆరాధన", "స్తుతి", "మహిమ", "పరిశుద్ధ", "హల్లెలూయ", "వందనం",
            "aaradhana", "aradhana", "sthuti", "stuti", "mahima", "parishudda", "vandanamu"
        ),
        "Hymns" to listOf(
            "thee", "thou", "thy", "thine", "hath", "doth", "wondrous",
            "amazing grace", "rock of ages", "blessed assurance", "great is thy faithfulness",
            "come thou fount", "old rugged cross", "fairest lord", "it is well", "just as i am",
            "కీర్తన", "కీర్తనలు", "గీతం", "భజన",
            "keertana", "kirtana", "keertanalu", "geetam", "bhajana"
        ),
        "Gospel" to listOf(
            "testimony", "shout", "revival", "anointing", "anointed", "victory is mine",
            "he's able", "trouble don't last always", "i'm free", "no weapon", "choir",
            "tambourine", "made it over", "glory glory", "sing hallelujah",
            "సాక్ష్యం", "విజయం", "పునరుజ్జీవనం", "సువార్త",
            "sakshyam", "vijayam", "suvarta", "punarujjeevanam"
        ),
        "Contemporary Christian" to listOf(
            "faithful", "faithfulness", "surrender", "healer", "redeemer", "reckless love",
            "way maker", "good good father", "broken", "chains", "freedom",
            "who you say i am", "still i rise", "oceans", "graves into gardens",
            "నమ్మకం", "కృప", "రక్షకుడు", "స్వేచ్ఛ", "సంకెళ్లు",
            "nammakam", "krupa", "rakshakudu", "swecha", "sankellu"
        ),
        "Christian Hip-Hop/Rap" to listOf(
            "flow", "bars", "mic", "beat drop", "hustle", "kingdom come", "grind",
            "spit truth", "verse", "cypher", "trap", "808", "real talk", "testimony in the booth",
            "ర్యాప్", "బార్స్", "ఫ్లో", "బీట్"
        ),
        "Christian Rock/Pop" to listOf(
            "fire", "roar", "unstoppable", "awake", "rise up", "revival fire",
            "electric", "loud", "break every chain", "shake the ground", "on fire", "unleashed",
            "అగ్ని", "మేల్కొను", "మేల్కొండి", "లేచి",
            "agni", "melkondi", "lechi"
        ),
        "Christmas" to listOf(
            "christmas", "manger", "bethlehem", "nativity", "shepherds", "wise men",
            "silent night", "noel", "emmanuel", "star of wonder", "away in a manger",
            "joy to the world", "o holy night", "born is the king",
            "క్రిస్మస్", "బేత్లెహేము", "శిశువు", "నక్షత్రం", "కాపరులు", "దేవదూత",
            "krismas", "bethlehemu", "shishuvu", "nakshatram", "kaparulu", "devadoota"
        )
    )
}
