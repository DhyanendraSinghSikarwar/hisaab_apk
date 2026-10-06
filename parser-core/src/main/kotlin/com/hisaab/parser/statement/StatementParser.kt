package com.hisaab.parser.statement

import com.hisaab.parser.ParserConfig
import com.hisaab.parser.extract.AccountExtractor
import com.hisaab.parser.extract.ChannelDetector
import com.hisaab.parser.extract.Money
import com.hisaab.parser.extract.RawMerchant
import com.hisaab.parser.hash.TransactionHasher
import com.hisaab.parser.merchant.MerchantNormalizer
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.text.rx
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** What a statement PDF held: transaction rows (card or bank statement), holdings (CAS, broker), or nothing usable. */
data class StatementResult(
    val bankName: String?,
    val last4: String?,
    val kind: AccountKind,
    val transactions: List<ParsedTransaction>,
    val holdings: List<HoldingSnapshot>,
    /** The statement line each transaction came from, in the same order, kept as its source text. */
    val lines: List<String> = emptyList(),
    val statementKind: StatementKind = StatementKind.OTHER,
    val summary: StatementSummary = StatementSummary(),
)

enum class StatementKind(val label: String) { CREDIT_CARD("Credit card"), BANK("Bank account"), INVESTMENT("Investments"), OTHER("Other") }

/** The figures printed at the top of a statement. Card statements have dues; any field may be missing. */
data class StatementSummary(
    val totalDueMinor: Long? = null,
    val minDueMinor: Long? = null,
    val dueDate: LocalDate? = null,
    val creditLimitMinor: Long? = null,
    val statementDate: LocalDate? = null,
    val openingMinor: Long? = null,
    val closingMinor: Long? = null,
    val debitsMinor: Long? = null,
    val creditsMinor: Long? = null,
    val availableMinor: Long? = null,
)

/**
 * Reads the text of a statement PDF.
 *
 * Transaction statements: every line that starts with a date and ends with an amount is a row. A credit
 * card row is a spend unless it says Cr, payment, refund or reversal. A bank row usually ends with the
 * running balance, so the direction comes from whether the balance went up or down.
 *
 * Holdings statements (CAMS/KFintech/NSDL/CDSL CAS, broker statements): a line with an ISIN and its units
 * and value, or a CAMS "Closing Unit Balance ... Market Value" line under an "ISIN:" scheme header.
 */
class StatementParser(private val config: ParserConfig = ParserConfig()) {

