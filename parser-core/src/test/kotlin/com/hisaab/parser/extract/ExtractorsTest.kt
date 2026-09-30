package com.hisaab.parser.extract

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.text.TextNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalTime

class ExtractorsTest {
    private fun n(s: String) = TextNormalizer.normalize(s)

    @Test
    fun `amount skips balance and limit figures`() {
        assertEquals(Money(25000, "INR"), AmountExtractor.extract(n("Avl Bal Rs 9,999.00. Rs.250.00 debited from A/c XX1234")))
        assertEquals(Money(12345650, "INR"), AmountExtractor.extract(n("Rs 1,23,456.50 credited. Avl bal:INR 2,00,000.00")))
        assertEquals(Money(1200, "USD"), AmountExtractor.extract("USD 12.00 spent on Card XX1234 at NETFLIX.COM"))
    }

    @Test
    fun `amount without a currency after debited by`() {
        assertEquals(Money(300000, "INR"), AmountExtractor.extract("A/C X1234 debited by 3,000.0 on date 05Sep26"))
    }

    @Test
    fun `money rejects zero and nonsense`() {
        assertNull(Money.parse("0.00"))
        assertNull(Money.parse(","))
        assertEquals(50, Money.parse("0.5")!!.minor)
    }

    @Test
    fun `balance and limit`() {
        assertEquals(1045000L, BalanceExtractor.balance(n("Avl bal:INR 10,450.00")))
        assertEquals(1209050L, BalanceExtractor.balance(n("AvlBal:Rs12090.50(2026:09:25)")))
        assertEquals(-50000L, BalanceExtractor.balance(n("Available Balance is INR -500.00")))
        assertEquals(4850100L, BalanceExtractor.limit(n("Avl Lmt INR 48,501.00")))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "A/c XX1234 debited|1234|ACCOUNT",
            "a/c no. XXXXXX1234 debited|1234|ACCOUNT",
            "Acct XX123 debited|123|ACCOUNT",
            "from A/C *1234 to|1234|ACCOUNT",
            "Card x5678 at|5678|CARD",
            "Credit Card ending 5678 for|5678|CARD",
            "Credit Card Account 4xxx xxxx xxxx 9012 on|9012|CARD",
            "your BOBCARD ending 9012 at|9012|CARD",
            "debited from HDFC Bank XX1234 on|1234|ACCOUNT",
        ],
    )
    fun `account last digits and kind`(text: String, last4: String, kind: AccountKind) {
        assertEquals(AccountRef(last4, kind), AccountExtractor.extract(text))
    }

    @Test
    fun `a masked mobile number is not an account`() {
        assertNull(AccountExtractor.extract("by A/c linked to mobile 9XXXXXX123"))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "UPI/P2M/526812345678/SWIGGY|526812345678",
            "Ref 526812345678.|526812345678",
            "(UPI 524412345678)|524412345678",
            "UPI:526612345678-ICICI Bank|526612345678",
            "IMPS Ref No. 626312345678|626312345678",
            "Your UPI transaction reference number is 526812345678.|526812345678",
            "UTR: CITIN52026090112345.|CITIN52026090112345",
            "Transaction number 626112345678.|626112345678",
            "RRN 525912345678|525912345678",
        ],
    )
    fun `reference numbers`(text: String, ref: String) {
        assertEquals(ref, ReferenceExtractor.extract(text))
    }

    @Test
    fun `words after a reference label are not references`() {
        assertNull(ReferenceExtractor.extract("UPI transaction refundable within 5 days"))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "on 25/09/26|2026-09-25|",
            "on 25-09-2026 at 10:15:32|2026-09-25|10:15:32",
            "on 25-Sep-26|2026-09-25|",
            "on date 25Sep26 trf|2026-09-25|",
            "on 01-SEP-26 for|2026-09-01|",
            "on 2026-09-24:18:22:10.|2026-09-24|18:22:10",
            "(2026:09:25 10:15:32)|2026-09-25|10:15:32",
            "on Sep 22, 2026 at 18:10:05|2026-09-22|18:10:05",
            "on 22/09/2026 at 06:40 PM|2026-09-22|18:40",
            "on 12SEP26 14:35|2026-09-12|14:35",
        ],
    )
    fun `date and time formats`(text: String, date: String, time: String?) {
        val parts = DateTimeExtractor.extract(text)
        assertEquals(LocalDate.parse(date), parts.date)
        assertEquals(time?.let(LocalTime::parse), parts.time)
    }

    @Test
    fun `an impossible date is ignored`() {
        assertNull(DateTimeExtractor.extract("on 31/02/26").date)
    }

    @Test
    fun `type follows the first money verb`() {
        assertEquals(TransactionType.DEBIT, TypeClassifier.classify("INR 240 debited; SWIGGY credited"))
        assertEquals(TransactionType.CREDIT, TypeClassifier.classify("INR 500 credited to A/c from VPA x@y. Debit card"))
        assertEquals(TransactionType.TRANSFER, TypeClassifier.classify("Payment of INR 5,000.00 received towards your Credit Card XX1"))
        assertNull(TypeClassifier.classify("Your balance is INR 500"))
    }

    @Test
    fun `channel detection`() {
        assertEquals(Channel.ATM, ChannelDetector.detect("withdrawn at ATM"))
        assertEquals(Channel.UPI, ChannelDetector.detect("to VPA a@okaxis"))
        assertEquals(Channel.AUTO_DEBIT, ChannelDetector.detect("Info: ACH D- ZERODHA"))
        assertEquals(Channel.NEFT, ChannelDetector.detect("by NEFT from ACME"))
        assertEquals(Channel.CARD, ChannelDetector.detect("spent on Card XX1 at AMAZON"))
    }

    @Test
    fun `merchant patterns`() {
        assertEquals(RawMerchant("SWIGGY", null), MerchantExtractor.extract("UPI/P2M/526812345678/SWIGGY. Not you?", TransactionType.DEBIT))
        assertEquals(RawMerchant("SWIGGY", "swiggy@icici"), MerchantExtractor.extract("debited to VPA swiggy@icici SWIGGY on 25-09-26", TransactionType.DEBIT))
        assertEquals(RawMerchant("AMAZON PAY INDIA", null), MerchantExtractor.extract("spent on Card x1 at AMAZON PAY INDIA on 2026-09-24", TransactionType.DEBIT))
        assertEquals(RawMerchant("JOHN DOE", null), MerchantExtractor.extract("credited with INR 5 by NEFT from JOHN DOE. UPI", TransactionType.CREDIT))
        assertEquals(RawMerchant(null, "rahul.sharma@oksbi"), MerchantExtractor.extract("Sent INR 5 to rahul.sharma@oksbi on 08-09-26", TransactionType.DEBIT))
    }
}
