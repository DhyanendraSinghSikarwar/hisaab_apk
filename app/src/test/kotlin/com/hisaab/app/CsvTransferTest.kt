package com.hisaab.app

import com.hisaab.app.csv.CsvTransfer
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.repo.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class CsvTransferTest {
    private val registry = ParserRegistry.default()
    private val now = 1_790_000_000_000L

    @Test
    fun `export then import restores every field that matters`() {
        val tx = registry.parse("Rs.450.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA swiggy@icici. UPI Ref 526812345678. Avl bal:INR 9,550.00", "VM-HDFCBK", now, Source.SMS)!!
        val out = StringWriter()
        CsvTransfer.export(listOf(tx.toEntity(1, now).copy(note = "lunch, with \"team\"")), out)
        val back = CsvTransfer.import(StringReader(out.toString())).rows.single()
        assertEquals(tx.amountMinor, back.amountMinor)
        assertEquals(tx.type, back.type)
        assertEquals(tx.accountLast4, back.accountLast4)
        assertEquals(tx.referenceNumber, back.referenceNumber)
        assertEquals(tx.merchant, back.merchant)
        assertEquals(tx.balanceMinor, back.balanceMinor)
        assertEquals(tx.transactionHash, back.transactionHash)
        assertEquals(tx.transactionTime, back.transactionTime)
    }

    @Test
    fun `columns are matched by header name and bad rows are counted`() {
        val csv = "type,amount,date,merchant\nDEBIT,120.50,2026-09-25T06:30:00Z,Cafe\nDEBIT,not-a-number,2026-09-25T06:30:00Z,Bad\n"
        val r = CsvTransfer.import(StringReader(csv))
        assertEquals(1, r.rows.size)
        assertEquals(12050L, r.rows[0].amountMinor)
        assertEquals(1, r.badLines)
    }

    @Test
    fun `a file without the required columns is refused`() {
        assertThrows(IllegalArgumentException::class.java) { CsvTransfer.import(StringReader("foo,bar\n1,2\n")) }
    }
}
