package com.hisaab.parser

import com.hisaab.parser.statement.BankIdentity
import com.hisaab.parser.statement.BankKeys
import com.hisaab.parser.statement.Identity
import com.hisaab.parser.statement.PasswordGuesser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BankIdentityGuessTest {
    private val profile = Identity("Asha Kumari Verma", LocalDate.of(1994, 3, 7), "abcpv1234k", "+91 98765 43210")
    private val hdfc = BankIdentity(customerId = "12345678", accountNumber = "50100234567891", ifsc = "HDFC0001234")

    @Test
    fun `the customer id comes first for an HDFC-like identity`() {
        val g = PasswordGuesser.forBanks(profile, listOf(hdfc), listOf("2779"))
        assertEquals("12345678", g.first())
    }

    @Test
    fun `account number variants and the IFSC are guessed`() {
        val g = PasswordGuesser.forBanks(profile, listOf(hdfc))
        for (p in listOf("50100234567891", "7891", "67891", "567891", "34567891", "HDFC0001234", "hdfc0001234")) assertTrue(p in g, p)
        assertTrue(g.indexOf("34567891") < g.indexOf("ABCPV1234K"))
    }

    @Test
    fun `an account name beats the profile name`() {
        val b = hdfc.copy(names = listOf("Ria Mehta"))
        val g = PasswordGuesser.forBanks(profile, listOf(b))
        assertTrue(g.indexOf("RIA0703") in 0 until g.indexOf("ASHA0703"), g.take(40).toString())
        assertTrue("ASHA0703" in g)
    }

    @Test
    fun `an account mobile beats the profile mobile`() {
        val g = PasswordGuesser.forBanks(profile, listOf(hdfc.copy(phones = listOf("91234 56780"))))
        assertTrue(g.indexOf("9123456780") < g.indexOf("9876543210"))
    }

    @Test
    fun `without combos only exact forms and the profile are tried`() {
        val b = hdfc.copy(names = listOf("Ria Mehta"))
        val g = PasswordGuesser.forBanks(profile, listOf(b), combos = false)
        assertTrue("RIA0703" !in g)
        assertEquals("12345678", g.first())
    }

    @Test
    fun `the list is bounded and has no duplicates`() {
        val many = (1..12).map { hdfc.copy(customerId = "9000000$it", accountNumber = "5010000000$it$it", names = listOf("Name$it Surname")) }
        val g = PasswordGuesser.forBanks(profile.copy(altName = "Aasha Sharma", altPhone = "91234 56780"), many, (1000..1011).map { it.toString() })
        assertTrue(g.size <= PasswordGuesser.MAX_WITH_BANKS, "${g.size}")
        assertEquals(g.size, g.toSet().size)
    }

    @Test
    fun `the old identity API is unchanged`() {
        assertEquals(PasswordGuesser.candidates(profile, listOf("2779")), PasswordGuesser.forBanks(profile, emptyList(), listOf("2779")))
        assertTrue(PasswordGuesser.candidates(Identity()).isEmpty())
        assertEquals(null, profile.email)
    }

    @Test
    fun `bank keys match names and senders`() {
        assertEquals("hdfc", BankKeys.of("HDFC Bank"))
        assertEquals("hdfc", BankKeys.of("HDFC <emailstatements.cards@hdfcbank.net>"))
        assertEquals("sbi", BankKeys.of("SBI Card"))
        assertEquals(null, BankKeys.of("Some Fund"))
    }
}
