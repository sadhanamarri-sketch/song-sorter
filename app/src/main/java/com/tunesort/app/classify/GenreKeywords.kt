package com.tunesort.app.classify

/**
 * Tune this freely — it's the whole "model". Each genre gets a weighted word list;
 * the classifier scores lyrics by counted hits per 100 words. Add/remove genres
 * and words to match your own library.
 *
 * Tuned for a Christian music library: since faith vocabulary (god/jesus/lord/
 * praise) shows up across almost every track, each list leans on words that are
 * more distinctive to that specific style rather than generic faith words alone
 * (e.g. archaic pronouns for Hymns, call-and-response phrasing for Gospel).
 */
object GenreKeywords {

    val GENRES: Map<String, List<String>> = mapOf(
        "Worship/Praise" to listOf(
            "worship", "praise", "holy", "holy holy holy", "hallelujah", "exalt",
            "magnify", "adore", "adoration", "bow down", "lift high", "glorify",
            "how great is our god", "how great thou art", "highest praise", "hosanna"
        ),
        "Hymns" to listOf(
            "thee", "thou", "thy", "thine", "hath", "doth", "wondrous",
            "amazing grace", "rock of ages", "blessed assurance", "great is thy faithfulness",
            "come thou fount", "old rugged cross", "fairest lord", "it is well", "just as i am"
        ),
        "Gospel" to listOf(
            "testimony", "shout", "revival", "anointing", "anointed", "victory is mine",
            "he's able", "trouble don't last always", "i'm free", "no weapon", "choir",
            "tambourine", "made it over", "glory glory", "sing hallelujah"
        ),
        "Contemporary Christian" to listOf(
            "faithful", "faithfulness", "surrender", "healer", "redeemer", "reckless love",
            "way maker", "good good father", "broken", "chains", "freedom",
            "who you say i am", "still i rise", "oceans", "graves into gardens"
        ),
        "Christian Hip-Hop/Rap" to listOf(
            "flow", "bars", "mic", "beat drop", "hustle", "kingdom come", "grind",
            "spit truth", "verse", "cypher", "trap", "808", "real talk", "testimony in the booth"
        ),
        "Christian Rock/Pop" to listOf(
            "fire", "roar", "unstoppable", "awake", "rise up", "revival fire",
            "electric", "loud", "break every chain", "shake the ground", "on fire", "unleashed"
        ),
        "Christmas" to listOf(
            "christmas", "manger", "bethlehem", "nativity", "shepherds", "wise men",
            "silent night", "noel", "emmanuel", "star of wonder", "away in a manger",
            "joy to the world", "o holy night", "born is the king"
        )
    )
}
