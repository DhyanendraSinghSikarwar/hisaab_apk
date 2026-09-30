package com.hisaab.app.sms

import android.content.Context
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject

data class InboxSms(val sender: String, val body: String, val receivedAt: Long, val sentAt: Long) {
    /** The same id the real-time receiver computes, so one SMS is never ingested twice. */
    val messageId: String get() = SmsIds.of(sender, sentAt.takeIf { it > 0 } ?: receivedAt, body)
}

interface SmsInboxSource {
    /**
     * Reads inbox messages received after [since], oldest first, in batches of [batchSize].
     * [accept] sees only the sender, so bodies of non-bank SMS are never copied out of the cursor.
     * Returns how many inbox rows were examined.
     */
    suspend fun readBatches(since: Long, batchSize: Int, accept: (String) -> Boolean, onBatch: suspend (List<InboxSms>) -> Unit): Int
}

class TelephonySmsInboxSource @Inject constructor(@ApplicationContext private val context: Context) : SmsInboxSource {
    override suspend fun readBatches(
        since: Long, batchSize: Int, accept: (String) -> Boolean, onBatch: suspend (List<InboxSms>) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT)
        var examined = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val dateSent = c.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
            var batch = ArrayList<InboxSms>(batchSize)
            while (c.moveToNext()) {
                examined++
                val sender = c.getString(address) ?: continue
                if (!accept(sender)) continue
                batch += InboxSms(sender, c.getString(body).orEmpty(), c.getLong(date), c.getLong(dateSent))
                if (batch.size == batchSize) {
                    onBatch(batch)
                    batch = ArrayList(batchSize)
                }
            }
            if (batch.isNotEmpty()) onBatch(batch)
        }
        examined
    }
}

object SmsIds {
    fun of(sender: String, sentAt: Long, body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("${sender.uppercase()}|$sentAt|$body".toByteArray())
        return "sms:" + digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
