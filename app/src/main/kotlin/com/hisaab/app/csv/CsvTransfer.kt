package com.hisaab.app.csv

import com.hisaab.parser.hash.TransactionHasher
import com.hisaab.app.i18n.t
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.Reader
import java.io.Writer
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/** CSV export and import. The export is also the import format, so a backup restores losslessly. */
object CsvTransfer {
    val HEADER = arrayOf(
        "date", "amount", "currency", "type", "bank", "account_last4", "account_kind", "merchant", "category",
        "channel", "reference", "balance", "upi_id", "note", "hash",
    )

    fun export(rows: List<TransactionEntity>, out: Writer) {
        CSVWriter(out).use { w ->
            w.writeNext(HEADER, false)
            for (t in rows) {
                w.writeNext(
                    arrayOf(
                        Instant.ofEpochMilli(t.timestamp).toString(), minorToString(t.amountMinor), t.currency, t.type.name, t.bankName,
                        t.accountLast4.orEmpty(), t.accountKind.name, t.merchant.orEmpty(), t.category.name, t.channel.name,
                        t.referenceNumber.orEmpty(), t.balanceMinor?.let(::minorToString).orEmpty(), t.upiId.orEmpty(), t.note.orEmpty(),
                        t.transactionHash,
                    ),
                    false,
                )
            }
        }
    }

    data class ImportResult(val rows: List<ParsedTransaction>, val badLines: Int)

    /** Reads an export. Columns are found by header name, so a hand-edited file with reordered columns works. */
    fun import(input: Reader, zone: ZoneId = ZoneId.systemDefault()): ImportResult {
        val rows = ArrayList<ParsedTransaction>()
        var bad = 0
        CSVReader(input).use { r ->
            val header = r.readNext()?.map { it.trim().lowercase() } ?: return ImportResult(emptyList(), 0)
            fun idx(name: String) = header.indexOf(name)
            val iDate = idx("date"); val iAmount = idx("amount"); val iType = idx("type")
            require(iDate >= 0 && iAmount >= 0 && iType >= 0) { t("Not a DhanKosh export: needs date, amount and type columns") }
            while (true) {
                val line = r.readNext() ?: break
                fun col(name: String) = idx(name).takeIf { it >= 0 && it < line.size }?.let { line[it].trim() }?.takeIf { it.isNotEmpty() }
                try {
                    val time = Instant.parse(line[iDate].trim()).toEpochMilli()
                    val amount = BigDecimal(line[iAmount].trim().replace(",", "")).movePointRight(2).longValueExact()
                    val type = TransactionType.valueOf(line[iType].trim().uppercase())
                    val last4 = col("account_last4")
                    val ref = col("reference")
                    val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
                    rows += ParsedTransaction(
                        amountMinor = amount, currency = col("currency") ?: "INR", type = type, bankName = col("bank") ?: "Imported",
                        accountLast4 = last4, accountKind = col("account_kind")?.let { enumOr(it, AccountKind.ACCOUNT) } ?: AccountKind.ACCOUNT,
                        merchant = col("merchant"), upiId = col("upi_id"), referenceNumber = ref,
                        channel = col("channel")?.let { enumOr(it, Channel.OTHER) } ?: Channel.OTHER,
                        balanceMinor = col("balance")?.let { BigDecimal(it).movePointRight(2).longValueExact() }, availableLimitMinor = null,
                        transactionTime = time, hasExplicitTime = true,
                        category = col("category")?.let { enumOr(it, Category.OTHER) } ?: Category.OTHER,
                        source = Source.SMS, sender = "csv", messageTimestamp = time, confidence = 1f,
                        transactionHash = col("hash") ?: TransactionHasher.hash(amount, type, last4, date, ref),
                    )
                } catch (_: RuntimeException) {
                    bad++
                }
            }
        }
        return ImportResult(rows, bad)
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String, fallback: E): E =
        runCatching { enumValueOf<E>(value.uppercase()) }.getOrDefault(fallback)

    private fun minorToString(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
}
