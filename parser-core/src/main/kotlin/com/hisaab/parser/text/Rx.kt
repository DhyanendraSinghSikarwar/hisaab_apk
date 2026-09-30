package com.hisaab.parser.text

import java.util.regex.Pattern

/**
 * Case-insensitive regex, ASCII folding only.
 *
 * Kotlin's RegexOption.IGNORE_CASE also switches on UNICODE_CASE, which slows every match.
 * Normalized bank text is ASCII apart from the rupee sign, and the rupee sign has no case.
 */
fun rx(pattern: String): Regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).toRegex()

/**
 * A regex behind a cheap substring pre-check. Most patterns cannot match most messages, and
 * `String.contains` on the lowercased text is far cheaper than a regex scan that fails.
 *
 * Every string the regex can match must contain at least one of [needles] (lowercase),
 * otherwise the guard would hide real matches.
 */
class Guarded(pattern: String, private vararg val needles: String) {
    val regex: Regex = rx(pattern)

    fun allows(lower: String): Boolean {
        if (needles.isEmpty()) return true
        for (n in needles) if (lower.contains(n)) return true
        return false
    }

    fun containsMatchIn(text: String, lower: String): Boolean = allows(lower) && regex.containsMatchIn(text)
    fun find(text: String, lower: String, start: Int = 0): MatchResult? = if (allows(lower)) regex.find(text, start) else null
    fun findAll(text: String, lower: String): Sequence<MatchResult> = if (allows(lower)) regex.findAll(text) else emptySequence()
}
