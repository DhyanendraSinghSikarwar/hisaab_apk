package com.hisaab.shared

import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.repo.mergedWith
import com.hisaab.shared.repo.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeTest {
    private val registry = ParserRegistry.default()
    private val now = 1_790_000_000_000L

    @Test
    fun `merge fills the merchant from email and keeps the balance from sms`() {
        val sms = registry.parse("INR 999.00 debited from HDFC Bank XX1234 on 05-SEP-26. Avl bal:INR 23,410.00", "VM-HDFCBK", now, Source.SMS)!!
        val mail = registry.parse(
            "Dear Customer, an amount of Rs. 999.00 has been debited from your account No. XXXX1234 on account of ACH D- TP ACH ZERODHA-1234567 on 05-09-2026.",
            "alerts@hdfcbank.net", now, Source.EMAIL,
        )!!
        val merged = sms.toEntity(accountId = 1, now = now).mergedWith(mail)
        assertEquals("Zerodha", merged.merchant)
        assertEquals(2341000L, merged.balanceMinor)
        assertEquals(TransactionType.INVESTMENT, merged.type)
    }

    private val parser = com.hisaab.parser.FreeTextParser()
    private fun app(body: String, at: Long = now) = parser.readNotification("PhonePe", listOf(body), at).tx!!

    @Test
    fun `a bank sms gives the exact paise a payment app dropped`() {
        val sms = registry.parse("Rs.144.93 spent on your SBI Credit Card ending 5678 at JIOHOTSTAR on 07/10/26.", "VM-SBICRD", now, Source.SMS)!!
        val merged = app("Paid ₹144 to Jiohotstar").toEntity(null, now).mergedWith(sms)
        assertEquals(14493L, merged.amountMinor)
        // the reverse never rounds the exact figure
        assertEquals(14493L, sms.toEntity(1, now).mergedWith(app("Paid ₹144 to Jiohotstar")).amountMinor)
    }

    @Test
    fun `a real payee name replaces a upi id`() {
        val sms = registry.parse("Rs.450.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA ravi.k@ybl. UPI Ref 526812345678", "VM-HDFCBK", now, Source.SMS)!!
        val named = app("Paid ₹450 to Ravi Kumar")
        val stored = sms.toEntity(1, now).copy(merchant = "ravi.k@ybl")
        assertEquals("Ravi Kumar", stored.mergedWith(named).merchant)
    }

    @Test
    fun `a merged payment never sits after the balance its message states`() {
        val sms = registry.parse("Rs.450.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA swiggy@icici. UPI Ref 526812345678. Avl bal:INR 9,550.00", "VM-HDFCBK", now, Source.SMS)!!
        val stored = app("Paid ₹450 to Swiggy", at = sms.transactionTime + 20_000).toEntity(null, now)
        assertEquals(true, stored.mergedWith(sms).timestamp <= sms.transactionTime)
    }

    @Test
    fun `merge never overwrites a field that is already set`() {
        val a = registry.parse("Rs.450.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA swiggy@icici. UPI Ref 526812345678", "VM-HDFCBK", now, Source.SMS)!!
        val b = registry.parse("Rs.450.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA zomato@icici. UPI Ref 526812345678", "VM-HDFCBK", now, Source.SMS)!!
        assertEquals("Swiggy", a.toEntity(null, now).mergedWith(b).merchant)
    }
}
