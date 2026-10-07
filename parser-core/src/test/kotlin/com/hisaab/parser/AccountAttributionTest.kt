package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.extract.AccountExtractor
import com.hisaab.parser.extract.TypeClassifier
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

/**
 * User feedback: the user's mobile number (last four 6810) showed up as an extra account at Axis and SBI,
 * and money sent to someone else's account (payee 9876) showed up as income in the payee's "account".
 */
class AccountAttributionTest {
    private val registry = ParserRegistry.default()

    private fun parse(body: String, sender: String, source: Source = Source.SMS) =
        registry.parse(body, sender, Fixture.RECEIVED_AT, source)

    // ---- Bug 1: masked mobile numbers and UPI IDs are not accounts ----

    @ParameterizedTest
    @ValueSource(
        strings = [
            "INR 500.00 debited A/c no. XX1234 05-10-26 12:30:11 UPI/P2A/527812345678/ANIL KUMAR Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank",
            "INR 500.00 debited A/c no. XX1234 05-10-26 12:30:11 UPI/P2A/527812345678/ANIL KUMAR from 9876546810@axl Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank",
            "Payment of INR 500.00 from XXXXXX6810@ybl to ANIL KUMAR. Debited from XX1234 on 05-10-26. UPI Ref 527812345678 - Axis Bank",
            "Your registered mobile XXXXXX6810: INR 500.00 debited from XX1234 on 05-10-26. UPI Ref 527812345678 - Axis Bank",
            "INR 500.00 sent from 98XXXXX6810@axl via UPI. Debited from XX1234 on 05-10-26. UPI Ref 527812345678 - Axis Bank",
        ],
    )
    fun `axis - the account is 1234, never the mobile 6810`(body: String) {
        val tx = parse(body, "AX-AXISBK-S")
        assertNotNull(tx, body)
        assertEquals(TransactionType.DEBIT, tx!!.type)
        assertEquals("1234", tx.accountLast4)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Dear UPI user A/C X1234 debited by 500.0 on date 05Oct26 trf to ANIL KUMAR Refno 527812345678. If not u? call 1800111109. -SBI",
            "Dear UPI user, UPI ID 9xxxxxx810@sbi: A/C X1234 debited by 500.0 on date 05Oct26 trf to ANIL KUMAR Refno 527812345678. If not u? call 1800111109. -SBI",
            "Dear SBI User, registered mobile no. XXXXXX6810. Your a/c XX1234 debited by Rs.500 on 05Oct26 trf to ANIL KUMAR Ref No 527812345678 -SBI",
            "Rs.500 debited from XXXXXX6810@ybl on 05Oct26 to ANIL KUMAR, A/C X1234. Refno 527812345678 -SBI",
        ],
    )
    fun `sbi - the account is 1234, never the mobile 6810`(body: String) {
        val tx = parse(body, "VM-SBIUPI-S")
        assertNotNull(tx, body)
        assertEquals(TransactionType.DEBIT, tx!!.type)
        assertEquals("1234", tx.accountLast4)
    }

    @Test
    fun `a message whose only number is the user's UPI ID has no account`() {
        val tx = parse("Rs.500 debited via UPI from XXXXXX6810@ybl on 05Oct26 to ANIL KUMAR. Refno 527812345678 -SBI", "VM-SBIUPI-S")
        assertNotNull(tx)
        assertNull(tx!!.accountLast4)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "UPI ID 9xxxxxx810@sbi debited INR 500",
            "debited INR 500. VPA XXXXXX6810@ybl",
            "9876546810@axl debited INR 500",
            "registered mobile number XXXXXX6810 debited INR 500",
            "mob no XX6810 debited INR 500",
            "Mobile No.: XXXXXX6810 debited INR 500",
            "INR 500 debited. Call +91 XXXXXX6810",
        ],
    )
    fun `masked mobile numbers and UPI IDs are never accounts`(text: String) {
        assertNull(AccountExtractor.extract(text))
        assertNull(AccountExtractor.extract(text, TransactionType.DEBIT))
    }

    @Test
    fun `a labelled account after a phone mention is still found`() {
        assertEquals("1234", AccountExtractor.extract("registered mobile XXXXXX6810 A/c XX1234 debited INR 500", TransactionType.DEBIT)?.last4)
        assertEquals("1234", AccountExtractor.extract("mob no XX6810, INR 500 debited from XX1234", TransactionType.DEBIT)?.last4)
    }

    // ---- Bug 2: money sent to another person's account is a debit, and their account is not the user's ----

    @ParameterizedTest
    @ValueSource(
        strings = [
            "NEFT of Rs 5,000.00 credited to Beneficiary A/c XXXX9876 (ANIL KUMAR) from your A/c XX1234 on 05-10-26. UTR AXISN52026100512345",
            "Rs.5000 has been credited to beneficiary account XXXXXXXX9876 from your A/c XX1234 via IMPS. IMPS Ref No 627812345678",
            "INR 5,000.00 credited to ANIL KUMAR's account XX9876 from A/c no. XX1234 via IMPS. Ref 627812345678",
            "Your fund transfer of INR 5,000 from A/c XX1234 to A/c XX9876 is successful. IMPS Ref No 627812345678",
        ],
    )
    fun `money sent to a beneficiary is a debit from the user's account`(body: String) {
        val tx = parse(body, "AX-AXISBK-S")
        assertNotNull(tx, body)
        assertEquals(TransactionType.DEBIT, tx!!.type)
        assertEquals("1234", tx.accountLast4)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Rs.5000 has been credited to beneficiary account XXXXXXXX9876 via IMPS. IMPS Ref No 627812345678",
            "Your fund transfer of INR 5,000 to A/c XX9876 is successful. IMPS Ref No 627812345678",
            "IMPS: Rs 5000 sent to a/c 9876 on 05-10-26. Ref 627812345678",
            "NEFT of Rs 5,000.00 credited to Beneficiary A/c XXXX9876 (ANIL KUMAR) on 05-10-26. UTR SBIN52026100512345",
        ],
    )
    fun `when only the payee's account is written there is no account`(body: String) {
        val tx = parse(body, "VM-SBIINB-S")
        assertNotNull(tx, body)
        assertEquals(TransactionType.DEBIT, tx!!.type)
        assertNull(tx.accountLast4)
    }

    @Test
    fun `outgoing transfers are classified as debits`() {
        assertEquals(TransactionType.DEBIT, TypeClassifier.classify("NEFT of INR 5,000.00 credited to Beneficiary A/c XXXX9876"))
        assertEquals(TransactionType.DEBIT, TypeClassifier.classify("INR 5000 has been credited to beneficiary account XXXXXXXX9876 via IMPS"))
        assertEquals(TransactionType.DEBIT, TypeClassifier.classify("INR 5000 credited to ANIL KUMAR's account"))
        assertEquals(TransactionType.DEBIT, TypeClassifier.classify("Your fund transfer of INR 5,000 to A/c XX9876 is successful"))
        // Money coming in is still a credit.
        assertEquals(TransactionType.CREDIT, TypeClassifier.classify("INR 5000 credited to your A/c XX1234 from ANIL KUMAR"))
        assertEquals(TransactionType.CREDIT, TypeClassifier.classify("INR 5000 credited to A/c XX1234 by transfer from ANIL KUMAR"))
    }

    @Test
    fun `beneficiary numbers are skipped whatever the direction`() {
        assertNull(AccountExtractor.extract("credited to Beneficiary A/c XXXX9876"))
        assertNull(AccountExtractor.extract("credited to Beneficiary A/c XXXX9876", TransactionType.CREDIT))
        assertEquals("1234", AccountExtractor.extract("in favour of a/c XX9876 from your A/c XX1234", TransactionType.CREDIT)?.last4)
    }
}
