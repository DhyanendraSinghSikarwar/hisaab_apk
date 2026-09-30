package com.hisaab.parser.merchant

import com.hisaab.parser.extract.RawMerchant
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MerchantNormalizerTest {
    private fun debit(name: String?, vpa: String? = null) =
        MerchantNormalizer.normalize(RawMerchant(name, vpa), TransactionType.DEBIT, Channel.UPI, "")

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "ACH D- TP ACH ZERODHA-1234567|Zerodha|INVESTMENT",
            "NEFT-CITIN52026090112345-ACME CORP|Acme Corp|OTHER",
            "UPI-525512345678-AMIT KUMAR|Amit Kumar|OTHER",
            "ACH/ICCL MUTUAL FUND/12345678|ICCL|INVESTMENT",
            "AMAZON PAY INDIA PVT LTD|Amazon|SHOPPING",
            "BUNDL TECHNOLOGIES|Swiggy|FOOD",
            "SWIGGY INSTAMART|Swiggy Instamart|GROCERIES",
            "UBER INDIA|Uber|TRANSPORT",
            "NETFLIX COM|Netflix|ENTERTAINMENT",
            "IRCTC|IRCTC|TRAVEL",
            "SHARMA MEDICALS|Sharma Medicals|HEALTH",
            "COCA COLA STORE|Coca Cola Store|OTHER",
        ],
    )
    fun `names are cleaned and categorised`(raw: String, name: String, category: Category) {
        val m = debit(raw)
        assertEquals(name, m.name)
        assertEquals(category, m.category)
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "swiggy.upi@axisbank|Swiggy",
            "zomato-order@ptybl|Zomato",
            "rahul.sharma@oksbi|Rahul Sharma",
            "paytm.s1abc2@paytm|S1abc",
        ],
    )
    fun `names come from the UPI id when the message has none`(vpa: String, name: String) {
        assertEquals(name, debit(null, vpa).name)
    }

    @Test
    fun `a personal vpa of digits gives no name`() {
        assertEquals(null, debit(null, "9876543210@ybl").name)
    }

    @Test
    fun `atm withdrawals are cash`() {
        val m = MerchantNormalizer.normalize(RawMerchant("HDFC0001234", null), TransactionType.DEBIT, Channel.ATM, "")
        assertEquals("ATM", m.name)
        assertEquals(Category.CASH, m.category)
    }

    @Test
    fun `credits are income, salary, or refund by wording`() {
        fun cat(text: String) = MerchantNormalizer.normalize(RawMerchant("ACME", null), TransactionType.CREDIT, Channel.NEFT, text).category
        assertEquals(Category.SALARY, cat("NEFT credit SALARY SEP 2026"))
        assertEquals(Category.REFUND, cat("Refund of INR 499 credited"))
        assertEquals(Category.INCOME, cat("INR 500 credited"))
    }
}
