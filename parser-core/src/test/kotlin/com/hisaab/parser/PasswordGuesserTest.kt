package com.hisaab.parser

import com.hisaab.parser.statement.Identity
import com.hisaab.parser.statement.PasswordGuesser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PasswordGuesserTest {
    private val me = Identity("Asha Kumari Verma", LocalDate.of(1994, 3, 7), "abcpv1234k", "+91 98765 43210")
    private val guesses = PasswordGuesser.candidates(me, listOf("2779"))

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
}
