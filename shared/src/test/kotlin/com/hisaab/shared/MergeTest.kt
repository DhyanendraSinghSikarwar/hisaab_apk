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

    @Test
    fun `merge never overwrites a field that is already set`() {
        val a = registry.parse("Rs.450.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA swiggy@icici. UPI Ref 526812345678", "VM-HDFCBK", now, Source.SMS)!!
        val b = registry.parse("Rs.450.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA zomato@icici. UPI Ref 526812345678", "VM-HDFCBK", now, Source.SMS)!!
        assertEquals("Swiggy", a.toEntity(null, now).mergedWith(b).merchant)
    }
}
