package com.tunesort.app.classify

/**
 * Tune this freely — it's the whole "model". Each genre gets a weighted word list;
 * the classifier scores lyrics by counted hits per 100 words. Add/remove genres
 * and words to match your own library.
 */
object GenreKeywords {

    val GENRES: Map<String, List<String>> = mapOf(
        "Worship" to listOf(
            "praise", "worship", "holy", "hallelujah", "jesus", "lord", "god",
            "savior", "grace", "glory", "amen", "heaven", "faith", "prayer", "bless"
        ),
        "Love/Romance" to listOf(
            "love", "heart", "kiss", "baby", "darling", "forever", "hold me",
            "miss you", "romance", "sweetheart", "hand in hand", "in love"
        ),
        "Heartbreak/Sad" to listOf(
            "cry", "tears", "alone", "lonely", "goodbye", "broken", "pain",
            "hurt", "sorry", "miss you", "empty", "sad", "regret", "gone"
        ),
        "Party/Dance" to listOf(
            "party", "dance", "club", "night", "drink", "turn up", "beat",
            "dj", "move your body", "celebrate", "let's go", "loud"
        ),
        "Motivation/Hype" to listOf(
            "rise", "fight", "champion", "power", "strong", "never give up",
            "hustle", "grind", "victory", "warrior", "unstoppable", "dream big"
        ),
        "Chill/Lofi" to listOf(
            "calm", "slow down", "breathe", "rain", "quiet", "peace", "gentle",
            "drift", "float", "soft", "easy", "mellow"
        )
    )
}
