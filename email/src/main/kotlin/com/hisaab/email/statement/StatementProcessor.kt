package com.hisaab.email.statement

import android.content.Context
import com.hisaab.email.mime.PdfOpen
import com.hisaab.email.mime.PdfTextExtractor
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

/** Turns a statement PDF into transactions for the caller to store. Used by both email sync engines. */
fun interface StatementHandler {
    suspend fun read(bytes: ByteArray, meta: StatementMeta): List<IncomingMessage>
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
 * Statement PDFs, all on the device: open (trying each saved password), parse rows or holdings, record the
 * statement. A PDF no saved password opens is kept in app-private, never-backed-up storage until the user
 * unlocks it; it is deleted once read.
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
) : StatementHandler {
    private val parser = StatementParser()
    private val lockedDir: File get() = File(context.noBackupFilesDir, "locked-statements").apply { mkdirs() }

    /** Read statements are kept here, private and never backed up, so they can be read again or deleted. */
    private val keptDir: File get() = File(context.noBackupFilesDir, "statements").apply { mkdirs() }

    private fun keep(bytes: ByteArray): String = File(keptDir, sha(bytes) + ".pdf").apply { if (!exists()) writeBytes(bytes) }.path

    override suspend fun read(bytes: ByteArray, meta: StatementMeta): List<IncomingMessage> = withContext(Dispatchers.Default) {
        val existing = statements.byKey(meta.key)
        if (existing != null && existing.status != StatementEntity.LOCKED) return@withContext emptyList()

        when (val opened = openWithSaved(bytes)) {
            is PdfOpen.Text -> messages(opened.text, meta, existing?.id ?: 0, keep(bytes), existing?.filePath)
            PdfOpen.Locked -> {
                val file = existing?.filePath?.let(::File)?.takeIf { it.exists() } ?: File(lockedDir, sha(bytes) + ".pdf").apply { writeBytes(bytes) }
                val guess = guessIssuer(meta)
                val row = entity(meta, existing?.id ?: 0, StatementEntity.LOCKED, 0, 0, file.path, guess, null)
                val id = statements.upsert(row)
                // Notify once, when the locked statement is first seen.
                if (existing == null) lockedNotifier.onLocked(row.copy(id = id))
                emptyList()
            }
            PdfOpen.Unreadable -> {
                statements.upsert(entity(meta, existing?.id ?: 0, StatementEntity.UNREADABLE, 0, 0, null, null, null))
                emptyList()
            }
        }
    }

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
        val report = store(withContext(Dispatchers.Default) { messages(opened.text, meta, s.id, keep(bytes), s.filePath) })
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
        val opened = withContext(Dispatchers.Default) { openWithSaved(bytes) } as? PdfOpen.Text ?: return null
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
        statements.byId(id)?.filePath?.let { File(it).delete() }
        statements.delete(id)
    }

    private suspend fun openWithSaved(bytes: ByteArray): PdfOpen {
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
        if (r.holdings.isNotEmpty()) holdings.record(r.holdings, "STATEMENT")
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

    private companion object {
        const val EMAIL_TEXT_CAP = 4_000
        private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
        val ISSUERS = listOf(
            rx("""hdfc""") to "HDFC Bank", rx("""icici""") to "ICICI Bank", rx("""sbi\s*card|sbicard""") to "SBI Card", rx("""\bsbi\b|state bank""") to "SBI",
            rx("""axis""") to "Axis Bank", rx("""kotak""") to "Kotak", rx("""idfc""") to "IDFC FIRST Bank", rx("""indusind""") to "IndusInd Bank",
            rx("""amex|american express""") to "American Express", rx("""hsbc""") to "HSBC", rx("""\brbl""") to "RBL Bank", rx("""yes\s*bank""") to "Yes Bank",
            rx("""camsonline|\bcams\b""") to "CAMS", rx("""kfintech|karvy""") to "KFintech", rx("""nsdl""") to "NSDL", rx("""cdsl""") to "CDSL",
            rx("""indmoney""") to "INDmoney", rx("""zerodha""") to "Zerodha", rx("""groww""") to "Groww", rx("""onecard""") to "OneCard",
        )
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).take(16).joinToString("") { "%02x".format(it) }
}
