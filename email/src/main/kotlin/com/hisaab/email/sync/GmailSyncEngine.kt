package com.hisaab.email.sync

import com.hisaab.email.api.GmailApi
import com.hisaab.email.api.GmailMessage
import com.hisaab.email.api.HistoryExpiredException
import com.hisaab.email.mime.EmailContent
import com.hisaab.email.mime.MimeParser
import com.hisaab.email.statement.StatementHandler
import com.hisaab.email.statement.StatementMeta
import com.hisaab.parser.bank.LoanStatus
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.parser.registry.SenderKeys
import com.hisaab.shared.db.ProcessedEmailEntity
import com.hisaab.shared.repo.IncomingMessage
import com.hisaab.shared.repo.IngestReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Where parsed emails go. The Room-backed implementation wraps TransactionRepository. */
interface EmailSink {
    suspend fun alreadyProcessed(ids: List<String>): Set<String>
    suspend fun store(messages: List<IncomingMessage>, processed: List<ProcessedEmailEntity>): IngestReport

    /** A lender's email that is not a payment: disbursal, outstanding amount or EMI reminder. */
    suspend fun loanStatus(status: LoanStatus) {}
}

enum class SyncMode { FULL, INCREMENTAL, FALLBACK_FULL, DISABLED }

data class SyncReport(
    val mode: SyncMode,
    val fetched: Int = 0,
    val parsed: Int = 0,
    val ingest: IngestReport = IngestReport.EMPTY,
    val millis: Long = 0,
) {
    fun summary(): String = when (mode) {
        SyncMode.DISABLED -> "Gmail sync is off"
        else -> "${mode.name.lowercase().replace('_', ' ')}: $fetched emails, $parsed transactions " +
            "(${ingest.inserted} new, ${ingest.merged} merged, ${ingest.flagged} to review) in ${millis / 1000.0}s"
    }
}

/**
 * First sync: messages.list with a server-side `from:(...) newer_than:Nd` filter, bodies fetched
 * 8 at a time. Later syncs: users.history.list from the stored historyId, so only new mail is read.
 * When Gmail has dropped that history (404), falls back to a full sync bounded by the time since the
 * last successful sync.
 */
