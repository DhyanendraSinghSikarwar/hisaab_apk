package com.hisaab.email.statement

import android.content.Context
import com.hisaab.email.mime.PdfOpen
import com.hisaab.email.mime.PdfTextExtractor
import com.hisaab.email.mime.SpreadsheetExtractor
import com.hisaab.email.mime.StatementFileKind
import com.hisaab.parser.hash.TransactionHasher
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.model.HoldingSnapshot
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.statement.MfOrder
import com.hisaab.parser.statement.MfOrderParser
import com.hisaab.parser.statement.StatementParser
import com.hisaab.shared.db.StatementDao
import com.hisaab.shared.db.StatementEntity
import com.hisaab.shared.repo.HoldingRepository
import com.hisaab.shared.repo.IncomingMessage
import com.hisaab.shared.repo.IngestReport
import com.hisaab.shared.repo.TransactionRepository
import com.hisaab.shared.repo.TransactionsChangedNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Where a statement PDF came from. [key] is stable, so the same PDF is only read once. */
data class StatementMeta(
    val source: String, val key: String, val sender: String, val subject: String?, val fileName: String, val receivedAt: Long,
    /** The text of the email it came with: often says what the password is ("your DOB in DDMMYYYY"). */
    val emailText: String? = null,
)

/** Turns a statement PDF or spreadsheet into transactions for the caller to store. Used by both email sync engines. */
fun interface StatementHandler {
    suspend fun read(bytes: ByteArray, meta: StatementMeta): List<IncomingMessage>

    /**
     * An email that is not a bank alert, read for a mutual fund purchase (SIP instalment, units allotted). Updates the
     * fund's holding and returns the investment as a transaction, unless the bank already reported that payment.
     */
    suspend fun readEmail(messageId: String, from: String, subject: String?, text: String, receivedAt: Long): List<IncomingMessage> = emptyList()
}

/** Tells the user a statement arrived that no saved password opens. The app shows a notification. */
fun interface LockedStatementNotifier {
    fun onLocked(statement: StatementEntity)
}

sealed interface UnlockResult {
    data class Done(val statement: StatementEntity, val report: IngestReport) : UnlockResult
    data object WrongPassword : UnlockResult
    data object Missing : UnlockResult
}

/**
 * Statement PDFs and spreadsheets (.xls, .xlsx, .csv), all on the device: open (trying each saved password on a
 * PDF), parse rows or holdings, record the statement. A PDF no saved password opens is kept in app-private,
 * never-backed-up storage until the user unlocks it; it is deleted once read. A password-protected spreadsheet is
 * recorded as unreadable with kind [KIND_PROTECTED]. Also reads mutual fund purchase emails ([readEmail]).
 */
