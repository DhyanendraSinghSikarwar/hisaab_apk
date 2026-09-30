package com.hisaab.email.imap

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A one-time 6-digit code mailed to the address being connected. Typing it back proves the address
 * is the user's and that the app can both send and receive with the login given.
 *
 * Kept in memory only, as a hash; it expires after [ttlMillis] and allows [maxAttempts] wrong tries.
 */
class VerificationCode(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val ttlMillis: Long = 10 * 60 * 1000L,
    private val maxAttempts: Int = 5,
) {
    enum class Result { OK, WRONG, EXPIRED, TOO_MANY_ATTEMPTS, NONE }

    private var hash: ByteArray? = null
    private var email: String? = null
    private var expiresAt = 0L
    private var attempts = 0

    /** Makes a fresh code for [forEmail], replacing any earlier one, and returns it for sending. */
    @Synchronized
    fun issue(forEmail: String): String {
        val code = (random.nextInt(900_000) + 100_000).toString()
        hash = digest(forEmail, code)
        email = forEmail
        expiresAt = clock() + ttlMillis
        attempts = 0
        return code
    }

    @Synchronized
    fun check(forEmail: String, typed: String): Result {
        val expected = hash ?: return Result.NONE
        if (email != forEmail) return Result.NONE
        if (clock() > expiresAt) return Result.EXPIRED
        if (attempts >= maxAttempts) return Result.TOO_MANY_ATTEMPTS
        attempts++
        val ok = MessageDigest.isEqual(expected, digest(forEmail, typed.filter { it.isDigit() }))
        if (ok) clear()
        return if (ok) Result.OK else Result.WRONG
    }

    @Synchronized
    fun clear() {
        hash = null
        email = null
        attempts = 0
    }

    private fun digest(email: String, code: String) = MessageDigest.getInstance("SHA-256").digest("$email|$code".toByteArray())
}
