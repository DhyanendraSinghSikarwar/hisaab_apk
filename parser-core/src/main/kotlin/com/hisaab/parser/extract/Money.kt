package com.hisaab.parser.extract

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(val minor: Long, val currency: String) {
    companion object {
        private const val MAX_MINOR = 1_000_000_000_000L // ₹10,000 crore, a sanity ceiling

        /** "1,23,456.5" -> 12345650. Returns null for anything that is not a sane positive amount. */
        fun parse(number: String, currency: String = "INR"): Money? {
            val cleaned = number.replace(",", "").trimEnd('.')
            if (cleaned.isEmpty()) return null
            val minor = try {
                BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
            } catch (_: NumberFormatException) {
                return null
            } catch (_: ArithmeticException) {
                return null
            }
            return if (minor <= 0 || minor > MAX_MINOR) null else Money(minor, currency.uppercase())
        }

        fun parseSigned(number: String): Long? {
            val negative = number.startsWith("-")
            val m = parse(number.removePrefix("-")) ?: return if (number.trim('-', '0', '.', ',').isEmpty()) 0L else null
            return if (negative) -m.minor else m.minor
        }
    }
}
