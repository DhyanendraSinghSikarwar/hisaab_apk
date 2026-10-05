package com.hisaab.email.imap

import com.hisaab.email.sync.GmailSettingsStore
import com.hisaab.email.sync.MailConnection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connecting an inbox by email: (1) the user gives the address, (2) signs in with an app password,
 * which is checked against IMAP, (3) a random 6-digit code is mailed to that address, and (4) once the
 * code is typed back, the login is saved (encrypted) and syncing can start.
 */
@Singleton
class MailConnector @Inject constructor(
    private val client: MailClient,
    private val accounts: MailAccountStore,
    private val settings: GmailSettingsStore,
) {
    private val code = VerificationCode()
    @Volatile private var pending: MailLogin? = null

    /** Checks the login, then mails the code. Throws [MailAuthException] or IOException with a readable message. */
    suspend fun signInAndSendCode(email: String, password: String) {
        val login = MailLogin(email.trim().lowercase(), password.replace(" ", ""), MailServers.forAddress(email))
        client.checkLogin(login)
        pending = login
        send(login)
    }

    suspend fun resendCode() {
        send(pending ?: throw IllegalStateException("Sign in first"))
    }

    private suspend fun send(login: MailLogin) {
        val c = code.issue(login.email)
        client.sendToSelf(
            login,
            subject = "Hisaab verification code: $c",
            body = "Your Hisaab verification code is $c\n\n" +
                "Type it into the app to finish connecting ${login.email}. It expires in 10 minutes.\n\n" +
                "If you did not ask for this, change your app password and ignore this email.",
        )
    }

    /** On success the login is stored and the account marked connected; the caller starts the first sync. */
    suspend fun verify(typed: String): VerificationCode.Result {
        val login = pending ?: return VerificationCode.Result.NONE
        val result = code.check(login.email, typed)
        if (result == VerificationCode.Result.OK) {
            accounts.save(login.email, login.password)
            settings.connected(login.email, MailConnection.IMAP)
            pending = null
        }
        return result
    }

    fun cancel() {
        pending = null
        code.clear()
    }

    /** Deletes every stored login and its Keystore key, and forgets the accounts. Transactions stay. */
    suspend fun signOut() {
        accounts.wipe()
        settings.clearAccount()
    }

    /** Disconnects one address. When it was the last one, email sync is switched off. */
    suspend fun remove(email: String) {
        accounts.remove(email)
        settings.forgetSyncPosition(email)
        if (accounts.logins().isEmpty()) settings.clearAccount()
    }
}
