package com.hisaab.email

import com.hisaab.email.imap.FetchedMail
import com.hisaab.email.imap.ImapSyncEngine
import com.hisaab.email.imap.ImapSyncState
import com.hisaab.email.imap.MailAuthException
import com.hisaab.email.imap.MailClient
import com.hisaab.email.imap.MailLogin
import com.hisaab.email.imap.MailServers
import com.hisaab.email.imap.VerificationCode
import com.hisaab.email.sync.EmailSink
import com.hisaab.email.sync.SyncMode
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.ProcessedEmailEntity
import com.hisaab.shared.repo.IncomingMessage
import com.hisaab.shared.repo.IngestReport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ImapSyncEngineTest {
    private val registry = ParserRegistry.default()
    private val now = 1_790_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)
    private val login = MailLogin("me@gmail.com", "app-password", MailServers.forAddress("me@gmail.com"))

    private class FakeState(var last: Long? = null, val days: Int = 30, val login: MailLogin?, val readPdf: Boolean = false) : ImapSyncState {
        var saved: String? = null
        override suspend fun logins() = listOfNotNull(login)
        override suspend fun lookbackDays() = days
        override suspend fun senders() = listOf("hdfcbank.net")
        override suspend fun readPdfStatements() = readPdf
        override suspend fun enabled() = true
        override suspend fun lastSyncAt(email: String) = last
        override suspend fun saveSync(email: String, at: Long, result: String) { last = at; saved = result }
    }

    private class FakeSink : EmailSink {
        val done = mutableSetOf<String>()
        val stored = mutableListOf<IncomingMessage>()
        override suspend fun alreadyProcessed(ids: List<String>) = ids.filter { it in done }.toSet()
        override suspend fun store(messages: List<IncomingMessage>, processed: List<ProcessedEmailEntity>): IngestReport {
            stored += messages
            done += processed.map { it.messageId }
            return IngestReport(messages.size, 0, 0, 0)
        }
    }

    private inner class FakeClient(val mails: List<FetchedMail>) : MailClient {
        var since: Long? = null
        var askedForPdfs: Boolean? = null
        override suspend fun checkLogin(login: MailLogin) = Unit
        override suspend fun sendToSelf(login: MailLogin, subject: String, body: String) = Unit
        override suspend fun fetchSince(login: MailLogin, since: Long, accept: (String) -> Boolean, withPdfs: Boolean): List<FetchedMail> {
            this.since = since
            askedForPdfs = withPdfs
            return mails.filter { it.receivedAt >= since && accept(it.from) }
        }
    }

    private fun alert(n: Int, from: String = "HDFC Bank <alerts@hdfcbank.net>", at: Long = now - day) = FetchedMail(
        id = "imap:<m$n@hdfcbank.net>", from = from, subject = "Alert",
        text = "Dear Customer, Rs.${100 + n}.00 has been debited from account **1234 to VPA shop$n@ybl SHOP on 23-09-26. " +
            "Your UPI transaction reference number is 5268${"%08d".format(n)}.",
        receivedAt = at, pdfs = emptyList(),
    )

    @Test
    fun `first sync reads the look-back window and parses bank alerts only`() = runBlocking {
        val client = FakeClient(listOf(alert(1), alert(2), alert(3, from = "Shop <deals@shop.com>")))
        val sink = FakeSink()
        val state = FakeState(login = login)
        val report = ImapSyncEngine(client, state, sink, registry, pdf = null, clock = { now }).sync()
        assertEquals(SyncMode.FULL, report.mode)
        assertEquals(now - 30 * day, client.since)
        assertEquals(2, sink.stored.size)
        assertEquals(now, state.last)
    }

    @Test
    fun `later syncs overlap a day and never parse the same email twice`() = runBlocking {
        val client = FakeClient(listOf(alert(1), alert(2)))
        val sink = FakeSink()
        val state = FakeState(login = login)
        val engine = ImapSyncEngine(client, state, sink, registry, pdf = null, clock = { now })
        engine.sync()
        val again = engine.sync()
        assertEquals(SyncMode.INCREMENTAL, again.mode)
        assertEquals(now - day, client.since)
        assertEquals(0, again.parsed)
        assertEquals(2, sink.stored.size)
    }

    @Test
    fun `statement PDFs attached to mail reach the statement reader`() = runBlocking {
        val statementMail = FetchedMail(
            id = "imap:<stmt1@hdfcbank.net>", from = "HDFC Bank <Emailstatements.cards@hdfcbank.net>", subject = "Your credit card statement",
            text = "Dear Customer, your HDFC Bank Credit Card statement for September 2026 is attached.", receivedAt = now - day,
            pdfs = listOf(byteArrayOf(1, 2, 3)), pdfNames = listOf("4893XXXXXXXX5678_20-09-2026.pdf"),
        )
        val client = FakeClient(listOf(statementMail))
        val seen = mutableListOf<com.hisaab.email.statement.StatementMeta>()
        val handler = com.hisaab.email.statement.StatementHandler { _, meta -> seen += meta; emptyList() }
        ImapSyncEngine(client, FakeState(login = login, readPdf = true), FakeSink(), registry, pdf = handler, clock = { now }).sync()
        assertEquals(true, client.askedForPdfs)
        assertEquals(1, seen.size)
        assertEquals("4893XXXXXXXX5678_20-09-2026.pdf", seen.first().fileName)
        assertEquals("imap:<stmt1@hdfcbank.net>#pdf0", seen.first().key)
    }

    @Test
    fun `default senders include card issuers and investment statement senders`() {
        val senders = registry.defaultEmailSenders
        for (d in listOf("hdfcbank.net", "sbicard.com", "camsonline.com", "kfintech.com", "nsdl.co.in", "cdslstatement.com", "indmoney.com", "getonecard.app")) {
            assertTrue(d, d in senders)
        }
    }

    @Test(expected = MailAuthException::class)
    fun `a missing login asks the user to sign in again`() {
        runBlocking { ImapSyncEngine(FakeClient(emptyList()), FakeState(login = null), FakeSink(), registry, pdf = null).sync() }
    }

    @Test
    fun `providers resolve to their servers`() {
        assertEquals("imap.gmail.com", MailServers.forAddress("A.B@Gmail.com").imapHost)
        assertEquals("outlook.office365.com", MailServers.forAddress("x@hotmail.com").imapHost)
        assertEquals("imap.mail.yahoo.com", MailServers.forAddress("x@yahoo.co.in").imapHost)
        assertEquals("imap.example.org", MailServers.forAddress("x@example.org").imapHost)
        assertTrue(MailServers.isValidAddress("name.surname+bank@gmail.com"))
        assertFalse(MailServers.isValidAddress("not an email"))
    }

    @Test
    fun `verification code accepts the issued code once`() {
        val code = VerificationCode(clock = { now })
        val issued = code.issue("me@gmail.com")
        assertEquals(6, issued.length)
        assertEquals(VerificationCode.Result.WRONG, code.check("me@gmail.com", if (issued == "123456") "654321" else "123456"))
        assertEquals(VerificationCode.Result.OK, code.check("me@gmail.com", issued))
        assertEquals(VerificationCode.Result.NONE, code.check("me@gmail.com", issued))
    }

    @Test
    fun `verification code expires and limits attempts`() {
        var t = now
        val code = VerificationCode(clock = { t }, maxAttempts = 2)
        val issued = code.issue("me@gmail.com")
        val wrong = if (issued == "111111") "222222" else "111111"
        assertEquals(VerificationCode.Result.WRONG, code.check("me@gmail.com", wrong))
        assertEquals(VerificationCode.Result.WRONG, code.check("me@gmail.com", wrong))
        assertEquals(VerificationCode.Result.TOO_MANY_ATTEMPTS, code.check("me@gmail.com", issued))
        val second = code.issue("me@gmail.com")
        assertNotEquals(VerificationCode.Result.OK, code.check("other@gmail.com", second))
        t += TimeUnit.MINUTES.toMillis(11)
        assertEquals(VerificationCode.Result.EXPIRED, code.check("me@gmail.com", second))
    }
}
