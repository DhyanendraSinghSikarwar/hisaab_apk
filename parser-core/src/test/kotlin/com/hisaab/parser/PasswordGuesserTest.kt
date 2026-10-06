package com.hisaab.parser

import com.hisaab.parser.statement.Identity
import com.hisaab.parser.statement.PasswordGuesser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PasswordGuesserTest {
    private val me = Identity("Asha Kumari Verma", LocalDate.of(1994, 3, 7), "abcpv1234k", "+91 98765 43210")
    private val guesses = PasswordGuesser.candidates(me, listOf("2779"))
    private val withAlts = me.copy(altName = "Aasha Sharma", altPhone = "91234 56780")
    private val altGuesses = PasswordGuesser.candidates(withAlts, listOf("2779"))

    @Test
    fun `common statement formats are guessed`() {
        for (p in listOf("ABCPV1234K", "ASHA0703", "asha0703", "07031994", "070319942779", "0703", "9876543210", "VERM0703")) {
            assertTrue(p in guesses, p)
        }
    }

    @Test
    fun `the PAN comes first, for CAS statements`() {
        assertTrue(guesses.first() == "ABCPV1234K")
    }

    @Test
    fun `the list stays small enough to try on every PDF`() {
        assertTrue(guesses.size < 120, "${guesses.size}")
    }

    @Test
    fun `the old four-field constructor still works and alternates default to none`() {
        assertEquals(null, me.altName)
        assertEquals(null, me.altPhone)
    }

    @Test
    fun `alternate name patterns are guessed`() {
        for (p in listOf("AASH0703", "aash070394", "SHAR0703", "Shar07031994", "SHAR2779", "SHAR3210")) {
            assertTrue(p in altGuesses, p)
        }
    }

    @Test
    fun `alternate mobile patterns are guessed`() {
        for (p in listOf("9123456780", "6780", "56780", "ASHA6780", "VERM6780", "SHAR6780", "67800703", "07036780")) {
            assertTrue(p in altGuesses, p)
        }
    }

    @Test
    fun `the primary name and mobile are tried before the alternates`() {
        assertTrue(altGuesses.indexOf("ASHA0703") < altGuesses.indexOf("SHAR0703"))
        assertTrue(altGuesses.indexOf("9876543210") < altGuesses.indexOf("9123456780"))
        assertEquals("ABCPV1234K", altGuesses.first())
    }

    @Test
    fun `adding alternates keeps every primary guess`() {
        assertTrue(altGuesses.containsAll(guesses))
    }

    @Test
    fun `alternates and many cards still stay bounded`() {
        val cards = (1000..1011).map { it.toString() }
        val many = PasswordGuesser.candidates(withAlts, cards)
        assertTrue(many.size < 250, "${many.size}")
        assertTrue(altGuesses.size < 250, "${altGuesses.size}")
        assertTrue("ASHA0703" in many)
    }

    @Test
    fun `a blank identity yields nothing`() {
        assertTrue(PasswordGuesser.candidates(Identity()).isEmpty())
    }
}
