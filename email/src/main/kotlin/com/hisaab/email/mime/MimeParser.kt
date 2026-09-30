package com.hisaab.email.mime

import com.hisaab.email.api.GmailMessage
import com.hisaab.email.api.MessagePart
import org.jsoup.Jsoup
import java.nio.charset.Charset
import java.util.Base64

data class PdfAttachment(val filename: String, val attachmentId: String?, val inlineData: String?)

data class EmailContent(
    val from: String,
    val subject: String?,
    /** Readable body: the text/plain part, or the text/html part turned into text. */
    val text: String,
    val receivedAt: Long,
    val pdfAttachments: List<PdfAttachment>,
)

/** Decodes a Gmail `format=full` message: walks the MIME tree, base64url-decodes, and prefers text/plain. */
object MimeParser {
    private val CHARSET = Regex("""charset="?([\w.:-]+)"?""", RegexOption.IGNORE_CASE)
    private const val MIN_PLAIN_LENGTH = 30

    fun extract(message: GmailMessage): EmailContent {
        val payload = message.payload
        val headers = payload?.headers.orEmpty()
        fun header(name: String) = headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

        var plain: String? = null
        var html: String? = null
        val pdfs = ArrayList<PdfAttachment>()
        payload?.let { root ->
            walk(root) { part ->
                val type = part.mimeType?.lowercase().orEmpty()
                val name = part.filename.orEmpty()
                when {
                    name.isNotEmpty() && (type == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) ->
                        pdfs += PdfAttachment(name, part.body?.attachmentId, part.body?.data)
                    name.isNotEmpty() -> Unit // other attachments are ignored
                    type == "text/plain" && plain == null -> plain = decodeText(part)
                    type == "text/html" && html == null -> html = decodeText(part)
                }
            }
        }
        val body = plain?.takeIf { it.trim().length >= MIN_PLAIN_LENGTH }
            ?: html?.let(::htmlToText)
            ?: plain
            ?: message.snippet.orEmpty()

        return EmailContent(
            from = header("From").orEmpty(),
            subject = header("Subject"),
            text = body,
            receivedAt = message.internalDate?.toLongOrNull() ?: 0L,
            pdfAttachments = pdfs,
        )
    }

    private fun walk(part: MessagePart, visit: (MessagePart) -> Unit) {
        visit(part)
        for (child in part.parts) walk(child, visit)
    }

    private fun decodeText(part: MessagePart): String? {
        val data = part.body?.data ?: return null
        val contentType = part.headers.firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }?.value.orEmpty()
        val charset = CHARSET.find(contentType)?.groupValues?.get(1)
            ?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(decodeBase64Url(data), charset)
    }

    /** Gmail uses URL-safe base64, usually without padding. */
    fun decodeBase64Url(data: String): ByteArray = Base64.getUrlDecoder().decode(data.trim().replace("\n", "").replace("\r", ""))

    /** HTML to text, keeping one line per block so the parser still sees field boundaries. */
    fun htmlToText(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, head").remove()
        doc.select("br").after(LINE)
        doc.select("p, div, tr, li, h1, h2, h3, h4, h5, h6, table").before(LINE)
        doc.select("td, th").after(" ")
        return doc.text().replace(LINE, "\n")
            .lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{2,}"), "\n")
            .trim()
    }

    // A marker Jsoup's text() keeps, swapped back to a newline afterwards.
    private const val LINE = " "
}
