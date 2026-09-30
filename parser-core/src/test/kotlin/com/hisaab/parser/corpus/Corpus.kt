package com.hisaab.parser.corpus

import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What a sample must parse to. Amount, type, account and reference are always checked, so a null
 * account or reference means "must be absent". Merchant, balance, date and kind are checked only when given.
 */
data class Expect(
    val amount: String,
    val type: TransactionType,
    val last4: String?,
    val ref: String? = null,
    val merchant: String? = null,
    val balance: String? = null,
    val date: String? = null,
    val kind: AccountKind? = null,
    val currency: String = "INR",
)

/** One anonymized message. [expect] null means the parser must reject it. */
data class Sample(val source: Source, val sender: String, val body: String, val expect: Expect?) {
    val label: String get() = "${source.name.lowercase()} ${if (expect == null) "reject" else expect.type.name.lowercase()}: " +
        body.replace('\n', ' ').take(70)
}

object Fixture {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    /** Every sample was "received" at 25 Sep 2026, 12:00 IST. */
    val RECEIVED_AT: Long = LocalDateTime.of(2026, 9, 25, 12, 0).atZone(IST).toInstant().toEpochMilli()
}

fun sms(sender: String, body: String, expect: Expect? = null) = Sample(Source.SMS, sender, body, expect)
fun email(sender: String, body: String, expect: Expect? = null) = Sample(Source.EMAIL, sender, body, expect)

fun debit(amount: String, last4: String?, ref: String? = null, merchant: String? = null, balance: String? = null, date: String? = null, kind: AccountKind? = null) =
    Expect(amount, TransactionType.DEBIT, last4, ref, merchant, balance, date, kind)
fun credit(amount: String, last4: String?, ref: String? = null, merchant: String? = null, balance: String? = null, date: String? = null, kind: AccountKind? = null) =
    Expect(amount, TransactionType.CREDIT, last4, ref, merchant, balance, date, kind)
fun transfer(amount: String, last4: String?, ref: String? = null, date: String? = null) =
    Expect(amount, TransactionType.TRANSFER, last4, ref, date = date, kind = AccountKind.CARD)
fun invest(amount: String, last4: String?, ref: String? = null, merchant: String? = null, balance: String? = null, date: String? = null) =
    Expect(amount, TransactionType.INVESTMENT, last4, ref, merchant, balance, date)

val CARD = AccountKind.CARD

object AllCorpora {
    val all: Map<String, List<Sample>> by lazy {
        linkedMapOf(
            "HDFC" to HdfcCorpus.samples, "ICICI" to IciciCorpus.samples, "SBI" to SbiCorpus.samples,
            "Axis" to AxisCorpus.samples, "Kotak" to KotakCorpus.samples, "IDFC" to IdfcCorpus.samples,
            "Yes" to YesCorpus.samples, "BoB" to BobCorpus.samples, "PNB" to PnbCorpus.samples, "AU" to AuCorpus.samples,
        )
    }
    val samples: List<Sample> by lazy { all.values.flatten() }
}
