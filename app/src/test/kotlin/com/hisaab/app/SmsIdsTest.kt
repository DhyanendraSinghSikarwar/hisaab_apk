package com.hisaab.app

import com.hisaab.app.sms.InboxSms
import com.hisaab.app.sms.SmsIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SmsIdsTest {
    @Test
    fun `the inbox scan and the live receiver give one SMS the same id`() {
        val body = "Rs.250.00 debited from A/c XX1234"
        val fromInbox = InboxSms(sender = "VM-HDFCBK", body = body, receivedAt = 1_000_500, sentAt = 1_000_000).messageId
        val fromReceiver = SmsIds.of("vm-hdfcbk", 1_000_000, body)
        assertEquals(fromInbox, fromReceiver)
    }

    @Test
    fun `different messages get different ids`() {
        assertNotEquals(SmsIds.of("VM-HDFCBK", 1, "a"), SmsIds.of("VM-HDFCBK", 1, "b"))
        assertNotEquals(SmsIds.of("VM-HDFCBK", 1, "a"), SmsIds.of("VM-HDFCBK", 2, "a"))
    }
}
