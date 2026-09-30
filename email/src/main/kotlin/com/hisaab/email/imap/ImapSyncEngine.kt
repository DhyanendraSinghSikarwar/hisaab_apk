package com.hisaab.email.imap

import com.hisaab.email.mime.PdfTextSource
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

/** The login plus the sync position the engine needs; the Settings stores implement it. */
interface ImapSyncState {
    suspend fun login(): MailLogin?
    suspend fun lookbackDays(): Int
    suspend fun senders(): List<String>
    suspend fun readPdfStatements(): Boolean
    suspend fun enabled(): Boolean
    /** Receive time the last successful sync covered up to, or null for a first sync. */
    suspend fun lastSyncAt(): Long?
    suspend fun saveSync(at: Long, result: String)
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
    private val pdf: PdfTextSource?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(): SyncReport {
        if (!state.enabled()) return SyncReport(SyncMode.DISABLED)
        val login = state.login() ?: throw MailAuthException("Sign in to your email again")
        val start = clock()
        val windowStart = start - TimeUnit.DAYS.toMillis(state.lookbackDays().toLong())
        val last = state.lastSyncAt()
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
        state.saveSync(start, report.summary())
        return report
    }

    private fun parse(m: FetchedMail, readPdf: Boolean): List<IncomingMessage> {
        val out = ArrayList<IncomingMessage>(1)
        registry.parse(m.text, m.from, m.receivedAt, Source.EMAIL)?.let { out += IncomingMessage(it, m.id, m.text) }
        if (readPdf) {
            for ((index, bytes) in m.pdfs.withIndex()) {
                val text = pdf!!.text(bytes) ?: continue
                // A statement is many transactions: feed it to the same parser one line at a time.
                text.lineSequence().map { it.trim() }.filter { it.length > MIN_LINE }.forEachIndexed { line, body ->
                    registry.parse(body, m.from, m.receivedAt, Source.EMAIL)?.let { out += IncomingMessage(it, "${m.id}#pdf$index:$line", body) }
                }
            }
        }
        return out
    }

    private companion object {
        val OVERLAP_MS = TimeUnit.DAYS.toMillis(1)
        const val MIN_LINE = 20
    }
}