@Singleton
class StatementProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdf: PdfTextExtractor,
    private val passwords: StatementPasswordStore,
    private val statements: StatementDao,
    private val holdings: HoldingRepository,
    private val transactions: TransactionRepository,
    private val notifier: TransactionsChangedNotifier,
    private val lockedNotifier: LockedStatementNotifier,
    private val accounts: com.hisaab.shared.db.AccountDao,
    private val transactionDao: com.hisaab.shared.db.TransactionDao,
) : StatementHandler {
    private val parser = StatementParser()
    private val mfParser = MfOrderParser()
    private val mfOrders by lazy { context.getSharedPreferences("mf-orders", Context.MODE_PRIVATE) }
    private val lockedDir: File get() = File(context.noBackupFilesDir, "locked-statements").apply { mkdirs() }

    /** Read statements are kept here, private and never backed up, so they can be read again or deleted. */
    private val keptDir: File get() = File(context.noBackupFilesDir, "statements").apply { mkdirs() }

    private fun keep(bytes: ByteArray, name: String): String =
        File(keptDir, sha(bytes) + "." + extension(bytes, name)).apply { if (!exists()) writeBytes(bytes) }.path

    private fun extension(bytes: ByteArray, name: String): String = SpreadsheetExtractor.kind(bytes, name).let {
        if (it == StatementFileKind.UNKNOWN) "pdf" else it.extension
    }

    override suspend fun read(bytes: ByteArray, meta: StatementMeta): List<IncomingMessage> = withContext(Dispatchers.Default) {
        val existing = statements.byKey(meta.key)
        if (existing != null && existing.status != StatementEntity.LOCKED) return@withContext emptyList()

        when (val opened = openWithSaved(bytes, meta.fileName)) {
            is PdfOpen.Text -> messages(opened.text, meta, existing?.id ?: 0, keep(bytes, meta.fileName), existing?.filePath)
            PdfOpen.Locked -> {
                val file = existing?.filePath?.let(::File)?.takeIf { it.exists() } ?: File(lockedDir, sha(bytes) + ".pdf").apply { writeBytes(bytes) }
                val guess = guessIssuer(meta)
                val row = entity(meta, existing?.id ?: 0, StatementEntity.LOCKED, 0, 0, file.path, guess, null)
                val id = statements.upsert(row)
                // Notify once, when the locked statement is first seen.
                if (existing == null) lockedNotifier.onLocked(row.copy(id = id))
                emptyList()
            }
            PdfOpen.Protected -> {
                // A spreadsheet saved with a password: nothing on the phone opens it. Recorded so the user knows why.
                statements.upsert(entity(meta, existing?.id ?: 0, StatementEntity.UNREADABLE, 0, 0, null, guessIssuer(meta), null).copy(kind = KIND_PROTECTED))
                emptyList()
            }
            PdfOpen.Unreadable -> {
                statements.upsert(entity(meta, existing?.id ?: 0, StatementEntity.UNREADABLE, 0, 0, null, null, null))
                emptyList()
            }
        }
    }

    override suspend fun readEmail(messageId: String, from: String, subject: String?, text: String, receivedAt: Long): List<IncomingMessage> =
        withContext(Dispatchers.Default) {
            val order = mfParser.parse(text, subject, from, receivedAt) ?: return@withContext emptyList()
            val key = listOf(order.identifier, order.date, order.amountMinor, order.units).joinToString("|")
            // The same confirmation can arrive twice (Gmail and IMAP both connected, or a resent email).
            if (key in mfOrders.getStringSet(ORDERS_KEY, emptySet()).orEmpty()) return@withContext emptyList()
            val received = java.time.Instant.ofEpochMilli(receivedAt).atZone(IST).toLocalDate()
            val at = if (order.date == received) receivedAt else order.date.atTime(12, 0).atZone(IST).toInstant().toEpochMilli()
            holdings.recordMfOrder(order, at)
            remember(key)
            val amount = order.amountMinor ?: return@withContext emptyList()
            // The bank's SMS for the SIP debit usually comes first, a day or two before the units are allotted.
            val fromMs = order.date.minusDays(3).atStartOfDay(IST).toInstant().toEpochMilli()
            val toMs = order.date.plusDays(2).atStartOfDay(IST).toInstant().toEpochMilli()
            val already = transactionDao.findPotentialDuplicates(amount, fromMs, toMs)
                .any { it.type == TransactionType.DEBIT || it.type == TransactionType.INVESTMENT }
            if (already) return@withContext emptyList()
            listOf(IncomingMessage(investment(order, amount, at, from, receivedAt), "$messageId#mf", text, subject = subject))
        }

    private fun investment(o: MfOrder, amount: Long, at: Long, sender: String, receivedAt: Long) = ParsedTransaction(
        amountMinor = amount, currency = "INR", type = TransactionType.INVESTMENT, bankName = o.platform, accountLast4 = null,
        accountKind = AccountKind.ACCOUNT, merchant = o.schemeName.take(60), upiId = null, referenceNumber = null,
        channel = if (o.isSip) Channel.AUTO_DEBIT else Channel.OTHER, balanceMinor = null, availableLimitMinor = null,
        transactionTime = at, hasExplicitTime = at == receivedAt, category = Category.INVESTMENT, source = Source.EMAIL,
        sender = sender, messageTimestamp = receivedAt, confidence = 0.8f,
        transactionHash = TransactionHasher.hash(amount, TransactionType.INVESTMENT, null, o.date, null),
    )

    @Synchronized
    private fun remember(key: String) {
        val set = mfOrders.getStringSet(ORDERS_KEY, emptySet()).orEmpty().toMutableSet()
        set += key
        // Old keys stop mattering once those emails are past the sync window.
        val trimmed = if (set.size > MAX_ORDER_KEYS) set.toList().takeLast(MAX_ORDER_KEYS).toSet() else set
        mfOrders.edit().putStringSet(ORDERS_KEY, trimmed).apply()
    }

    /**
     * The holdings a statement listed, for its detail page. Saved beside the statement when it is read; a statement
     * read by an older version is read again from the kept file.
     */
    suspend fun holdingsOf(s: StatementEntity): List<HoldingSnapshot> = withContext(Dispatchers.IO) {
        if (s.holdingCount == 0 && s.kind != "INVESTMENT") return@withContext emptyList()
        readHoldings(s.key)?.let { return@withContext it }
        val bytes = s.filePath?.let(::File)?.takeIf { it.exists() }?.readBytes() ?: return@withContext emptyList()
        val opened = openWithSaved(bytes, s.fileName) as? PdfOpen.Text ?: return@withContext emptyList()
        val found = parser.parse(opened.text, s.sender, s.receivedAt).holdings
        saveHoldings(s.key, found)
        found
    }

    private fun holdingsFile(key: String) = File(keptDir, sha(key.toByteArray()) + ".holdings")

    private fun saveHoldings(key: String, list: List<HoldingSnapshot>) {
        if (list.isEmpty()) return
        runCatching {
            holdingsFile(key).writeText(
                list.joinToString("\n") { h ->
                    listOf(h.kind.name, h.name.replace('\t', ' ').replace('\n', ' '), h.identifier, h.units?.toString().orEmpty(),
                        h.valueMinor?.toString().orEmpty(), h.investedMinor?.toString().orEmpty(), h.asOf.toString()).joinToString("\t")
                },
            )
        }
    }

    private fun readHoldings(key: String): List<HoldingSnapshot>? = runCatching {
        val f = holdingsFile(key)
        if (!f.exists()) return@runCatching null
        f.readLines().mapNotNull { line ->
            val c = line.split('\t')
            if (c.size < 7) return@mapNotNull null
            HoldingSnapshot(
                runCatching { HoldingKind.valueOf(c[0]) }.getOrDefault(HoldingKind.OTHER), c[1], c[2], c[3].toDoubleOrNull(),
                c[4].toLongOrNull(), c[5].toLongOrNull(), c[6].toLongOrNull() ?: 0L,
            )
        }
    }.getOrNull()

    /** A PDF the user picked from the phone: read and stored right away. */
    suspend fun importFile(bytes: ByteArray, fileName: String): StatementEntity? {
        val meta = StatementMeta("FILE", "file:" + sha(bytes), "File on this phone", null, fileName, System.currentTimeMillis())
        store(read(bytes, meta))
        return statements.byKey(meta.key)
    }

    /** Opens a locked statement with [password]; with [remember], the password is saved for future statements. */
    suspend fun unlock(id: Long, password: String, remember: Boolean, label: String): UnlockResult {
        val s = statements.byId(id) ?: return UnlockResult.Missing
        val file = s.filePath?.let(::File)?.takeIf { it.exists() } ?: return UnlockResult.Missing
        val bytes = file.readBytes()
        val opened = withContext(Dispatchers.Default) { pdf.open(bytes, password) }
        if (opened !is PdfOpen.Text) return UnlockResult.WrongPassword
        if (remember) passwords.add(label, password)
        val meta = StatementMeta(s.source, s.key, s.sender, s.subject, s.fileName, s.receivedAt, s.emailText)
        val report = store(withContext(Dispatchers.Default) { messages(opened.text, meta, s.id, keep(bytes, s.fileName), s.filePath) })
        return UnlockResult.Done(statements.byId(id) ?: s, report)
    }

    /**
     * Reads a kept statement again: picks up rows and totals a newer version of the reader understands, and
     * brings the account up to date. Rows already stored are recognised and not added twice.
     */
    suspend fun reread(id: Long): IngestReport? {
        val s = statements.byId(id) ?: return null
        if (s.status == StatementEntity.LOCKED) return null
        val bytes = s.filePath?.let(::File)?.takeIf { it.exists() }?.readBytes() ?: return null
        val opened = withContext(Dispatchers.Default) { openWithSaved(bytes, s.fileName) } as? PdfOpen.Text ?: return null
        val meta = StatementMeta(s.source, s.key, s.sender, s.subject, s.fileName, s.receivedAt, s.emailText)
        return store(withContext(Dispatchers.Default) { messages(opened.text, meta, s.id, s.filePath, null) })
    }

    /** Whether [reread] can work: the PDF is still on the phone. Statements read before 1.10 were not kept. */
    fun canReread(s: StatementEntity): Boolean = s.status != StatementEntity.LOCKED && s.filePath?.let { File(it).exists() } == true

    /** After a password is added: tries every locked statement again. Returns how many opened. */
    suspend fun retryLocked(): Int {
        var opened = 0
        for (s in statements.locked()) {
            val bytes = s.filePath?.let(::File)?.takeIf { it.exists() }?.readBytes() ?: continue
            val meta = StatementMeta(s.source, s.key, s.sender, s.subject, s.fileName, s.receivedAt, s.emailText)
            val msgs = read(bytes, meta)
            if (statements.byKey(s.key)?.status != StatementEntity.LOCKED) { store(msgs); opened++ }
        }
        return opened
    }

    suspend fun delete(id: Long) {
        statements.byId(id)?.let { s ->
            s.filePath?.let { File(it).delete() }
            holdingsFile(s.key).delete()
        }
        statements.delete(id)
    }

    /** A PDF, trying each saved password; a spreadsheet or CSV is read as it is. */
    private suspend fun openWithSaved(bytes: ByteArray, name: String): PdfOpen {
        when (SpreadsheetExtractor.kind(bytes, name)) {
            StatementFileKind.XLSX, StatementFileKind.XLS, StatementFileKind.CSV -> return SpreadsheetExtractor.open(bytes, name)
            else -> Unit
        }
        val first = pdf.open(bytes, null)
        if (first != PdfOpen.Locked) return first
        for (p in passwords.attempts(accounts.cardLast4s())) {
            val r = pdf.open(bytes, p)
            if (r is PdfOpen.Text) return r
        }
        return PdfOpen.Locked
    }

    private suspend fun messages(text: String, meta: StatementMeta, id: Long, keptPath: String?, lockedPath: String?): List<IncomingMessage> {
        val r = parser.parse(text, meta.sender, meta.receivedAt)
        if (r.holdings.isNotEmpty()) {
            holdings.record(r.holdings, "STATEMENT")
            saveHoldings(meta.key, r.holdings)
        }
        val msgs = r.transactions.mapIndexed { i, tx ->
            IncomingMessage(tx, "stmt:${meta.key}:$i", "${r.lines.getOrNull(i).orEmpty()}\n\nFrom ${meta.fileName}", sourceName = "STATEMENT")
        }
        val status = if (msgs.isEmpty() && r.holdings.isEmpty()) StatementEntity.EMPTY else StatementEntity.PARSED
        val sum = r.summary
        // Bring the account up to date: a card's available limit, or a bank account's closing balance.
        if (r.bankName != null && r.last4 != null &&
            (msgs.isNotEmpty() || sum.creditLimitMinor != null || sum.closingMinor != null || sum.availableMinor != null)
        ) {
            val at = sum.statementDate?.atTime(23, 59)?.atZone(java.time.ZoneId.of("Asia/Kolkata"))?.toInstant()?.toEpochMilli()
                ?: r.transactions.maxOfOrNull { it.transactionTime } ?: meta.receivedAt
            transactions.applyStatementToAccount(
                r.bankName!!, r.last4!!, r.kind,
                // The closing balance printed in the summary is the most reliable; otherwise the last row's balance.
                closingBalance = sum.closingMinor ?: r.transactions.lastOrNull { it.balanceMinor != null }?.balanceMinor,
                creditLimit = sum.creditLimitMinor, totalDue = sum.totalDueMinor, available = sum.availableMinor, at = at,
            )
        }
        statements.upsert(
            entity(meta, id, status, msgs.size, r.holdings.size, keptPath, r.bankName, r.last4).copy(
                kind = r.statementKind.name, totalDueMinor = sum.totalDueMinor, minDueMinor = sum.minDueMinor,
                dueEpochDay = sum.dueDate?.toEpochDay(), creditLimitMinor = sum.creditLimitMinor, statementEpochDay = sum.statementDate?.toEpochDay(),
                openingMinor = sum.openingMinor, closingMinor = sum.closingMinor, debitsMinor = sum.debitsMinor,
                creditsMinor = sum.creditsMinor, availableMinor = sum.availableMinor,
            ),
        )
        lockedPath?.takeIf { it != keptPath }?.let { File(it).delete() }
        return msgs
    }

    private suspend fun store(msgs: List<IncomingMessage>): IngestReport {
        if (msgs.isEmpty()) return IngestReport.EMPTY
        val report = transactions.ingestBatch(msgs)
        notifier.onTransactionsChanged()
        return report
    }

    private fun entity(meta: StatementMeta, id: Long, status: String, txCount: Int, holdingCount: Int, path: String?, bank: String?, last4: String?) =
        StatementEntity(
            id = id, key = meta.key, source = meta.source, sender = meta.sender, subject = meta.subject, fileName = meta.fileName,
            receivedAt = meta.receivedAt, status = status, transactionCount = txCount, holdingCount = holdingCount, filePath = path,
            bankName = bank, last4 = last4, processedAt = System.currentTimeMillis(),
            emailText = meta.emailText?.trim()?.take(EMAIL_TEXT_CAP),
        )

    /** Before a locked PDF can be read, name the issuer from the sender or subject, for the notification. */
    private fun guessIssuer(meta: StatementMeta): String? {
        val text = "${meta.sender} ${meta.subject.orEmpty()} ${meta.fileName}"
        return ISSUERS.firstOrNull { it.first.containsMatchIn(text) }?.second
            ?: meta.sender.substringBefore('<').trim().trim('"').takeIf { it.isNotEmpty() && '@' !in it }
    }

    companion object {
        /** [StatementEntity.kind] of a spreadsheet saved with a password (status UNREADABLE). */
        const val KIND_PROTECTED = "PROTECTED"
        private const val EMAIL_TEXT_CAP = 4_000
        private const val ORDERS_KEY = "applied"
        private const val MAX_ORDER_KEYS = 500
        private val IST: java.time.ZoneId = java.time.ZoneId.of("Asia/Kolkata")
        private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
        private val ISSUERS = listOf(
            rx("""hdfc""") to "HDFC Bank", rx("""icici""") to "ICICI Bank", rx("""sbi\s*card|sbicard""") to "SBI Card", rx("""\bsbi\b|state bank""") to "SBI",
            rx("""axis""") to "Axis Bank", rx("""kotak""") to "Kotak", rx("""idfc""") to "IDFC FIRST Bank", rx("""indusind""") to "IndusInd Bank",
            rx("""amex|american express""") to "American Express", rx("""hsbc""") to "HSBC", rx("""\brbl""") to "RBL Bank", rx("""yes\s*bank""") to "Yes Bank",
            rx("""camsonline|\bcams\b""") to "CAMS", rx("""kfintech|karvy""") to "KFintech", rx("""nsdl""") to "NSDL", rx("""cdsl""") to "CDSL",
            rx("""indmoney""") to "INDmoney", rx("""zerodha""") to "Zerodha", rx("""groww""") to "Groww", rx("""onecard""") to "OneCard",
        )
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).take(16).joinToString("") { "%02x".format(it) }
}
