package com.hisaab.email

import com.hisaab.email.api.GmailMessage
import com.hisaab.email.api.Header
import com.hisaab.email.api.MessagePart
import com.hisaab.email.api.MessagePartBody
import com.hisaab.email.mime.MimeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class MimeParserTest {
    private fun b64(s: String, charset: java.nio.charset.Charset = Charsets.UTF_8) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(charset))

    private fun message(payload: MessagePart) = GmailMessage(id = "m1", internalDate = "1790000000000", payload = payload)

    @Test
    fun `prefers text plain over html`() {
        val m = message(
            MessagePart(
                mimeType = "multipart/alternative",
                headers = listOf(Header("From", "HDFC Bank <alerts@hdfcbank.net>"), Header("Subject", "Alert")),
                parts = listOf(
                    MessagePart(mimeType = "text/plain", body = MessagePartBody(data = b64("Rs.450.00 has been debited from account **1234 to VPA a@ybl"))),
                    MessagePart(mimeType = "text/html", body = MessagePartBody(data = b64("<p>HTML version</p>"))),
                ),
            ),
        )
        val c = MimeParser.extract(m)
        assertEquals("Rs.450.00 has been debited from account **1234 to VPA a@ybl", c.text)
        assertEquals("HDFC Bank <alerts@hdfcbank.net>", c.from)
        assertEquals(1790000000000L, c.receivedAt)
    }

    @Test
    fun `html only body is converted to text with block line breaks and no styles`() {
        val html = "<html><head><style>.x{color:red}</style></head><body><table><tr><td>Amount</td><td>Rs.450.00</td></tr>" +
            "<tr><td>Account</td><td>XX1234</td></tr></table><p>Dear&nbsp;Customer,<br>debited</p><script>alert(1)</script></body></html>"
        val c = MimeParser.extract(message(MessagePart(mimeType = "text/html", body = MessagePartBody(data = b64(html)))))
        assertTrue(c.text, c.text.contains("Amount Rs.450.00"))
        assertTrue(c.text.contains("Account XX1234"))
        assertTrue(c.text.contains("\n"))
        assertFalse(c.text.contains("color:red"))
        assertFalse(c.text.contains("alert(1)"))
    }

    @Test
    fun `nested multipart and pdf attachments are found`() {
        val m = message(
            MessagePart(
                mimeType = "multipart/mixed",
                parts = listOf(
                    MessagePart(mimeType = "multipart/alternative", parts = listOf(
                        MessagePart(mimeType = "text/html", body = MessagePartBody(data = b64("<div>Your statement for September is attached for your records.</div>"))),
                    )),
                    MessagePart(mimeType = "application/pdf", filename = "Statement.pdf", body = MessagePartBody(attachmentId = "att-1", size = 2048)),
                    MessagePart(mimeType = "image/png", filename = "logo.png", body = MessagePartBody(attachmentId = "att-2")),
                ),
            ),
        )
        val c = MimeParser.extract(m)
        assertEquals(listOf("att-1"), c.pdfAttachments.map { it.attachmentId })
        assertTrue(c.text.contains("statement"))
    }

    @Test
    fun `declared charset is honoured`() {
        val text = "Montant débité: Rs.100.00 from account XX1234 for testing charsets"
        val part = MessagePart(
            mimeType = "text/plain",
            headers = listOf(Header("Content-Type", "text/plain; charset=\"ISO-8859-1\"")),
            body = MessagePartBody(data = b64(text, Charsets.ISO_8859_1)),
        )
        assertEquals(text, MimeParser.extract(message(part)).text)
    }

    @Test
    fun `base64url with and without padding decodes`() {
        assertEquals("ab?", String(MimeParser.decodeBase64Url("YWI_")))
        assertEquals("a", String(MimeParser.decodeBase64Url("YQ")))
        assertEquals("a", String(MimeParser.decodeBase64Url("YQ==")))
    }
}
