package com.hisaab.parser.hash

import com.hisaab.parser.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TransactionHasherTest {
    private val day = LocalDate.of(2026, 9, 25)

    @Test
    fun `hash is a stable lowercase sha-256 hex of the canonical key`() {
        val h = TransactionHasher.hash(25000, TransactionType.DEBIT, "1234", day, "526812345678")
        assertEquals(64, h.length)
        assertEquals(h, TransactionHasher.hash(25000, TransactionType.DEBIT, "1234", day, "526812345678"))
        // SHA-256("250.00|OUT|1234|2026-09-25|526812345678")
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("250.00|OUT|1234|2026-09-25|526812345678".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, h)
    }

    @Test
    fun `debit and investment hash alike because both move money out`() {
        assertEquals(
            TransactionHasher.hash(99900, TransactionType.DEBIT, "1234", day, null),
            TransactionHasher.hash(99900, TransactionType.INVESTMENT, "1234", day, null),
        )
    }

    @Test
    fun `every key field changes the hash`() {
        val base = TransactionHasher.hash(25000, TransactionType.DEBIT, "1234", day, "R1")
        assertNotEquals(base, TransactionHasher.hash(25001, TransactionType.DEBIT, "1234", day, "R1"))
        assertNotEquals(base, TransactionHasher.hash(25000, TransactionType.CREDIT, "1234", day, "R1"))
        assertNotEquals(base, TransactionHasher.hash(25000, TransactionType.DEBIT, "9999", day, "R1"))
        assertNotEquals(base, TransactionHasher.hash(25000, TransactionType.DEBIT, "1234", day.plusDays(1), "R1"))
        assertNotEquals(base, TransactionHasher.hash(25000, TransactionType.DEBIT, "1234", day, null))
    }

    @Test
    fun `amounts are formatted with two decimals`() {
        assertEquals(
            TransactionHasher.hash(5, TransactionType.DEBIT, null, day, null),
            java.security.MessageDigest.getInstance("SHA-256").digest("0.05|OUT||2026-09-25|".toByteArray())
                .joinToString("") { "%02x".format(it) },
        )
    }
}
