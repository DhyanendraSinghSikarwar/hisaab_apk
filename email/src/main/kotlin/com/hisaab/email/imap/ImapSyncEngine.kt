package com.hisaab.email.imap

import com.hisaab.email.statement.StatementHandler
import com.hisaab.email.statement.StatementMeta
import com.hisaab.email.sync.EmailSink
import com.hisaab.email.sync.GmailSyncEngine
import com.hisaab.email.sync.SenderAllowList
import com.hisaab.email.sync.SyncMode
import com.hisaab.email.sync.SyncReport
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.ProcessedEmailEntity
import com.hisaab.shared.repo.IncomingMessage
import java.util.concurrent.TimeUnit

/** The connected inboxes plus the sync position of each; the Settings stores implement it. */
interface ImapSyncState {
    suspend fun logins(): List<MailLogin>
    suspend fun lookbackDays(): Int
    suspend fun senders(): List<String>
    suspend fun readPdfStatements(): Boolean
    suspend fun enabled(): Boolean
    /** Receive time the last successful sync of [email] covered up to, or null for a first sync. */
    suspend fun lastSyncAt(email: String): Long?
    suspend fun saveSync(email: String, at: Long, result: String)
}

/**
 * Reads bank alerts over IMAP. The first sync covers the look-back window; later syncs re-read from a
 * day before the previous one (so late-arriving mail is not missed) and skip ids already processed.
 */
class ImapSyncEngine(
    private val client: MailClient,
    private val state: ImapSyncState,
    private val sink: EmailSink,
    private val registry: ParserRegistry,
    /** Reads statement PDFs (card, bank, CAS); null skips attachments. */
    private val pdf: StatementHandler?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Syncs every connected inbox in turn. One address refusing its password doesn't stop the others;
     * only when every inbox is refused does the sync fail with [MailAuthException].
     */
    suspend fun sync(): SyncReport {
        if (!state.enabled()) return SyncReport(SyncMode.DISABLED)
        val logins = state.logins().ifEmpty { throw MailAuthException("Sign in to your email again") }
        var total: SyncReport? = null
        var refused: MailAuthException? = null
        for (login in logins) {
            val r = try { syncOne(login) } catch (e: MailAuthException) { refused = e; continue }
            total = total?.let { it.copy(fetched = it.fetched + r.fetched, parsed = it.parsed + r.parsed, ingest = it.ingest + r.ingest, millis = it.millis + r.millis) } ?: r
        }
        return total ?: throw (refused ?: MailAuthException("Sign in to your email again"))
    }

    private suspend fun syncOne(login: MailLogin): SyncReport {
        val start = clock()
        val windowStart = start - TimeUnit.DAYS.toMillis(state.lookbackDays().toLong())
        val last = state.lastSyncAt(login.email)
        val since = last?.let { maxOf(windowStart, it - OVERLAP_MS) } ?: windowStart
        val mode = if (last == null) SyncMode.FULL else SyncMode.INCREMENTAL

        val allow = SenderAllowList(state.senders())
        val readPdf = state.readPdfStatements() && pdf != null
        val mails = client.fetchSince(login, since, allow::allows, readPdf)

        val done = sink.alreadyProcessed(mails.map { it.id })
        val fresh = mails.filter { it.id !in done }
        val incoming = ArrayList<IncomingMessage>()
        val processed = ArrayList<ProcessedEmailEntity>(fresh.size)
        for (m in fresh) {
            val found = parse(m, readPdf)
            incoming += found
            processed += ProcessedEmailEntity(m.id, start, if (found.isEmpty()) GmailSyncEngine.OUTCOME_REJECTED else GmailSyncEngine.OUTCOME_PARSED, null)
        }
        val ingest = sink.store(incoming, processed)
        val report = SyncReport(mode, fetched = fresh.size, parsed = incoming.size, ingest = ingest, millis = clock() - start)
        state.saveSync(login.email, start, report.summary())
        return report
    }

    private suspend fun parse(m: FetchedMail, readPdf: Boolean): List<IncomingMessage> {
        val out = ArrayList<IncomingMessage>(1)
        registry.parse(m.text, m.from, m.receivedAt, Source.EMAIL)?.let { out += IncomingMessage(it, m.id, m.text, subject = m.subject) }
        if (readPdf) {
            for ((index, bytes) in m.pdfs.withIndex()) {
                val name = m.pdfNames.getOrNull(index) ?: "statement.pdf"
                out += pdf!!.read(bytes, StatementMeta("EMAIL", "${m.id}#pdf$index", m.from, m.subject, name, m.receivedAt))
            }
        }
        return out
    }

    private companion object {
        val OVERLAP_MS = TimeUnit.DAYS.toMillis(1)
    }
}
