package com.hisaab.parser.extract

/** Button and link captions from payment-app notifications and emails, which are never a merchant name. */
object GenericPhrases {
    private val PHRASES = setOf(
        "view details", "view detail", "view", "details", "view receipt", "view transaction", "view statement", "view more",
        "click here", "click", "know more", "check balance", "tap to view", "tap here", "download", "pay now",
        "learn more", "track order", "explore", "open app", "open", "see details", "see more", "show details",
        "view order", "check now", "get started", "shop now", "register now", "apply now", "unsubscribe",
    )

    /** True for a UI phrase, or one made of nothing else. */
    fun isGeneric(name: String?): Boolean {
        val k = name?.lowercase()?.replace(Regex("[^a-z ]"), " ")?.trim()?.replace(Regex("""\s+"""), " ") ?: return false
        return k in PHRASES
    }

    /** [name] unless it is a UI phrase. */
    fun clean(name: String?): String? = name?.takeUnless(::isGeneric)
}