    fun parse(text: String, sender: String, receivedAt: Long): StatementResult {
        val lines = text.lineSequence().map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }.toList()
        val header = lines.take(HEADER_LINES).joinToString(" ")
        val isCard = CARD_STATEMENT.containsMatchIn(header)
        val last4 = MASKED_NUMBER.find(header)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() } ?: AccountExtractor.extract(header)?.last4
        val bank = BANKS.firstOrNull { it.first.containsMatchIn(header) }?.second ?: sender.substringBefore('<').trim().ifEmpty { "Statement" }
        val holdings = holdings(lines, receivedAt)
        val rows = if (holdings.isNotEmpty()) emptyList() else rows(lines, bank, last4, isCard, sender, receivedAt)
        val kind = when {
            holdings.isNotEmpty() || INVESTMENT_STATEMENT.containsMatchIn(header) || INVESTMENT_SENDER.containsMatchIn(sender) -> StatementKind.INVESTMENT
            isCard -> StatementKind.CREDIT_CARD
            rows.isNotEmpty() || BANK_STATEMENT.containsMatchIn(header) -> StatementKind.BANK
            else -> StatementKind.OTHER
        }
        return StatementResult(
            bank, last4, if (isCard) AccountKind.CARD else AccountKind.ACCOUNT, rows.map { it.first }, holdings, rows.map { it.second },
            statementKind = kind, summary = summary(lines),
        )
    }

    // Summary figures: "Total Amount Due: 12,345.00", or labels on one line and values on the next.

    fun summary(lines: List<String>): StatementSummary {
        var total: Long? = null
        var min: Long? = null
        var due: LocalDate? = null
        var limit: Long? = null
        var stmt: LocalDate? = null
        val found = HashMap<String, Long>()
        val top = lines.take(SUMMARY_LINES)
        for ((i, line) in top.withIndex()) {
            // The header of the transactions table ("Narration ... Closing Balance") is not a summary.
            if (TABLE_HEADER.containsMatchIn(line)) continue
            val labels = SUMMARY_LABELS.mapNotNull { (key, rx) -> rx.find(line)?.let { key to it } }.sortedBy { it.second.range.first }
            if (labels.isEmpty()) continue
            val next = top.getOrNull(i + 1).orEmpty()
            val nextAmounts = AMOUNT_ANY.findAll(next).map { it.value }.toMutableList()
            val nextDates = DATE_ANY.findAll(next).map { it.value }.toMutableList()
            for ((idx, entry) in labels.withIndex()) {
                val (key, m) = entry
                // The value is between this label and the next one on the same line, or else the next unused value below.
                val end = labels.getOrNull(idx + 1)?.second?.range?.first ?: line.length
                val sameLine = line.substring(m.range.last + 1, end)
                if (key == "due_date" || key == "stmt_date") {
                    val d = (DATE_ANY.find(sameLine)?.value ?: nextDates.removeFirstOrNull())?.let(::parseDate)
                    if (key == "due_date" && due == null) due = d
                    if (key == "stmt_date" && stmt == null) stmt = d
                } else {
                    val a = (AMOUNT_ANY.find(sameLine)?.value ?: nextAmounts.removeFirstOrNull())?.let { Money.parse(it)?.minor ?: 0L.takeIf { _ -> it.replace(",", "").toDoubleOrNull() == 0.0 } }
                    when (key) {
                        "total" -> if (total == null) total = a
                        "min" -> if (min == null) min = a
                        "limit" -> if (limit == null) limit = a
                        else -> if (a != null) found.putIfAbsent(key, a)
                    }
                }
            }
        }
        return StatementSummary(
            total, min, due, limit, stmt,
            openingMinor = found["opening"], closingMinor = found["closing"], debitsMinor = found["debits"],
            creditsMinor = found["credits"], availableMinor = found["available"],
        )
    }

    // Transaction rows.

    private fun rows(lines: List<String>, bank: String, last4: String?, isCard: Boolean, sender: String, receivedAt: Long): List<Pair<ParsedTransaction, String>> {
        val received = java.time.Instant.ofEpochMilli(receivedAt).atZone(config.zone).toLocalDate()
        val out = ArrayList<Pair<ParsedTransaction, String>>()
        var previousBalance: Long? = null
        for (line in lines) {
            if (SKIP_ROW.containsMatchIn(line)) {
                // An opening balance row seeds the running balance for the first transaction.
                if (OPENING.containsMatchIn(line)) AMOUNT.findAll(line).lastOrNull()?.let { previousBalance = Money.parse(it.groupValues[1])?.minor }
                continue
            }
            val dateMatch = LEADING_DATE.find(line) ?: continue
            val date = parseDate(dateMatch.value) ?: continue
            if (date.isAfter(received.plusDays(1)) || date.isBefore(received.minusYears(2))) continue
            val amounts = AMOUNT.findAll(line.substring(dateMatch.range.last + 1)).toList()
            if (amounts.isEmpty()) continue
            val rest = line.substring(dateMatch.range.last + 1)
            val description = rest.substring(0, amounts.first().range.first).replace(SECOND_DATE, " ").trim().trim('-', '|', ' ')
            if (description.length < 2) continue

            val (amountStr, balance) = if (!isCard && amounts.size >= 2) amounts[amounts.size - 2] to Money.parse(amounts.last().groupValues[1])?.minor
            else amounts.last() to null
            val amount = Money.parse(amountStr.groupValues[1])?.minor ?: continue
            if (amount <= 0) continue
            val crDr = amountStr.groupValues[2].uppercase()

            val type = when {
                isCard && CARD_PAYMENT.containsMatchIn(description) -> TransactionType.TRANSFER
                crDr.startsWith("C") -> TransactionType.CREDIT
                crDr.startsWith("D") -> TransactionType.DEBIT
                balance != null && previousBalance != null -> if (balance > previousBalance!!) TransactionType.CREDIT else TransactionType.DEBIT
                CREDIT_WORDS.containsMatchIn(description) -> TransactionType.CREDIT
                else -> TransactionType.DEBIT
            }
            if (balance != null) previousBalance = balance

            val channel = ChannelDetector.detect(description)
            val merchant = MerchantNormalizer.normalize(RawMerchant(description.take(60), null), type, channel, description)
            val at = date.atTime(NOON).atZone(config.zone).toInstant().toEpochMilli()
            out += ParsedTransaction(
                amountMinor = amount, currency = "INR", type = type, bankName = bank, accountLast4 = last4,
                accountKind = if (isCard) AccountKind.CARD else AccountKind.ACCOUNT, merchant = merchant.name ?: description.take(40),
                upiId = null, referenceNumber = null, channel = channel, balanceMinor = balance, availableLimitMinor = null,
                transactionTime = at, hasExplicitTime = false, category = merchant.category, source = Source.STATEMENT,
                sender = sender, messageTimestamp = receivedAt, confidence = 0.7f,
                transactionHash = TransactionHasher.hash(amount, type, last4, date, null),
            ) to line
        }
        return out
    }

    private fun parseDate(s: String): LocalDate? {
        if (s.isBlank()) return null
        val t = s.trim().replace(Regex("""\s+"""), " ")
        for (f in DATE_FORMATS) runCatching { return LocalDate.parse(t, f) }
        return null
    }

    // Holdings.

    private fun holdings(lines: List<String>, receivedAt: Long): List<HoldingSnapshot> {
        val asOf = asOfDate(lines, receivedAt)
        val out = LinkedHashMap<String, HoldingSnapshot>()
        // Rows whose figures were cross-checked (units x price = value); a later checked row beats an earlier unchecked one.
        val checked = HashSet<String>()
        val fromClosing = HashSet<String>()
        var schemeName: String? = null
        var schemeIsin: String? = null
        for ((i, line) in lines.withIndex()) {
            // CAMS/KFintech CAS: "<scheme> - ISIN: INF...(Advisor: DIRECT)" then "Closing Unit Balance: x ... Market Value on ...: INR y".
            ISIN_LABEL.find(line)?.let { m ->
                schemeIsin = m.groupValues[1]
                schemeName = line.substring(0, m.range.first).replace(SCHEME_CODE, "").trim().trim('-', ' ', ':').ifEmpty { null }
                    ?: lines.getOrNull(i - 1)?.trim()
            }
            if (CLOSING_LABEL.containsMatchIn(line)) {
                val isin = schemeIsin ?: continue
                // The figures can wrap onto the next lines when the PDF is read.
                val joined = (0..2).mapNotNull { lines.getOrNull(i + it) }.joinToString(" ")
                val units = CLOSING_UNITS.find(joined)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: continue
                val nav = NAV_ON.find(joined)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
                val value = MARKET_VALUE.find(joined)?.let { Money.parse(it.groupValues[1])?.minor }
                    ?: nav?.let { Math.round(units * it * 100) }
                val cost = COST.find(joined)?.let { Money.parse(it.groupValues[1])?.minor }
                val name = schemeName ?: isin
                val prev = out[isin]
                // One scheme in two folios: the folios add up.
                out[isin] = if (prev != null && isin in fromClosing) {
                    prev.copy(units = (prev.units ?: 0.0) + units, valueMinor = (prev.valueMinor ?: 0) + (value ?: 0),
                        investedMinor = if (prev.investedMinor == null && cost == null) null else (prev.investedMinor ?: 0) + (cost ?: 0))
                } else {
                    HoldingSnapshot(kindOf(isin, name), name, isin, units, value, cost, asOf)
                }
                fromClosing += isin
                checked += isin
                schemeIsin = null
                continue
            }
            if (ISIN_LABEL.containsMatchIn(line)) continue
            // NSDL/CDSL CAS and broker statements: "INE009A01021 INFOSYS LIMITED 10 1,500.00 15,000.00", or the
            // CDSL mutual-fund table "<scheme> INF... <folio> <units> <NAV> <invested> <value> <gain>".
            val isin = ISIN.find(line) ?: continue
            if (LEADING_DATE.containsMatchIn(line)) continue // a transaction row, not a holding
            val before = line.substring(0, isin.range.first).trim()
            var after = line.substring(isin.range.last + 1)
            var row: HoldingLines.Row? = HoldingLines.read(before, after)
            // The figures wrapped onto the next line or two.
            var extra = 1
            while (row?.consistent != true && extra <= 2) {
                val next = lines.getOrNull(i + extra) ?: break
                if (ISIN.containsMatchIn(next) || LEADING_DATE.containsMatchIn(next)) break
                after += " " + next
                val longer = HoldingLines.read(before, after)
                if (longer != null && (row == null || longer.consistent)) row = longer
                extra++
            }
            val r = row ?: continue
            var name = r.name
            // A scheme name split over two lines ("Parag Parikh Flexi Cap Fund - Direct" / "Plan Growth INF879O01027 ...").
            val prevLine = lines.getOrNull(i - 1)?.trim().orEmpty()
            if ((isin.value.startsWith("INF") || before.isNotEmpty()) && (name.length < 3 || name.split(' ').size <= 2) &&
                prevLine.isNotEmpty() && !ISIN.containsMatchIn(prevLine) &&
                !TABLE_WORDS.containsMatchIn(prevLine) && NUMBER.findAll(prevLine).count() <= 1 && prevLine.any { it.isLetter() }
            ) {
                name = HoldingLines.cleanName("$prevLine $name")
            }
            if (name.length < 2) continue
            val key = isin.value
            if (key in fromClosing) continue
            if (key in out && (key in checked || !r.consistent)) continue
            out[key] = HoldingSnapshot(kindOf(key, name), name, key, r.units, r.valueMinor, r.investedMinor, asOf)
            if (r.consistent) checked += key
        }
        return out.values.filter { (it.valueMinor ?: 0) > 0 }
    }

    /** "Holdings as on 31-May-2026" / "Statement for the period ... to 31-05-2026": when the values were true. */
    private fun asOfDate(lines: List<String>, receivedAt: Long): Long {
        val received = java.time.Instant.ofEpochMilli(receivedAt).atZone(config.zone).toLocalDate()
        val date = lines.take(SUMMARY_LINES).firstNotNullOfOrNull { l -> AS_ON.find(l)?.groupValues?.get(1)?.let(::parseDate) }
            ?: return receivedAt
        if (date.isAfter(received.plusDays(1)) || date.isBefore(received.minusMonths(6))) return receivedAt
        return date.atTime(NOON).atZone(config.zone).toInstant().toEpochMilli()
    }

    private fun kindOf(isin: String, name: String?): HoldingKind = when {
        name != null && ETF_NAME.containsMatchIn(name) -> HoldingKind.ETF
        isin.startsWith("INF") -> HoldingKind.MUTUAL_FUND
        name != null && BOND_NAME.containsMatchIn(name) -> HoldingKind.BOND
        name != null && GOLD_NAME.containsMatchIn(name) -> HoldingKind.GOLD
        else -> HoldingKind.STOCK
    }

    private companion object {
        const val HEADER_LINES = 40
        const val SUMMARY_LINES = 80
        val AMOUNT_ANY = rx("""\d{1,3}(?:,\d{2,3})*\.\d{2}|\d+\.\d{2}""")
        val DATE_ANY = rx("""\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|\d{1,2}[\s-][A-Za-z]{3}[a-z]*[\s,-]+\d{2,4}""")
        val SUMMARY_LABELS = listOf(
            "total" to rx("""total\s+(?:amount\s+)?dues?\b|total\s+outstanding|closing\s+balance\s+due"""),
            "min" to rx("""minimum\s+(?:amount\s+)?dues?\b|min\.?\s+amt\.?\s+due"""),
            "due_date" to rx("""(?:payment\s+)?due\s+date\b"""),
            "limit" to rx("""(?<!available\s)(?<!avl\.\s)(?:total\s+)?credit\s+limit\b"""),
            "stmt_date" to rx("""statement\s+date\b"""),
            "available" to rx("""avail(?:able|\.)?\s+(?:credit\s+)?limit|avl\.?\s+(?:credit\s+|cr\.?\s+)?limit"""),
            "opening" to rx("""(?:opening|previous)\s+(?:statement\s+)?bal(?:ance)?\b"""),
            "closing" to rx("""closing\s+bal(?:ance)?\b(?!\s+due)"""),
            "debits" to rx("""total\s+(?:debits?|withdrawals?)\b|(?<!no\.\s)(?<!no\s)\bdebits\b|\bwithdrawals\b"""),
            "credits" to rx("""total\s+(?:credits?|deposits?)\b|(?<!no\.\s)(?<!no\s)\bcredits\b|\bdeposits\b"""),
        )
        val TABLE_HEADER = rx("""\b(?:narration|particulars|description|chq|cheque|value\s+dt|txn\s+date|tran\s+date)\b""")
        val INVESTMENT_STATEMENT = rx("""consolidated\s+account\s+statement|\bCAS\b|demat|holding\s+statement|portfolio|mutual\s+fund|\bfolio\b|US\s+stocks""")
        val INVESTMENT_SENDER = rx("""camsonline|kfintech|karvy|nsdl|cdsl|indmoney|zerodha|groww|upstox|angelone|dhan\.co|kuvera""")
        val BANK_STATEMENT = rx("""account\s+statement|statement\s+of\s+account|savings\s+account|current\s+account""")
        val NOON: LocalTime = LocalTime.NOON
        val CARD_STATEMENT = rx("""\bcredit\s+card\b|\bcard\s+statement\b|\bminimum\s+amount\s+due\b|\btotal\s+amount\s+due\b""")
        val MASKED_NUMBER = rx("""(?:\d{4}|[x*]{4})[\s-]?[x*]{4}[\s-]?[x*]{4}[\s-]?(\d{4})\b|\b[x*]{4,}(\d{4})\b""")
        val LEADING_DATE = rx("""^\s*(?:\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|\d{1,2}[\s-][A-Za-z]{3}[a-z]*[\s,-]+\d{2,4})""")
        val SECOND_DATE = rx("""\b\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}\b""")
        val AMOUNT = rx("""(\d{1,3}(?:,\d{2,3})*\.\d{2}|\d+\.\d{2})\s*(Cr|CR|Dr|DR|C|D)?\b""")
        val NUMBER = rx("""\d[\d,]*(?:\.\d+)?""")
        val SKIP_ROW = rx("""\b(?:opening\s+balance|closing\s+balance|balance\s+(?:b/f|c/f|brought|carried)|total|minimum\s+amount|amount\s+due|""" +
            """payment\s+due|credit\s+limit|statement\s+date|reward|points?\s+(?:earned|balance))\b""")
        val OPENING = rx("""\bopening\s+balance\b|\bbalance\s+(?:b/f|brought)\b""")
        val CARD_PAYMENT = rx("""\b(?:payment\s+received|thank\s+you|bbps\s+payment|autopay|auto\s+debit\s+payment|neft\s+payment|payment\s*-)\b""")
        val CREDIT_WORDS = rx("""\b(?:refund|reversal|reversed|cashback|credit|interest\s+credit|salary|neft\s+cr|imps\s+cr|by\s+transfer|deposit)\b""")
        val ISIN = Regex("""\b([A-Z]{2}[A-Z0-9]{9}\d)\b""") // ISINs are upper case: case-sensitive on purpose
        val ISIN_LABEL = Regex("""ISIN\s*:\s*([A-Z]{2}[A-Z0-9]{9}\d)""")
        val SCHEME_CODE = Regex("""^[A-Z0-9]{2,12}-""")
        val CLOSING_LABEL = rx("""closing\s+unit\s+balance""")
        val CLOSING_UNITS = rx("""closing\s+unit\s+balance\s*:?\s*([\d,]+\.?\d*)""")
        val NAV_ON = rx("""\bNAV\s+on\s+[^:]{4,20}:\s*(?:INR|Rs\.?|₹)?\s*([\d,]+\.?\d*)""")
        val MARKET_VALUE = rx("""(?:market\s+value|valuation)\s+on\s+[^:]{4,20}:\s*(?:INR|Rs\.?|₹)?\s*([\d,]+\.?\d*)""")
        val COST = rx("""(?:total\s+)?cost\s+value\s*:?\s*(?:INR|Rs\.?|₹)?\s*([\d,]+\.?\d*)""")
        val AS_ON = rx("""\b(?:holdings?|statement|valuation|portfolio|balances?)\b.{0,40}?\bas\s+(?:on|of|at)\s*:?\s*(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|\d{1,2}[\s-][A-Za-z]{3}[a-z]*[\s,-]+\d{4})""")
        val TABLE_WORDS = rx("""\b(?:ISIN|scheme\s+name|folio|security|closing\s+bal|NAV|valuation|market\s+(?:price|value)|units?)\b""")
        val ETF_NAME = rx("""\bETF\b|\bBEES\b""")
        val BOND_NAME = rx("""\b(?:bond|debenture|NCD|SGB)\b""")
        val GOLD_NAME = rx("""\bgold\b""")
        val BANKS = listOf(
            rx("""\bHDFC\b""") to "HDFC Bank", rx("""\bICICI\b""") to "ICICI Bank", rx("""\bSBI\b|state\s+bank|SBI\s+Card""") to "SBI",
            rx("""\bAxis\b""") to "Axis Bank", rx("""\bKotak\b""") to "Kotak Mahindra Bank", rx("""\bIDFC\b""") to "IDFC FIRST Bank",
            rx("""\bYes\s+Bank\b""") to "Yes Bank", rx("""\bIndusInd\b""") to "IndusInd Bank", rx("""\bAmerican\s+Express\b""") to "American Express",
            rx("""\bHSBC\b""") to "HSBC", rx("""\bRBL\b""") to "RBL Bank", rx("""\bAU\s+Small\s+Finance\b""") to "AU Small Finance Bank",
            rx("""\bFederal\s+Bank\b""") to "Federal Bank", rx("""\bStandard\s+Chartered\b""") to "Standard Chartered",
        )
        private fun fmt(p: String): DateTimeFormatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH)
        val DATE_FORMATS = listOf(
            "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "d/M/uu", "d-M-uu", "d.M.uu",
            "d MMM uuuu", "d-MMM-uuuu", "d MMM uu", "d-MMM-uu", "d MMM, uuuu", "d MMMM uuuu",
        ).map(::fmt)
    }
}
