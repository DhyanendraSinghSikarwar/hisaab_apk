package com.hisaab.parser.perf

import com.hisaab.parser.corpus.AllCorpora
import com.hisaab.parser.corpus.Fixture
import com.hisaab.parser.registry.ParserRegistry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Throughput on the build machine's JVM, single-threaded. A mid-range phone runs this code roughly
 * 3-5x slower, so 150 µs here is about 0.5-0.75 ms on a device: inside the 1 ms per-message target.
 * The SMS worker also parses batches in parallel on Dispatchers.Default, which is what keeps
 * 10,000 messages under 5 s on a phone.
 * Run with ./gradlew :parser-core:perfTest
 */
@Tag("perf")
class ParserPerformanceTest {
    private val registry = ParserRegistry.default()
    private val corpus = AllCorpora.samples

    @Test
    fun `parses 10,000 messages well inside the budget`() {
        val messages = List(10_000) { corpus[it % corpus.size] }
        repeat(3) { for (m in messages) registry.parse(m.body, m.sender, Fixture.RECEIVED_AT, m.source) } // JIT warm-up

        val start = System.nanoTime()
        var parsed = 0
        for (m in messages) if (registry.parse(m.body, m.sender, Fixture.RECEIVED_AT, m.source) != null) parsed++
        val ms = (System.nanoTime() - start) / 1_000_000.0
        val perMessageUs = ms * 1000 / messages.size
        println("10,000 messages in ${"%.1f".format(ms)} ms (${"%.1f".format(perMessageUs)} µs/message, $parsed parsed)")
        assertTrue(ms < 1_500, "took $ms ms")
        assertTrue(perMessageUs < 150, "took $perMessageUs µs per message")
    }

    @Test
    fun `sender pre-filter is effectively free`() {
        val senders = List(100_000) { if (it % 2 == 0) "VM-HDFCBK" else "VM-AMAZON" }
        repeat(2) { senders.forEach(registry::isKnownSender) }
        val start = System.nanoTime()
        senders.forEach(registry::isKnownSender)
        val ns = (System.nanoTime() - start) / senders.size
        println("isKnownSender: $ns ns/call")
        assertTrue(ns < 5_000)
    }
}
