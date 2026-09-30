package com.hisaab.parser.hash

import com.hisaab.parser.model.TransactionType
import java.security.MessageDigest
import java.time.LocalDate

/**
 * transactionHash = SHA-256(amount | direction | accountLast4 | yyyy-MM-dd | refNo-or-empty).
 *
 * The SMS and the email for one transaction produce the same hash when they agree on those fields.
 * When one carries the reference and the other does not, the hashes differ, and the fuzzy matcher
 * in the dedup layer catches the pair instead.
 */
object TransactionHasher {
    private val HEX = "0123456789abcdef".toCharArray()

    fun hash(amountMinor: Long, type: TransactionType, accountLast4: String?, date: LocalDate, referenceNumber: String?): String {
        val amount = "%d.%02d".format(amountMinor / 100, amountMinor % 100)
        val key = buildString {
            append(amount).append('|')
            append(type.direction).append('|')
            append(accountLast4.orEmpty()).append('|')
            append(date.toString()).append('|')
            append(referenceNumber.orEmpty())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val out = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val v = digest[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }
}
