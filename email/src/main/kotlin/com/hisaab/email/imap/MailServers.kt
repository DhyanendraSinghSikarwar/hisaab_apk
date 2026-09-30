package com.hisaab.email.imap

/** Where to read (IMAP over TLS) and send (SMTP) mail for one address. */
data class MailServer(
    val provider: String,
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int,
    /** True for SMTP over TLS from the start (465); false for STARTTLS (587). */
    val smtpSsl: Boolean,
    /** Where the user creates an app password, when the provider needs one for IMAP. */
    val appPasswordUrl: String? = null,
)

object MailServers {
    private val EMAIL = Regex("""^[A-Za-z0-9._%+\-]+@([A-Za-z0-9\-]+\.)+[A-Za-z]{2,}$""")

    fun isValidAddress(email: String): Boolean = EMAIL.matches(email.trim())

    fun forAddress(email: String): MailServer {
        val domain = email.trim().substringAfterLast('@').lowercase()
        return when (domain) {
            "gmail.com", "googlemail.com" -> MailServer(
                "Gmail", "imap.gmail.com", smtpHost = "smtp.gmail.com", smtpPort = 465, smtpSsl = true,
                appPasswordUrl = "https://myaccount.google.com/apppasswords",
            )
            "outlook.com", "hotmail.com", "live.com", "msn.com", "outlook.in", "hotmail.co.in" -> MailServer(
                "Outlook", "outlook.office365.com", smtpHost = "smtp-mail.outlook.com", smtpPort = 587, smtpSsl = false,
                appPasswordUrl = "https://account.live.com/proofs/AppPassword",
            )
            "yahoo.com", "yahoo.co.in", "yahoo.in", "ymail.com", "rocketmail.com" -> MailServer(
                "Yahoo Mail", "imap.mail.yahoo.com", smtpHost = "smtp.mail.yahoo.com", smtpPort = 465, smtpSsl = true,
                appPasswordUrl = "https://login.yahoo.com/account/security",
            )
            "icloud.com", "me.com", "mac.com" -> MailServer(
                "iCloud Mail", "imap.mail.me.com", smtpHost = "smtp.mail.me.com", smtpPort = 587, smtpSsl = false,
                appPasswordUrl = "https://account.apple.com/account/manage",
            )
            "zoho.com", "zohomail.com", "zohomail.in" -> MailServer(
                "Zoho Mail", "imap.zoho.com", smtpHost = "smtp.zoho.com", smtpPort = 465, smtpSsl = true,
                appPasswordUrl = "https://accounts.zoho.com/home#security/app_password",
            )
            "rediffmail.com" -> MailServer("Rediffmail", "imap.rediffmail.com", smtpHost = "smtp.rediffmail.com", smtpPort = 465, smtpSsl = true)
            "aol.com" -> MailServer(
                "AOL Mail", "imap.aol.com", smtpHost = "smtp.aol.com", smtpPort = 465, smtpSsl = true,
                appPasswordUrl = "https://login.aol.com/account/security",
            )
            // Most other providers follow the imap./smtp. naming.
            else -> MailServer(domain, "imap.$domain", smtpHost = "smtp.$domain", smtpPort = 465, smtpSsl = true)
        }
    }
}