class GmailSyncEngine(
    private val api: GmailApi,
    private val state: SyncStateStore,
    private val sink: EmailSink,
    private val registry: ParserRegistry,
    /** Reads statement PDFs (card, bank, CAS); null skips attachments. */
    private val pdf: StatementHandler?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [onProgress] gets the running count of emails fetched, after each page. */
    suspend fun sync(onProgress: suspend (Int) -> Unit = {}): SyncReport {
        val s = state.read()
        if (!s.enabled) return SyncReport(SyncMode.DISABLED)
        val start = clock()
        val report = when (val historyId = s.historyId) {
            null -> fullSync(s, s.lookbackDays, SyncMode.FULL, onProgress)
            else -> try {
                incrementalSync(s, historyId, onProgress)
            } catch (_: HistoryExpiredException) {
                val days = s.lastSyncAt?.let { TimeUnit.MILLISECONDS.toDays(clock() - it).toInt() + 1 } ?: s.lookbackDays
                fullSync(s, days.coerceIn(1, s.lookbackDays), SyncMode.FALLBACK_FULL, onProgress)
            }
        }
        val done = report.copy(millis = clock() - start)
        return done
    }

    private suspend fun fullSync(s: GmailSettings, days: Int, mode: SyncMode, onProgress: suspend (Int) -> Unit): SyncReport {
        // Taken before listing, so mail arriving mid-sync is picked up by the next incremental sync.
        val checkpoint = api.profile().historyId
        val query = GmailQuery.build(s.senders, days)
        var page: String? = null
        var total = SyncReport(mode)
        do {
            val response = api.listMessages(query, page, PAGE_SIZE)
            total = total.plus(process(response.messages.map { it.id }, s, filterBySender = false))
            onProgress(total.fetched)
            page = response.nextPageToken
        } while (page != null)
        state.saveCheckpoint(checkpoint, clock(), total.summary())
        return total
    }

    private suspend fun incrementalSync(s: GmailSettings, startHistoryId: String, onProgress: suspend (Int) -> Unit): SyncReport {
        val ids = LinkedHashSet<String>()
        var latest = startHistoryId
        var page: String? = null
        do {
            val response = api.history(startHistoryId, page)
            response.history.forEach { h -> h.messagesAdded.forEach { ids += it.message.id } }
            latest = response.historyId ?: latest
            page = response.nextPageToken
        } while (page != null)
        // History lists all new mail, so the sender whitelist is applied here instead of in a query.
        val report = process(ids.toList(), s, filterBySender = true).copy(mode = SyncMode.INCREMENTAL)
        onProgress(report.fetched)
        state.saveCheckpoint(latest, clock(), report.summary())
        return report
    }

    private suspend fun process(ids: List<String>, s: GmailSettings, filterBySender: Boolean): SyncReport {
        if (ids.isEmpty()) return SyncReport(SyncMode.FULL)
        val done = sink.alreadyProcessed(ids)
        val fresh = ids.filter { it !in done }
        if (fresh.isEmpty()) return SyncReport(SyncMode.FULL)

        val gate = Semaphore(MAX_CONCURRENT_FETCHES)
        val messages: List<GmailMessage> = coroutineScope {
            fresh.map { id -> async { gate.withPermit { api.message(id) } } }.awaitAll()
        }

        val allow = SenderAllowList(s.senders)
        val now = clock()
        val incoming = ArrayList<IncomingMessage>()
        val processed = ArrayList<ProcessedEmailEntity>(messages.size)
        withContext(Dispatchers.Default) {
            for (m in messages) {
                val content = MimeParser.extract(m)
                if (filterBySender && !allow.allows(content.from, content.subject)) {
                    processed += ProcessedEmailEntity(m.id, now, OUTCOME_SKIPPED, null)
                    continue
                }
                val found = parseEmail(m.id, content, true)
                incoming += found
                processed += ProcessedEmailEntity(m.id, now, if (found.isEmpty()) OUTCOME_REJECTED else OUTCOME_PARSED, null)
            }
        }
        val ingest = sink.store(incoming, processed)
        return SyncReport(SyncMode.FULL, fetched = messages.size, parsed = incoming.size, ingest = ingest)
    }

    private suspend fun parseEmail(messageId: String, content: EmailContent, readPdf: Boolean): List<IncomingMessage> {
        val out = ArrayList<IncomingMessage>(1)
        registry.parse(content.text, content.from, content.receivedAt, Source.EMAIL)?.let {
            out += IncomingMessage(it, messageId, content.text, subject = content.subject)
        }
        if (out.isEmpty()) registry.loanStatus(content.text, content.from, content.receivedAt, Source.EMAIL)?.let { sink.loanStatus(it) }
        // Not a bank alert: maybe a mutual fund purchase confirmation (SIP instalment, units allotted).
        if (out.isEmpty() && pdf != null) out += pdf.readEmail(messageId, content.from, content.subject, content.text, content.receivedAt)
        if (readPdf && pdf != null) {
            for ((index, attachment) in content.pdfAttachments.withIndex()) {
                val data = attachment.inlineData ?: attachment.attachmentId?.let { api.attachment(messageId, it).data } ?: continue
                out += pdf.read(
                    MimeParser.decodeBase64Url(data),
                    StatementMeta("EMAIL", "$messageId#pdf$index", content.from, content.subject, attachment.filename, content.receivedAt, content.text),
                )
            }
        }
        return out
    }

    private fun SyncReport.plus(o: SyncReport) = copy(fetched = fetched + o.fetched, parsed = parsed + o.parsed, ingest = ingest + o.ingest)

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_CONCURRENT_FETCHES = 8
        const val OUTCOME_PARSED = "PARSED"
        const val OUTCOME_REJECTED = "REJECTED"
        const val OUTCOME_SKIPPED = "SKIPPED"
    }
}

object GmailQuery {
    /**
     * `{from:(a OR b) subject:"..."} newer_than:Nd`: the allowed senders, or a statement subject from anyone.
     * Gmail matches a bare domain in from: against any address at it.
     */
    fun build(senders: List<String>, days: Int): String {
        val from = senders.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" OR ")
        val subjects = com.hisaab.parser.registry.ParserRegistry.STATEMENT_SUBJECTS.joinToString(" ") { "subject:\"$it\"" }
        return "{from:($from) $subjects} newer_than:${days}d"
    }
}

/** Matches a From header against addresses and domains, including subdomains (alerts.sbi.co.in for sbi.co.in). */
class SenderAllowList(senders: List<String>) {
    private val entries = senders.map { it.trim().lowercase() }.toHashSet()
    fun allows(from: String, subject: String? = null): Boolean =
        SenderKeys.candidates(from).any { it in entries } || com.hisaab.parser.registry.ParserRegistry.isStatementSubject(subject)
}
