package com.healthy.app.scan

/**
 * Typical caffeine densities, used only to pre-fill the dialog when Open Food
 * Facts has no figure (spec 11.4 asks the user; this gives them a start).
 *
 * These are deliberately kept apart from the drink catalog in spec 7. That
 * catalog is a list of things the user logs with one tap and its numbers are
 * the spec's. This is a set of guesses about a product the app has never seen,
 * shown as a guess, and overwritten by whatever the user reads off the tin.
 */
object CaffeineReference {

    data class Guess(
        val mgPer100Ml: Int,
        /** What the guess was based on, so the user can judge it. */
        val basis: String,
    )

    /**
     * Keyword to density. Ordered: the first match wins, so the more specific
     * families are listed before the general ones.
     */
    private val BY_KEYWORD: List<Pair<List<String>, Guess>> = listOf(
        listOf("espresso", "ristretto") to Guess(210, "a typical espresso"),
        listOf("cold brew", "coldbrew") to Guess(67, "a typical cold brew"),
        listOf("filter coffee", "americano", "instant coffee", "coffee", "καφε", "espresso")
            to Guess(40, "a typical coffee"),
        listOf("energy", "hell", "monster", "red bull", "redbull", "rockstar", "burn", "tiger")
            to Guess(32, "a typical energy drink"),
        listOf("green tea", "matcha") to Guess(12, "a typical green tea"),
        listOf("tea", "chai", "τσαι") to Guess(20, "a typical black tea"),
        listOf("pepsi", "cola", "coke", "coca") to Guess(11, "a typical cola"),
        listOf("chocolate", "cocoa") to Guess(20, "typical dark chocolate"),
    )

    /**
     * A density for a product name, or null when nothing looks close enough.
     * A wrong guess is worse than none: the user would accept it without
     * checking.
     */
    fun forName(name: String?): Guess? {
        val haystack = name?.lowercase()?.trim() ?: return null
        if (haystack.isEmpty()) return null
        return BY_KEYWORD.firstOrNull { (keywords, _) ->
            keywords.any { it in haystack }
        }?.second
    }
}
