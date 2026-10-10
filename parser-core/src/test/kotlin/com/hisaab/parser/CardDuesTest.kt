package com.hisaab.parser

import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.parser.statement.CardDueParser
import com.hisaab.parser.statement.StatementKind
import com.hisaab.parser.statement.StatementParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Card dues as issuers print them in statements, and as they announce them by SMS or email. */
class CardDuesTest {
    private val parser = StatementParser()

    private fun check(
        text: String, sender: String, bank: String, last4: String,
        total: Long, min: Long?, due: LocalDate, stmt: LocalDate, limit: Long? = null,
    ) {
        val r = parser.parse(text, sender, Fixture.RECEIVED_AT)
        assertEquals(StatementKind.CREDIT_CARD, r.statementKind, bank)
        assertEquals(bank, r.bankName)
        assertEquals(last4, r.last4, bank)
        assertEquals(total, r.summary.totalDueMinor, "$bank total")
        assertEquals(min, r.summary.minDueMinor, "$bank min")
        assertEquals(due, r.summary.dueDate, "$bank due date")
        assertEquals(stmt, r.summary.statementDate, "$bank statement date")
        if (limit != null) assertEquals(limit, r.summary.creditLimitMinor, "$bank limit")
    }

    @Test
    fun `HDFC header row then values row with rupee glyph and cash limit column`() = check(
        """
        HDFC Bank Credit Cards Statement
        Name: A SHARMA
        Card No: 4000 00XX XXXX 5521
        Statement Date:20/09/2026
        Payment Due Date Total Dues Minimum Amount Due
        21/10/2026 C12,345.00 C620.00
        Credit Limit Available Credit Limit Available Cash Limit
        C2,00,000.00 C1,75,000.00 C40,000.00
        """.trimIndent(),
        "HDFC Bank <emailstatements.cards@hdfcbank.net>", "HDFC Bank", "5521", 1234500, 62000, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 9, 20), 20000000,
    )

    @Test
    fun `HDFC available limit is not taken for the credit limit`() {
        val r = parser.parse(
            "HDFC Bank Credit Card Statement\nCard No: 4000 00XX XXXX 5521\nCredit Limit Available Credit Limit Available Cash Limit\nC2,00,000.00 C1,75,000.00 C40,000.00",
            "HDFC Bank <x@hdfcbank.net>", Fixture.RECEIVED_AT,
        )
        assertEquals(20000000L, r.summary.creditLimitMinor)
        assertEquals(17500000L, r.summary.availableMinor)
    }

    @Test
    fun `ICICI one label per line with the value on the next line and a trailing Dr`() = check(
        """
        ICICI Bank Credit Card Statement
        Credit Card No: 4375 XXXX XXXX 8842
        STATEMENT DATE
        20-Sep-2026
        PAYMENT DUE DATE
        08-Oct-2026
        Total Amount due
        Rs.24,560.75 Dr
        Minimum Amount due
        Rs.1,230.00
        Credit Limit
        Rs.2,00,000.00
        Available Credit Limit
        Rs.1,75,439.25
        """.trimIndent(),
        "ICICI Bank <credit_cards@icicibank.com>", "ICICI Bank", "8842", 2456075, 123000, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 9, 20), 20000000,
    )

    @Test
    fun `SBI Card label block then value block`() = check(
        """
        SBI Card
        Credit Card Statement
        SBI Card ending XXXX XXXX XXXX 7310
        Statement Date
        Payment Due Date
        Total Amount Due
        Minimum Amount Due
        12 Oct 2026
        02 Nov 2026
        ₹5,430.00
        ₹270.00
        """.trimIndent(),
        "SBI Card <Statements@sbicard.com>", "SBI", "7310", 543000, 27000, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 10, 12),
    )

    @Test
    fun `Axis total payment due with Dr and statement period`() = check(
        """
        Axis Bank Credit Card Statement
        Card No: 4147XXXXXXXX3190
        Statement Period: 13/08/2026 To 12/09/2026
        Statement Generation Date 12/09/2026
        Payment Summary
        Total Payment Due Minimum Payment Due Payment Due Date
        Rs. 8,100.00 Dr Rs. 405.00 Dr 02/10/2026
        Credit Limit Available Credit Limit
        Rs. 1,50,000.00 Rs. 1,41,900.00
        """.trimIndent(),
        "Axis Bank <cc.statements@axisbank.com>", "Axis Bank", "3190", 810000, 40500, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 9, 12), 15000000,
    )

    @Test
    fun `Axis statement period gives the statement date when none is printed`() {
        val r = parser.parse(
            "Axis Bank Credit Card Statement\nCard No: 4147XXXXXXXX3190\nStatement Period: 13/08/2026 To 12/09/2026\nTotal Payment Due Rs. 100.00",
            "Axis Bank <cc@axisbank.com>", Fixture.RECEIVED_AT,
        )
        assertEquals(LocalDate.of(2026, 9, 12), r.summary.statementDate)
    }

    @Test
    fun `Kotak labels with colons on one line each`() = check(
        """
        Kotak Mahindra Bank Credit Card Statement
        Card Number: 5241 80XX XXXX 7788
        Statement Date: 15-Sep-2026
        Total Amount Due: Rs. 18,250.40
        Minimum Amount Due: Rs. 912.52
        Payment Due Date: 05-Oct-2026
        Credit Limit: Rs. 3,00,000.00
        """.trimIndent(),
        "Kotak <statements@kotak.com>", "Kotak Mahindra Bank", "7788", 1825040, 91252, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 9, 15), 30000000,
    )

    @Test
    fun `IDFC FIRST rupee sign and dd-mm-yyyy`() = check(
        """
        IDFC FIRST Bank Credit Card Statement
        Card Number XXXX XXXX XXXX 3321
        Statement Date 11/09/2026
        Payment Due Date 29/09/2026
        Total Amount Due ₹ 12,480.00
        Minimum Amount Due ₹ 624.00
        Credit Limit ₹ 1,00,000.00
        """.trimIndent(),
        "IDFC FIRST Bank <cards@idfcfirstbank.com>", "IDFC FIRST Bank", "3321", 1248000, 62400, LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 11), 10000000,
    )

    @Test
    fun `Yes Bank due date without a year and INR amounts`() = check(
        """
        YES BANK Credit Card Statement
        Card Number: 4580 XXXX XXXX 9145
        Statement Date 10 Sep 2026
        Payment Due Date 30 September
        Total Amount Due INR 6,750.00
        Minimum Amount Due INR 340.00
        Credit Limit INR 75,000.00
        """.trimIndent(),
        "YES BANK <estatement@yesbank.in>", "Yes Bank", "9145", 675000, 34000, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 10), 7500000,
    )

    @Test
    fun `AU total payment due`() = check(
        """
        AU Small Finance Bank Credit Card Statement
        Card No: XXXX XXXX XXXX 6110
        Statement Date : 18/09/2026
        Total Payment Due : Rs 9,999.00
        Minimum Payment Due : Rs 500.00
        Payment Due Date : 08/10/2026
        Credit Limit : Rs 1,20,000.00
        """.trimIndent(),
        "AU Bank <cards@aubank.in>", "AU Small Finance Bank", "6110", 999900, 50000, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 9, 18), 12000000,
    )

    @Test
    fun `RBL two header rows each followed by a values row`() = check(
        """
        RBL Bank Credit Card Statement
        Card No. 5236 XXXX XXXX 1450
        Statement Date Payment Due Date
        14-09-2026 04-10-2026
        Total Amount Due Minimum Amount Due
        ₹ 31,200.00 ₹ 1,560.00
        Credit Limit Available Credit Limit
        ₹ 2,50,000.00 ₹ 2,18,800.00
        """.trimIndent(),
        "RBL Bank <cards@rblbank.com>", "RBL Bank", "1450", 3120000, 156000, LocalDate.of(2026, 10, 4), LocalDate.of(2026, 9, 14), 25000000,
    )

    @Test
    fun `IndusInd amounts with a trailing Dr`() = check(
        """
        IndusInd Bank Credit Card Statement
        Card Number XXXX XXXX XXXX 8802
        Statement Date 05/09/2026
        Total Amount Due 4,321.50 Dr
        Minimum Amount Due 216.00 Dr
        Payment Due Date 25/09/2026
        Credit Limit 90,000.00
        """.trimIndent(),
        "IndusInd Bank <cards@indusind.com>", "IndusInd Bank", "8802", 432150, 21600, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 5), 9000000,
    )

    @Test
    fun `American Express new balance and minimum payment due`() = check(
        """
        American Express Credit Card Statement
        Card Number: XXXX-XXXXXX-1007
        Statement Closing Date 10 Sep 2026
        New Balance Rs. 45,210.00
        Minimum Payment Due Rs. 2,260.00
        Payment Due Date 05 Oct 2026
        """.trimIndent(),
        "American Express <AmericanExpress@welcome.americanexpress.com>", "American Express", "1007", 4521000, 226000, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 9, 10),
    )

    @Test
    fun `HSBC and Standard Chartered`() {
        check(
            """
            HSBC Credit Card Statement
            Card Number 4546 XXXX XXXX 3099
            Statement Date 22 Sep 2026
            Payment Due Date 12 Oct 2026
            Total Amount Due Rs 15,000.00
            Minimum Amount Due Rs 750.00
            Credit Limit Rs 2,50,000.00
            """.trimIndent(),
            "HSBC <hsbc@mail.hsbc.co.in>", "HSBC", "3099", 1500000, 75000, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 9, 22), 25000000,
        )
        check(
            """
            Standard Chartered Bank Credit Card Statement
            Card No XXXX XXXX XXXX 6677
            Statement Date 21-09-2026
            Total Amount Due 7,800.00
            Minimum Amount Due 390.00
            Payment Due Date 11-10-2026
            """.trimIndent(),
            "Standard Chartered <statements@sc.com>", "Standard Chartered", "6677", 780000, 39000, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 9, 21),
        )
    }

    @Test
    fun `Federal OneCard short labels`() = check(
        """
        OneCard Federal Bank Credit Card Statement
        Card Number XXXX XXXX XXXX 2024
        Statement Date 01/09/2026
        Total Due ₹ 22,000.00
        Minimum Due ₹ 1,100.00
        Due Date 21/09/2026
        """.trimIndent(),
        "OneCard <statements@getonecard.app>", "Federal Bank", "2024", 2200000, 110000, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 1),
    )

    @Test
    fun `two digit year and a credit balance`() {
        val r = parser.parse(
            "Axis Bank Credit Card Statement\nCard No XXXX XXXX XXXX 1111\nStatement Date 12/09/26\nTotal Amount Due 1,200.00 Cr\nPayment Due Date 02/10/26",
            "Axis Bank <cc@axisbank.com>", Fixture.RECEIVED_AT,
        )
        assertEquals(0L, r.summary.totalDueMinor)
        assertEquals(LocalDate.of(2026, 10, 2), r.summary.dueDate)
        assertEquals(LocalDate.of(2026, 9, 12), r.summary.statementDate)
    }

    // SMS and email messages.

    private val at = Fixture.RECEIVED_AT
    private val registry = ParserRegistry.default()

    @Test
    fun `HDFC statement SMS`() {
        val d = registry.cardDue(
            "Your HDFC Bank Credit Card XX5521 statement is generated. Total Amount Due Rs.12,345.00, Minimum Amount Due Rs.620.00, Payment Due Date 21-OCT-2026",
            "VM-HDFCBK", at,
        )
        assertNotNull(d)
        assertEquals("HDFC Bank", d!!.bank)
        assertEquals("5521", d.last4)
        assertEquals(1234500L, d.totalDueMinor)
        assertEquals(62000L, d.minDueMinor)
        assertEquals(LocalDate.of(2026, 10, 21).toEpochDay(), d.dueEpochDay)
    }

    @Test
    fun `SBI Card statement SMS with two digit years`() {
        val d = registry.cardDue(
            "SBI Card ending 7310: Statement dated 12/10/26. Total amt due Rs 5,430, min amt due Rs 270, pay by 02/11/26", "VM-SBICRD", at,
        )
        assertNotNull(d)
        assertEquals("SBI", d!!.bank)
        assertEquals("7310", d.last4)
        assertEquals(543000L, d.totalDueMinor)
        assertEquals(27000L, d.minDueMinor)
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), d.dueEpochDay)
        assertEquals(LocalDate.of(2026, 10, 12).toEpochDay(), d.statementEpochDay)
    }

    @Test
    fun `ICICI statement SMS`() {
        val d = CardDueParser.parse(
            "ICICI Bank Credit Card XX8842 statement dated 20-SEP-2026 is generated. Total Amount due: INR 24,560.75. Minimum due: INR 1,230.00. Please pay by 08-OCT-2026.",
            "ICICI Bank", at,
        )
        assertNotNull(d)
        assertEquals(2456075L, d!!.totalDueMinor)
        assertEquals(123000L, d.minDueMinor)
        assertEquals(LocalDate.of(2026, 10, 8).toEpochDay(), d.dueEpochDay)
        assertEquals(LocalDate.of(2026, 9, 20).toEpochDay(), d.statementEpochDay)
    }

    @Test
    fun `Axis statement SMS`() {
        val d = registry.cardDue(
            "Axis Bank Credit Card No. XX3190: Total payment due INR 8,100.00. Minimum payment due INR 405.00. Due date 02-10-2026.", "AX-AXISBK", at,
        )
        assertNotNull(d)
        assertEquals("Axis Bank", d!!.bank)
        assertEquals("3190", d.last4)
        assertEquals(810000L, d.totalDueMinor)
        assertEquals(40500L, d.minDueMinor)
        assertEquals(LocalDate.of(2026, 10, 2).toEpochDay(), d.dueEpochDay)
    }

    @Test
    fun `payment acknowledgement`() {
        val d = registry.cardDue(
            "Payment of Rs.12,345.00 has been received towards your HDFC Bank Credit Card XX5521 on 05-OCT-2026. Thank you.", "VM-HDFCBK", at,
        )
        assertNotNull(d)
        assertEquals(1234500L, d!!.paymentMinor)
        assertNull(d.totalDueMinor)
    }

    @Test
    fun `card spend alerts and failed payments are not dues`() {
        assertNull(registry.cardDue("Spent Rs.276.53 On HDFC Bank Card 5521 At SWIGGY On 2026-09-24:10:12:11. Avl Lmt Rs.1,23,456.00", "VM-HDFCBK", at))
        assertNull(registry.cardDue("Payment of Rs.5,000.00 towards your HDFC Bank Credit Card XX5521 failed.", "VM-HDFCBK", at))
        assertNull(registry.cardDue("Your A/c XX1234 balance is Rs 1,000.00. Minimum balance Rs 500.", "VM-HDFCBK", at))
    }
}
