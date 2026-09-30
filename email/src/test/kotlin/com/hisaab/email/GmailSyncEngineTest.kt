package com.hisaab.email

import com.hisaab.email.api.AccessTokenProvider
import com.hisaab.email.api.GmailApi
import com.hisaab.email.sync.EmailSink
import com.hisaab.email.sync.GmailQuery
import com.hisaab.email.sync.GmailSettings
import com.hisaab.email.sync.GmailSyncEngine
import com.hisaab.email.sync.SenderAllowList
import com.hisaab.email.sync.SyncMode
import com.hisaab.email.sync.SyncStateStore
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.ProcessedEmailEntity
import com.hisaab.shared.repo.IncomingMessage
import com.hisaab.shared.repo.IngestReport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class GmailSyncEngineTest {
    private val registry = ParserRegistry.default()
    private val now = 1_790_000_000_000L

    private class FakeState(var s: GmailSettings) : SyncStateStore {
        var saved: String? = null
        override suspend fun read() = s
        override suspend fun saveCheckpoint(historyId: String, at: Long, result: String) { saved = historyId; s = s.copy(historyId = historyId, lastSyncAt = at) }
        override suspend fun setNeedsReauth(value: Boolean) { s = s.copy(needsReauth = value) }
    }

    private class FakeSink(val done: MutableSet<String> = mutableSetOf()) : EmailSink {
        val stored = mutableListOf<IncomingMessage>()
        override suspend fun alreadyProcessed(ids: List<String>) = ids.filter { it in done }.toSet()
        override suspend fun store(messages: List<IncomingMessage>, processed: List<ProcessedEmailEntity>): IngestReport {
            stored += messages
            done += processed.map { it.messageId }
            return IngestReport(messages.size, 0, 0, 0)
        }
    }

    private fun settings(historyId: String? = null, lastSync: Long? = null) = GmailSettings(
        enabled = true, accountEmail = "me@gmail.com", lookbackDays = 90, senders = listOf("hdfcbank.net", "icicibank.com"),
        readPdfStatements = false, historyId = historyId, lastSyncAt = lastSync, needsReauth = false, lastResult = null,
    )

    private fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    /** A Gmail message whose body is a unique HDFC debit alert. */
    private fun messageJson(id: String, from: String = "HDFC Bank <alerts@hdfcbank.net>"): String {
        val n = id.filter { it.isDigit() }.ifEmpty { "0" }.toInt()
        val body = "Dear Customer, Rs.${100 + n}.00 has been debited from account **1234 to VPA shop$n@ybl SHOP on 25-09-26. " +
            "Your UPI transaction reference number is 5268${"%08d".format(n)}."
        return """{"id":"$id","internalDate":"$now","payload":{"mimeType":"text/plain","headers":[{"name":"From","value":"$from"}],
            "body":{"data":"${b64(body)}"}}}"""
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun engine(state: FakeState, sink: FakeSink, handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData): GmailSyncEngine {
        val client = HttpClient(MockEngine { handler(it) }) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val tokens = object : AccessTokenProvider { override suspend fun token(forceRefresh: Boolean) = if (forceRefresh) "fresh" else "stale" }
        return GmailSyncEngine(GmailApi(client, tokens, "https://gmail.test/me"), state, sink, registry, pdf = null, clock = { now })
    }

    @Test
    fun `query lists senders and lookback`() {
        assertEquals("from:(alerts@hdfcbank.net OR icicibank.com) newer_than:90d", GmailQuery.build(listOf("alerts@hdfcbank.net", "icicibank.com", " "), 90))
    }

    @Test
    fun `allow list matches addresses, domains and subdomains`() {
        val allow = SenderAllowList(listOf("sbi.co.in", "alerts@hdfcbank.net"))
        assertTrue(allow.allows("SBI <donotreply@alerts.sbi.co.in>"))
        assertTrue(allow.allows("alerts@hdfcbank.net"))
        assertTrue(!allow.allows("offers@hdfcbank.net"))
        assertTrue(!allow.allows("x@evil.com"))
    }

    @Test
    fun `first sync pages through messages with at most 8 fetches in flight`() = runBlocking {
        val state = FakeState(settings())
        val sink = FakeSink()
        val inFlight = AtomicInteger()
        var peak = 0
        var query: String? = null
        val engine = engine(state, sink) { req ->
            val path = req.url.encodedPath
            when {
                path.endsWith("/profile") -> json("""{"historyId":"5000"}""")
                path.endsWith("/messages") -> {
                    query = req.url.parameters["q"]
                    assertEquals("100", req.url.parameters["maxResults"])
                    val page = req.url.parameters["pageToken"]
                    val ids = if (page == null) (1..100) else (101..150)
                    val next = if (page == null) ""","nextPageToken":"p2"""" else ""
                    json("""{"messages":[${ids.joinToString(",") { """{"id":"m$it"}""" }}]$next}""")
                }
                path.contains("/messages/") -> {
                    val now = inFlight.incrementAndGet(); synchronized(this@GmailSyncEngineTest) { peak = maxOf(peak, now) }
                    delay(5)
                    inFlight.decrementAndGet()
                    json(messageJson(path.substringAfterLast('/')))
                }
                else -> error("unexpected $path")
            }
        }
        val report = engine.sync()
        assertEquals(SyncMode.FULL, report.mode)
        assertEquals(150, report.fetched)
        assertEquals(150, sink.stored.size)
        assertEquals("from:(hdfcbank.net OR icicibank.com) newer_than:90d", query)
        assertTrue("peak concurrency $peak", peak in 2..8)
        assertEquals("5000", state.saved)
    }

    @Test
    fun `incremental sync reads only history and skips non-bank senders`() = runBlocking {
        val state = FakeState(settings(historyId = "5000", lastSync = now - 3_600_000))
        val sink = FakeSink()
        var listed = false
        val engine = engine(state, sink) { req ->
            val path = req.url.encodedPath
            when {
                path.endsWith("/history") -> {
                    assertEquals("5000", req.url.parameters["startHistoryId"])
                    json("""{"historyId":"5010","history":[{"id":"5005","messagesAdded":[{"message":{"id":"m7"}},{"message":{"id":"m8"}}]}]}""")
                }
                path.endsWith("/messages/m7") -> json(messageJson("m7"))
                path.endsWith("/messages/m8") -> json(messageJson("m8", from = "Newsletter <news@shop.com>"))
                path.endsWith("/messages") -> { listed = true; json("{}") }
                else -> error("unexpected $path")
            }
        }
        val report = engine.sync()
        assertEquals(SyncMode.INCREMENTAL, report.mode)
        assertEquals(1, sink.stored.size)
        assertEquals(setOf("m7", "m8"), sink.done) // the newsletter is marked processed too
        assertEquals("5010", state.saved)
        assertTrue(!listed)
    }

    @Test
    fun `expired history id falls back to a date-bounded full sync`() = runBlocking {
        val state = FakeState(settings(historyId = "1", lastSync = now - 3 * 86_400_000L))
        val sink = FakeSink()
        var query: String? = null
        val engine = engine(state, sink) { req ->
            val path = req.url.encodedPath
            when {
                path.endsWith("/history") -> json("""{"error":{"code":404}}""", HttpStatusCode.NotFound)
                path.endsWith("/profile") -> json("""{"historyId":"9000"}""")
                path.endsWith("/messages") -> { query = req.url.parameters["q"]; json("""{"messages":[{"id":"m1"}]}""") }
                path.contains("/messages/") -> json(messageJson("m1"))
                else -> error("unexpected $path")
            }
        }
        val report = engine.sync()
        assertEquals(SyncMode.FALLBACK_FULL, report.mode)
        assertTrue(query!!.endsWith("newer_than:4d"))
        assertEquals("9000", state.saved)
    }

    @Test
    fun `emails already processed are never fetched again`() = runBlocking {
        val state = FakeState(settings())
        val sink = FakeSink(mutableSetOf("m1", "m2"))
        val fetched = mutableListOf<String>()
        val engine = engine(state, sink) { req ->
            val path = req.url.encodedPath
            when {
                path.endsWith("/profile") -> json("""{"historyId":"1"}""")
                path.endsWith("/messages") -> json("""{"messages":[{"id":"m1"},{"id":"m2"},{"id":"m3"}]}""")
                path.contains("/messages/") -> { fetched += path.substringAfterLast('/'); json(messageJson(path.substringAfterLast('/'))) }
                else -> error("unexpected $path")
            }
        }
        engine.sync()
        assertEquals(listOf("m3"), fetched)
    }

    @Test
    fun `a 401 refreshes the token once and retries`() = runBlocking {
        val state = FakeState(settings())
        val tokensSeen = mutableListOf<String>()
        val engine = engine(state, FakeSink()) { req ->
            val auth = req.headers[HttpHeaders.Authorization].orEmpty()
            val path = req.url.encodedPath
            when {
                path.endsWith("/profile") -> {
                    tokensSeen += auth
                    if (auth == "Bearer stale") json("{}", HttpStatusCode.Unauthorized) else json("""{"historyId":"1"}""")
                }
                path.endsWith("/messages") -> json("""{"messages":[]}""")
                else -> error("unexpected $path")
            }
        }
        engine.sync()
        assertEquals(listOf("Bearer stale", "Bearer fresh"), tokensSeen)
    }
}
