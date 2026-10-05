package com.hisaab.email.imap

import com.hisaab.email.mime.MimeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Date
import java.util.Properties
import javax.inject.Inject
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.ContentType
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

/** The provider refused the address and password. */
class MailAuthException(message: String) : IOException(message)

data class MailLogin(val email: String, val password: String, val server: MailServer)

/** One message, decoded. [id] is stable across syncs: the Message-ID header, or the IMAP UID. */
data class FetchedMail(
    val id: String,
    val from: String,
    val subject: String?,
    val text: String,
    val receivedAt: Long,
    val pdfs: List<ByteArray>,
    val pdfNames: List<String> = emptyList(),
)

interface MailClient {
    /** Logs in to IMAP and out again. Throws [MailAuthException] for a wrong password, IOException otherwise. */
    suspend fun checkLogin(login: MailLogin)

    /** Sends a plain email from the account to itself. */
    suspend fun sendToSelf(login: MailLogin, subject: String, body: String)

    /**
     * Inbox messages received on or after [since] (the server compares dates, not times) whose From
     * header passes [accept]. Only those bodies are downloaded; every other message is read as an envelope only.
     */
    suspend fun fetchSince(login: MailLogin, since: Long, accept: (String) -> Boolean, withPdfs: Boolean): List<FetchedMail>
}

/** IMAP over TLS and SMTP with JavaMail. The password is used for the call and never logged. */
class JavaMailClient @Inject constructor() : MailClient {

    override suspend fun checkLogin(login: MailLogin) = io {
        val store = session(login).getStore("imaps")
        store.connect(login.server.imapHost, login.server.imapPort, login.email, login.password)
        store.close()
    }

    override suspend fun sendToSelf(login: MailLogin, subject: String, body: String) = io {
        val s = login.server
        val props = Properties().apply {
            put("mail.smtp.host", s.smtpHost)
            put("mail.smtp.port", s.smtpPort.toString())
            put("mail.smtp.auth", "true")
            if (s.smtpSsl) put("mail.smtp.ssl.enable", "true")
            else { put("mail.smtp.starttls.enable", "true"); put("mail.smtp.starttls.required", "true") }
            put("mail.smtp.ssl.checkserveridentity", "true")
            put("mail.smtp.connectiontimeout", TIMEOUT); put("mail.smtp.timeout", TIMEOUT); put("mail.smtp.writetimeout", TIMEOUT)
        }
        val message = MimeMessage(Session.getInstance(props)).apply {
            setFrom(InternetAddress(login.email, "Hisaab"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(login.email))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
            sentDate = Date()
        }
        Transport.send(message, login.email, login.password)
    }

    override suspend fun fetchSince(login: MailLogin, since: Long, accept: (String) -> Boolean, withPdfs: Boolean): List<FetchedMail> = io {
        val store = session(login).getStore("imaps")
        store.connect(login.server.imapHost, login.server.imapPort, login.email, login.password)
        try {
            // Statements are often archived or filtered under a label; Gmail's "All Mail" holds them all.
            val inbox = allMailFolder(store) ?: store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val found = inbox.search(ReceivedDateTerm(ComparisonTerm.GE, Date(since)))
                // One round trip for every envelope, so the From filter never downloads a body.
                inbox.fetch(found, FetchProfile().apply { add(FetchProfile.Item.ENVELOPE); add(UIDFolder.FetchProfileItem.UID) })
                found.mapNotNull { m ->
                    val from = m.from?.firstOrNull()?.toString().orEmpty()
                    if (!accept(from)) return@mapNotNull null
                    runCatching { decode(inbox, m, from, withPdfs) }.getOrNull()
                }
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
    }

    private fun decode(folder: Folder, m: Message, from: String, withPdfs: Boolean): FetchedMail {
        val id = (m as? MimeMessage)?.messageID?.trim()?.takeIf { it.isNotEmpty() }
            ?: "uid:${(folder as? UIDFolder)?.getUID(m) ?: m.messageNumber}"
        val parts = BodyParts()
        walk(m, parts, withPdfs)
        val text = parts.plain?.takeIf { it.trim().length >= MIN_PLAIN_LENGTH }
            ?: parts.html?.let(MimeParser::htmlToText)
            ?: parts.plain
            ?: ""
        return FetchedMail(
            id = "imap:$id", from = from, subject = m.subject, text = text,
            receivedAt = (m.receivedDate ?: m.sentDate)?.time ?: 0L, pdfs = parts.pdfs, pdfNames = parts.pdfNames,
        )
    }

    private class BodyParts {
        var plain: String? = null
        var html: String? = null
        val pdfs = ArrayList<ByteArray>()
        val pdfNames = ArrayList<String>()
    }

    private fun walk(part: Part, out: BodyParts, withPdfs: Boolean) {
        // Some banks send the name MIME-encoded ("=?UTF-8?B?...?=") with a generic content type.
        val name = part.fileName?.let { runCatching { MimeUtility.decodeText(it) }.getOrDefault(it) }.orEmpty()
        when {
            part.isMimeType("multipart/*") -> {
                val mp = part.content as? Multipart ?: return
                for (i in 0 until mp.count) walk(mp.getBodyPart(i), out, withPdfs)
            }
            part.isMimeType("message/rfc822") -> (part.content as? Part)?.let { walk(it, out, withPdfs) }
            part.isMimeType("application/pdf") || name.endsWith(".pdf", ignoreCase = true) -> {
                if (withPdfs && out.pdfs.size < MAX_PDFS) {
                    out.pdfs += part.inputStream.use { it.readBytes() }
                    out.pdfNames += name.ifEmpty { "statement.pdf" }
                }
            }
            name.isNotEmpty() || Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) -> Unit
            part.isMimeType("text/plain") && out.plain == null -> out.plain = readText(part)
            part.isMimeType("text/html") && out.html == null -> out.html = readText(part)
        }
    }

    /** Decodes the bytes with the part's own charset, without relying on JavaMail's content handlers. */
    private fun readText(part: Part): String {
        val charset = runCatching { ContentType(part.contentType).getParameter("charset") }.getOrNull()
            ?.let { runCatching { charset(javax.mail.internet.MimeUtility.javaCharset(it)) }.getOrNull() } ?: Charsets.UTF_8
        return part.inputStream.use { String(it.readBytes(), charset) }
    }

    /** The folder with the IMAP \\All attribute (Gmail's "All Mail", whatever its language), or null. */
    private fun allMailFolder(store: javax.mail.Store): Folder? = runCatching {
        store.defaultFolder.list("*").firstOrNull { f ->
            (f as? com.sun.mail.imap.IMAPFolder)?.attributes?.any { it.equals("\\All", ignoreCase = true) } == true
        }
    }.getOrNull()

    private fun session(login: MailLogin): Session = Session.getInstance(
        Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", login.server.imapHost)
            put("mail.imaps.port", login.server.imapPort.toString())
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", TIMEOUT); put("mail.imaps.timeout", TIMEOUT)
            // Fetch whole message bodies in one go rather than 16 KB at a time.
            put("mail.imaps.partialfetch", "false")
        },
    )

    /** Runs blocking JavaMail on the IO pool and maps its exceptions onto IOException. */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: AuthenticationFailedException) {
            throw MailAuthException(e.message?.takeIf { it.isNotBlank() } ?: "The email address or app password was not accepted")
        } catch (e: MessagingException) {
            throw IOException(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private companion object {
        const val TIMEOUT = "20000"
        const val MIN_PLAIN_LENGTH = 30
        const val MAX_PDFS = 3
    }
}
