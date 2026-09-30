package com.hisaab.parser

import com.hisaab.parser.corpus.AllCorpora
import com.hisaab.parser.corpus.AxisCorpus
import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.corpus.IciciCorpus
import com.hisaab.parser.corpus.KotakCorpus
import com.hisaab.parser.corpus.SbiCorpus
import com.hisaab.parser.extract.Money
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.registry.ParserRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** The accuracy targets, measured across every bank's corpus through the registry, as the app will call it. */
class CorpusAccuracyTest {
    private val registry = ParserRegistry.default()

    private fun parse(s: com.hisaab.parser.corpus.Sample): ParsedTransaction? =
        registry.parse(s.body, s.sender, Fixture.RECEIVED_AT, s.source)

    @Test
    fun `field extraction accuracy is at least 98 percent`() {
        var checked = 0
        var correct = 0
        val misses = mutableListOf<String>()
        fun field(label: String, ok: Boolean) { checked++; if (ok) correct++ else misses += label }

        for (s in AllCorpora.samples) {
            val e = s.expect ?: continue
            val tx = parse(s)
            val tag = s.label
            field("$tag amount", tx?.amountMinor == Money.parse(e.amount)!!.minor)
            field("$tag type", tx?.type == e.type)
            field("$tag account", tx != null && tx.accountLast4 == e.last4)
            field("$tag ref", tx != null && tx.referenceNumber == e.ref)
            e.merchant?.let { field("$tag merchant", tx?.merchant == it) }
            e.balance?.let { field("$tag balance", tx?.balanceMinor == Money.parse(it)!!.minor) }
            e.date?.let { d -> field("$tag date", tx != null && Instant.ofEpochMilli(tx.transactionTime).atZone(Fixture.IST).toLocalDate().toString() == d) }
        }
        val accuracy = correct.toDouble() / checked
        println("Field accuracy: $correct/$checked = ${"%.2f".format(accuracy * 100)}%")
        assertTrue(accuracy >= 0.98, "accuracy $accuracy, misses: $misses")
    }

    @Test
    fun `no negative sample is ever parsed`() {
        val leaked = AllCorpora.samples.filter { it.expect == null }.filter { parse(it) != null }.map { it.label }
        assertEquals(emptyList<String>(), leaked)
    }

    @Test
    fun `the sms and the email for the same transaction produce the same hash`() {
        // Pairs that describe one transaction: same amount, account, reference and day.
        val pairs = listOf(
            IciciCorpus.samples[0] to IciciCorpus.samples.first { it.body.contains("UPI/526612345678/SWIGGY") },
            AxisCorpus.samples[0] to AxisCorpus.samples.first { it.source.name == "EMAIL" && it.body.contains("526812345678") },
            SbiCorpus.samples[0] to SbiCorpus.samples.first { it.source.name == "EMAIL" && it.body.contains("526812345678") },
            KotakCorpus.samples[0] to KotakCorpus.samples.first { it.source.name == "EMAIL" && it.body.contains("526812345678") },
        )
        for ((sms, mail) in pairs) {
            val a = parse(sms)!!
            val b = parse(mail)!!
            assertEquals(a.transactionHash, b.transactionHash, "${sms.label} vs ${mail.label}")
        }
    }
}
