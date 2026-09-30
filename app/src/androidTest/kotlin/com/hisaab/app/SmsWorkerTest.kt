package com.hisaab.app

import android.Manifest
import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.sms.InboxSms
import com.hisaab.app.sms.OptimizedSmsReaderWorker
import com.hisaab.app.sms.SmsInboxSource
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.shared.db.HisaabDatabase
import com.hisaab.shared.repo.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real worker against a fake inbox and an in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class SmsWorkerTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: HisaabDatabase
    private val registry = ParserRegistry.default()
    private var notified = 0

    private class FakeInbox(val messages: List<InboxSms>) : SmsInboxSource {
        var bodiesRead = 0
        override suspend fun readBatches(since: Long, batchSize: Int, accept: (String) -> Boolean, onBatch: suspend (List<InboxSms>) -> Unit): Int {
            messages.filter { it.receivedAt > since && accept(it.sender) }.also { bodiesRead += it.size }.chunked(batchSize).forEach { onBatch(it) }
            return messages.size
        }
    }

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
        db = Room.inMemoryDatabaseBuilder(context, HisaabDatabase::class.java).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        runBlocking { AppSettingsStore(context).resetSmsCursor() }
    }

    @After fun tearDown() { db.close() }

    private fun worker(inbox: SmsInboxSource, full: Boolean = true): OptimizedSmsReaderWorker {
        val repo = TransactionRepository(db, registry)
        val settings = AppSettingsStore(context)
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                OptimizedSmsReaderWorker(appContext, workerParameters, inbox, registry, repo, settings) { notified++ }
        }
        return TestListenableWorkerBuilder<OptimizedSmsReaderWorker>(context).setWorkerFactory(factory)
            .setInputData(workDataOf(OptimizedSmsReaderWorker.KEY_FULL to full)).build()
    }

    private fun bankSms(i: Int) = InboxSms(
        "VM-HDFCBK", "Rs.${100 + i}.00 debited from HDFC Bank A/c XX1234 on 25-09-26 to VPA shop$i@ybl. UPI Ref 5268${"%08d".format(i)}",
        receivedAt = 1_790_000_000_000L + i * 1000, sentAt = 1_790_000_000_000L + i * 1000 - 200,
    )

    @Test
    fun scansBatchesSkipsNonBankSendersAndIsIdempotent() = runBlocking {
        val messages = (0 until 1200).map(::bankSms) + (0 until 300).map { InboxSms("AD-AMAZON", "Your order of Rs.$it shipped", 1_790_000_000_000L + it, 0) }
        val inbox = FakeInbox(messages)

        val result = worker(inbox).doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1200, db.transactions().count())
        assertEquals(1200, inbox.bodiesRead) // the 300 shop messages never reached the parser
        assertEquals(1, notified)

        // A second full scan finds everything already stored.
        worker(inbox).doWork()
        assertEquals(1200, db.transactions().count())
    }

    @Test
    fun incrementalScanOnlyReadsNewerMessages() = runBlocking {
        val first = (0 until 10).map(::bankSms)
        worker(FakeInbox(first), full = false).doWork()
        val inbox = FakeInbox(first + (10 until 15).map(::bankSms))
        worker(inbox, full = false).doWork()
        assertEquals(5, inbox.bodiesRead)
        assertEquals(15, db.transactions().count())
    }
}
