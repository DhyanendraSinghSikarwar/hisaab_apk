package com.hisaab.parser.statement

import com.hisaab.parser.ParserConfig
import com.hisaab.parser.bank.DepositKind
import com.hisaab.parser.bank.DepositParser
import com.hisaab.parser.bank.LoanStatus
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
import com.hisaab.parser.text.TextNormalizer
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
    /** FD, RD or PPF accounts the statement or advice describes (see [LoanStatus.deposit]); their rows are not transactions. */
    val deposits: List<LoanStatus> = emptyList(),
    /** A holdings sheet's stated totals against its rows' sums; null when the sheet states none. */
    val holdingsCheck: HoldingsCheck? = null,
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
        depositKind(header)?.let { return deposit(lines, header, it, bank, last4, sender, receivedAt) }
        val holdings = holdings(lines, receivedAt, sender)
        val rows = if (holdings.isNotEmpty() || EpfPassbook.isEpf(lines)) emptyList() else rows(lines, bank, last4, isCard, sender, receivedAt)
        val kind = when {
            holdings.isNotEmpty() || INVESTMENT_STATEMENT.containsMatchIn(header) || INVESTMENT_SENDER.containsMatchIn(sender) -> StatementKind.INVESTMENT
            isCard -> StatementKind.CREDIT_CARD
            rows.isNotEmpty() || BANK_STATEMENT.containsMatchIn(header) -> StatementKind.BANK
            else -> StatementKind.OTHER
        }
        return StatementResult(
            bank, last4, if (isCard) AccountKind.CARD else AccountKind.ACCOUNT, rows.map { it.first }, holdings, rows.map { it.second },
            statementKind = kind, summary = summary(lines, java.time.Instant.ofEpochMilli(receivedAt).atZone(config.zone).toLocalDate()),
            holdingsCheck = if (holdings.isEmpty() && rows.isNotEmpty()) null else HoldingsCheckReader.check(lines, holdings),
        )
    }

    // Summary figures: "Total Amount Due: 12,345.00", labels with their values below (a header row, or one label per line).

    /** A label's value, as printed: amounts with their Cr/Dr, or the dates found. */
    private class Pool(val amounts: MutableList<CardFigures.Amount>, val dates: MutableList<String>)

    private fun poolOf(line: String) = Pool(CardFigures.amounts(line).toMutableList(), DATE_ANY.findAll(line).map { it.value }.toMutableList())

    private fun labelsOf(line: String): List<Pair<String, MatchResult>> {
        val all = SUMMARY_LABELS.mapNotNull { (key, rx) -> rx.find(line)?.let { key to it } }.sortedBy { it.second.range.first }
        val out = ArrayList<Pair<String, MatchResult>>()
        for (l in all) if (out.isEmpty() || l.second.range.first > out.last().second.range.last) out += l
        return out
    }

    fun summary(lines: List<String>, ref: LocalDate? = null): StatementSummary {
        var total: Long? = null
        var min: Long? = null
        var due: String? = null
        var limit: Long? = null
        var stmt: String? = null
        var period: String? = null
        val found = HashMap<String, Long>()
        val top = lines.take(SUMMARY_LINES)
        fun give(key: String, amount: CardFigures.Amount?, date: String?) {
            when (key) {
                "total" -> if (total == null && amount != null) total = if (amount.credit) 0L else amount.minor
                "min" -> if (min == null && amount != null) min = if (amount.credit) 0L else amount.minor
                "limit" -> if (limit == null && amount != null) limit = amount.minor
                "due_date" -> if (due == null) due = date
                "stmt_date" -> if (stmt == null) stmt = date
                "period" -> if (period == null) period = date
                else -> if (amount != null) found.putIfAbsent(key, amount.minor)
            }
        }
        var i = 0
        while (i < top.size) {
            val line = top[i]
            // The header of the transactions table ("Narration ... Closing Balance") is not a summary.
            val labels = if (TABLE_HEADER.containsMatchIn(line)) emptyList() else labelsOf(line)
            if (labels.isEmpty()) { i++; continue }
            val pending = ArrayList<String>()
            for ((idx, entry) in labels.withIndex()) {
                val (key, m) = entry
                // The value is between this label and the next one on the same line.
                val end = labels.getOrNull(idx + 1)?.second?.range?.first ?: line.length
                val same = poolOf(line.substring(m.range.last + 1, end))
                if (key in DATE_KEYS) {
                    val d = if (key == "period") same.dates.lastOrNull() else same.dates.firstOrNull()
                    if (d != null) give(key, null, d) else pending += key
                } else {
                    val a = same.amounts.firstOrNull()
                    if (a != null) give(key, a, null) else pending += key
                }
            }
            var next = i + 1
            if (pending.isNotEmpty()) {
                // More labels on the lines below (one per line), then the values: in the same order.
                var k = i + 1
                while (k < top.size && k <= i + LABEL_BLOCK) {
                    val l = labelsOf(top[k])
                    if (l.isEmpty() || TABLE_HEADER.containsMatchIn(top[k])) break
                    val withValue = l.withIndex().any { (idx, e) ->
                        val end = l.getOrNull(idx + 1)?.second?.range?.first ?: top[k].length
                        poolOf(top[k].substring(e.second.range.last + 1, end)).let { it.amounts.isNotEmpty() || it.dates.isNotEmpty() }
                    }
                    if (withValue) break
                    l.forEach { pending += it.first }
                    k++
                }
                val pool = Pool(ArrayList(), ArrayList())
                var taken = 0
                val needDates = pending.count { it in DATE_KEYS }
                val needAmounts = pending.size - needDates
                while (k < top.size && taken < pending.size && labelsOf(top[k]).isEmpty() && (pool.amounts.size < needAmounts || pool.dates.size < needDates)) {
                    val p = poolOf(top[k])
                    if (p.amounts.isEmpty() && p.dates.isEmpty()) break
                    pool.amounts += p.amounts
                    pool.dates += p.dates
                    taken++
                    k++
                }
                for (key in pending) {
                    if (key in DATE_KEYS) {
                        // "Statement Period 12/09/2026 to 11/10/2026": the end of the period.
                        val d = if (key == "period") (if (pool.dates.size >= 2) pool.dates.removeAt(1).also { pool.dates.removeAt(0) } else pool.dates.removeFirstOrNull()) else pool.dates.removeFirstOrNull()
                        give(key, null, d)
                    } else give(key, pool.amounts.removeFirstOrNull(), null)
                }
                next = if (k > i + 1) k else i + 1
            }
            i = next
        }
        val stmtDate = (stmt ?: period)?.let { CardFigures.parse(it, ref, forward = false) }
        val dueDate = due?.let { CardFigures.parse(it, stmtDate ?: ref, forward = true) }
        return StatementSummary(
            total, min, dueDate, limit, stmtDate,
            openingMinor = found["opening"], closingMinor = found["closing"], debitsMinor = found["debits"],
            creditsMinor = found["credits"], availableMinor = found["available"],
        )
    }

    // Deposits: a PPF or RD account statement, or an FD advice/receipt.

    private fun depositKind(header: String): DepositKind? {
        if (CARD_STATEMENT.containsMatchIn(header) || SAVINGS_STATEMENT.containsMatchIn(header)) return null
        return when {
            PPF_STATEMENT.containsMatchIn(header) -> DepositKind.PPF
            RD_STATEMENT.containsMatchIn(header) -> DepositKind.RD
            FD_ADVICE.containsMatchIn(header) -> DepositKind.FD
            else -> null
        }
    }

    /**
     * The deposit's balance (the summary's closing balance, else the last running balance, else an FD's principal), its
     * maturity and what happens then. Deposits into a PPF or RD came from a savings account, which has its own record
     * of them, so the rows are not returned as transactions.
     */
    private fun deposit(lines: List<String>, header: String, kind: DepositKind, bank: String, last4: String?, sender: String, receivedAt: Long): StatementResult {
        val sum = summary(lines, java.time.Instant.ofEpochMilli(receivedAt).atZone(config.zone).toLocalDate())
        val top = lines.take(SUMMARY_LINES).joinToString("\n")
        val number = DepositParser.numbered(TextNormalizer.normalize(header))?.second ?: last4
        val rows = rows(lines, bank, number, false, sender, receivedAt)
        val at = sum.statementDate?.atTime(NOON)?.atZone(config.zone)?.toInstant()?.toEpochMilli() ?: asOfDate(lines, receivedAt)
        val stated = DepositParser.status(top, bank, at)
        val info = DepositParser.info(TextNormalizer.normalize(top), kind)
        val balance = sum.closingMinor ?: rows.lastOrNull { it.first.balanceMinor != null }?.first?.balanceMinor
            ?: DEPOSIT_AMOUNT.find(top)?.let { Money.parse(it.groupValues[1])?.minor }
            ?: stated?.outstandingMinor
        val principal = DEPOSIT_AMOUNT.find(top)?.let { Money.parse(it.groupValues[1])?.minor } ?: stated?.principalMinor
        val deposits = if (number == null) emptyList() else listOf(LoanStatus(stated?.lender ?: bank, number, principal, balance, at, info))
        return StatementResult(
            bank, number, AccountKind.ACCOUNT, emptyList(), emptyList(), statementKind = StatementKind.INVESTMENT, summary = sum, deposits = deposits,
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

    private fun holdings(lines: List<String>, receivedAt: Long, sender: String): List<HoldingSnapshot> {
        val asOf = asOfDate(lines, receivedAt)
        // A spreadsheet of holdings: read by column, whichever platform exported it.
        HoldingSheet.read(lines, asOf, HoldingProfiles.platformOf(sender))?.let { return it }
        // EPFO member passbook: no ISINs, its rows are contributions, not bank transactions.
        if (EpfPassbook.isEpf(lines)) return EpfPassbook.read(lines, asOf)
        val usd = usdRate(lines)
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
            // A foreign ISIN (a US stock) is priced in dollars: converted at the statement's rate, or left out.
            val fx = if (key.startsWith("IN")) 1.0 else usd ?: continue
            out[key] = HoldingSnapshot(
                if (fx == 1.0) kindOf(key, name) else HoldingKind.STOCK, name, key, r.units,
                r.valueMinor?.let { Math.round(it * fx) }, r.investedMinor?.let { Math.round(it * fx) }, asOf,
            )
            if (r.consistent) checked += key
        }
        val found = out.values.filter { (it.valueMinor ?: 0) > 0 }
        // NPS Statement of Transaction: per-tier value and contributions, no ISINs.
        if (NpsStatement.isNps(lines)) return found + NpsStatement.read(lines, asOf)
        // App statements without ISINs (Groww "Holdings statement"): a table of units, invested and current value.
        if (found.isEmpty()) return HoldingTable.read(lines, asOf, usd, HoldingProfiles.platformOf(sender)) { ISIN.containsMatchIn(it) }
        return found
    }

    /** Rupees per US dollar printed on the statement ("USD/INR: 83.50", "Exchange rate 1 USD = INR 83.50"), or null. */
    private fun usdRate(lines: List<String>): Double? = lines.firstNotNullOfOrNull { l ->
        USD_RATE.find(l)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it in 40.0..200.0 }
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
        const val SUMMARY_LINES = 120
        const val LABEL_BLOCK = 8
        val DATE_ANY = CardFigures.DATE
        val DATE_KEYS = setOf("due_date", "stmt_date", "period")
        val SUMMARY_LABELS = listOf(
            "total" to rx(
                """total\s+(?:(?:amount|amt|payment)\.?\s+)?(?:due|payable|outstanding)\b|total\s+dues?\b|closing\s+balance\s+due|new\s+balance\b|""" +
                    """(?<!minimum\s)(?<!min\s)(?<!min\.\s)(?<!past\s)(?<!overdue\s)\bamount\s+(?:due|payable)\b""",
            ),
            "min" to rx("""min(?:imum)?\.?(?:\s+(?:amount|amt|payment))?\.?\s+dues?\b|minimum\s+payable\b"""),
            "due_date" to rx("""(?:payment\s+)?due\s+date\b"""),
            "limit" to rx("""(?<!available\s)(?<!avl\.\s)(?:total\s+)?credit\s+limit\b"""),
            "stmt_date" to rx("""statement\s+(?:generation\s+|closing\s+)?date\b|statement\s+dated\b|bill(?:ing)?\s+date\b"""),
            "period" to rx("""(?:statement|billing)\s+period\b"""),
            "cash_limit" to rx("""(?:available\s+)?cash\s+(?:advance\s+)?limit\b"""),
            "available" to rx("""avail(?:able|\.)?\s+(?:credit\s+)?limit|avl\.?\s+(?:credit\s+|cr\.?\s+)?limit|available\s+credit\b"""),
            "opening" to rx("""(?:opening|previous)\s+(?:statement\s+)?bal(?:ance)?\b"""),
            "closing" to rx("""closing\s+bal(?:ance)?\b(?!\s+due)"""),
            "debits" to rx("""total\s+(?:debits?|withdrawals?)\b|(?<!no\.\s)(?<!no\s)\bdebits\b|\bwithdrawals\b"""),
            "credits" to rx("""total\s+(?:credits?|deposits?)\b|(?<!no\.\s)(?<!no\s)\bcredits\b|\bdeposits\b"""),
        )
        val SAVINGS_STATEMENT = rx("""(?:savings|current)\s+(?:bank\s+)?account\s+statement|statement\s+of\s+(?:your\s+)?(?:savings|current)\s+account""")
        val PPF_STATEMENT = rx("""public\s+provident\s+fund|\bPPF\s+(?:account|a/c|statement|passbook)\b|\bPPF\b.{0,30}\bstatement\b""")
        val RD_STATEMENT = rx("""recurring\s+deposit|\bRD\s+(?:account|a/c|statement|passbook|advice)\b""")
        val FD_ADVICE = rx(
            """(?:fixed|term)\s+deposit\s+(?:advice|receipt|confirmation|certificate|statement|account\s+statement)|deposit\s+(?:advice|receipt|confirmation)""" +
                """|\bFD\s+(?:advice|receipt|confirmation|certificate)|\bTDR\b|\bFDR\b""",
        )
        val DEPOSIT_AMOUNT = rx("""\b(?:deposit|principal)\s+amount\s*(?:\(\s*(?:INR|Rs\.?|₹)\s*\))?\s*[:\-]?\s*(?:INR|Rs\.?|₹)?\s*(\d[\d,]*(?:\.\d{1,2})?)""")
        val USD_RATE = rx(
            """(?:USD\s*/\s*INR|USD\s*-\s*INR|\bUSDINR\b|exchange\s+rate|conversion\s+rate|FX\s+rate|1\s*(?:USD|US\$|\$)\s*=)""" +
                """\s*(?:\(\s*\w+\s*\))?\s*[:=@]?\s*(?:1\s*USD\s*=\s*)?(?:INR|Rs\.?|₹)?\s*(\d{2,3}(?:\.\d{1,4})?)""",
        )
        val TABLE_HEADER = rx("""\b(?:narration|particulars|description|chq|cheque|value\s+dt|txn\s+date|tran\s+date)\b""")
        val INVESTMENT_STATEMENT = rx("""consolidated\s+account\s+statement|\bCAS\b|demat|holding\s+statement|portfolio|mutual\s+fund|\bfolio\b|US\s+stocks|statement\s+of\s+transaction|\bPRAN\b|\bNPS\b""")
        val INVESTMENT_SENDER = rx("""camsonline|kfintech|karvy|nsdl|cdsl|indmoney|zerodha|groww|upstox|angelone|dhan\.co|kuvera|icicidirect|icicisecurities|hdfcsec|kotaksecurities|paytmmoney|etmoney|mfcentral|proteantech|npscra|kfintech-cra|camsnps|npstrust""")
        val BANK_STATEMENT = rx("""account\s+statement|statement\s+of\s+account|savings\s+account|current\s+account""")
        val NOON: LocalTime = LocalTime.NOON
        val CARD_STATEMENT = rx(
            """\bcredit\s+cards?\b|\bcard\s+statement\b|\bminimum\s+(?:amount|payment)\s+due\b|\btotal\s+(?:amount|payment)\s+due\b|\bcredit\s+limit\b|\bnew\s+balance\b""",
        )
        val MASKED_NUMBER = rx(
            """(?:\d{4}|[x*]{4})[\s-]?[x*]{4}[\s-]?[x*]{4}[\s-]?(\d{4})\b|\b\d{4}[\s-]?\d{2}[x*]{2}[\s-]?[x*]{4}[\s-]?(\d{4})\b|""" +
                """\b\d{4,6}[x*]{4,}[\s-]?(\d{4})\b|\b[x*]{2,}[\s-]?(\d{4})\b""",
        )
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
        val COST = rx("""(?:total\s+)?cost\s+value\s*(?:\(\s*(?:INR|Rs\.?|₹)\s*\))?\s*:?\s*(?:INR|Rs\.?|₹)?\s*([\d,]+\.?\d*)""")
        val AS_ON = rx("""\b(?:holdings?|statement|valuation|portfolio|balances?)\b.{0,40}?\bas\s+(?:on|of|at)\s*:?\s*(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}|\d{4}-\d{2}-\d{2}|\d{1,2}[\s-][A-Za-z]{3}[a-z]*[\s,-]+\d{4})""")
        val TABLE_WORDS = rx("""\b(?:ISIN|scheme\s+name|folio|security|closing\s+bal|NAV|valuation|market\s+(?:price|value)|units?)\b""")
        val ETF_NAME = rx("""\bETF\b|\bBEES\b""")
        val BOND_NAME = rx("""\b(?:bond|debenture|NCD|SGB)\b""")
        val GOLD_NAME = rx("""\bgold\b""")
        val BANKS = listOf(
            rx("""\bHDFC\b""") to "HDFC Bank", rx("""\bICICI\b""") to "ICICI Bank", rx("""\bSBI\b|state\s+bank|SBI\s+Card""") to "SBI",
            rx("""\bAxis\b""") to "Axis Bank", rx("""\bKotak\b""") to "Kotak Mahindra Bank", rx("""\bIDFC\b""") to "IDFC FIRST Bank",
            rx("""\bYes\s+Bank\b""") to "Yes Bank", rx("""\bIndusInd\b""") to "IndusInd Bank", rx("""\bAmerican\s+Express\b""") to "American Express",
            rx("""\bHSBC\b""") to "HSBC", rx("""\bRBL\b""") to "RBL Bank", rx("""\bAU\s+Small\s+Finance\b""") to "AU Small Finance Bank",
            rx("""\bFederal\s+Bank\b""") to "Federal Bank", rx("""\bIndia\s+Post\b|department\s+of\s+posts""") to "India Post", rx("""\bStandard\s+Chartered\b""") to "Standard Chartered",
        )
        private fun fmt(p: String): DateTimeFormatter = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH)
        val DATE_FORMATS = listOf(
            "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "d/M/uu", "d-M-uu", "d.M.uu",
            "d MMM uuuu", "d-MMM-uuuu", "d MMM uu", "d-MMM-uu", "d MMM, uuuu", "d MMMM uuuu", "uuuu-M-d",
        ).map(::fmt)
    }
}
