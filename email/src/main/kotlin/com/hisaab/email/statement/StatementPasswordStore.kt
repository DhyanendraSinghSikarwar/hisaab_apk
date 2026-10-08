package com.hisaab.email.statement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.email.auth.KeystoreSecret
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.statementPasswords: DataStore<Preferences> by preferencesDataStore(name = "statement_passwords")

/**
 * What a bank account holds for opening statements (all optional). [name], [mobile] and [email] are this
 * account's own; when empty the profile's apply.
 */
data class AccountDetails(
    val customerId: String = "", val accountNumber: String = "", val ifsc: String = "",
    val name: String = "", val mobile: String = "", val email: String = "",
) {
    val isEmpty get() = fields().all { it.isBlank() }

    /** Order is the on-disk format: append new fields at the end only. */
    internal fun fields() = listOf(customerId, accountNumber, ifsc.uppercase(), name, mobile, email).map { it.replace(SEP_CHAR, ' ').trim() }

    /** Own name, mobile and email first, then the profile's. */
    fun effective(me: com.hisaab.parser.statement.Identity) = com.hisaab.parser.statement.BankIdentity(
        customerId = customerId.ifBlank { null }, accountNumber = accountNumber.ifBlank { null }, ifsc = ifsc.ifBlank { null },
        names = listOfNotNull(name.ifBlank { null }),
        phones = listOfNotNull(mobile.ifBlank { null }),
        emails = listOfNotNull(email.ifBlank { null }, me.email),
    )

    companion object {
        internal fun from(f: List<String>) = AccountDetails(
            f.getOrElse(0) { "" }, f.getOrElse(1) { "" }, f.getOrElse(2) { "" }, f.getOrElse(3) { "" }, f.getOrElse(4) { "" }, f.getOrElse(5) { "" },
        )
    }
}

private const val SEP_CHAR = '\u001F'

/** A saved statement password, shown by its label only. */
data class SavedPassword(val id: String, val label: String)

/**
 * Passwords for statement PDFs (card statements, CAS, bank statements). Each is sealed with a Keystore
 * key; only ciphertext is on disk, and they are never sent anywhere. New statements try every saved one.
 */
@Singleton
class StatementPasswordStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.statementPasswords
    private val secret = KeystoreSecret(ALIAS)

    val saved: Flow<List<SavedPassword>> = store.data.map { p -> p[ENTRIES].orEmpty().mapNotNull(::decode).map { SavedPassword(it.first, it.second) }.sortedBy { it.label } }

    suspend fun add(label: String, password: String) {
        if (password.isEmpty()) return
        val entry = listOf(UUID.randomUUID().toString(), label.trim().ifEmpty { "Statement password" }.replace(SEP, ' '), secret.seal(password))
            .joinToString(SEP.toString())
        store.edit { it[ENTRIES] = it[ENTRIES].orEmpty() + entry }
    }

    suspend fun remove(id: String) = store.edit { p -> p[ENTRIES] = p[ENTRIES].orEmpty().filterNot { decode(it)?.first == id }.toSet() }

    /**
     * The user's details that banks build passwords from (name, DOB, PAN, mobile, and an alternate name and
     * mobile), sealed like the passwords.
     */
    val identity: Flow<com.hisaab.parser.statement.Identity> = store.data.map { p -> p[IDENTITY]?.let(::decodeIdentity) ?: com.hisaab.parser.statement.Identity() }

    suspend fun setIdentity(id: com.hisaab.parser.statement.Identity) = store.edit {
        // Field order is the on-disk format: append new fields at the end only.
        val fields = listOf(
            id.name.orEmpty(), id.dob?.toString().orEmpty(), id.pan.orEmpty().uppercase(), id.phone.orEmpty(),
            id.altName.orEmpty(), id.altPhone.orEmpty(), id.email.orEmpty(),
        ).map { f -> f.replace(SEP, ' ').trim() }
        if (fields.all { f -> f.isBlank() }) it.remove(IDENTITY) else it[IDENTITY] = secret.seal(fields.joinToString(SEP.toString()))
    }

    /** Reads the original four-field value, the six-field one and the current seven-field one; missing fields are null. */
    private fun decodeIdentity(sealed: String): com.hisaab.parser.statement.Identity? {
        val f = runCatching { secret.open(sealed) }.getOrNull()?.split(SEP) ?: return null
        if (f.size < 4) return null
        fun at(i: Int) = f.getOrNull(i)?.ifBlank { null }
        return com.hisaab.parser.statement.Identity(
            name = at(0), dob = at(1)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            pan = at(2), phone = at(3), altName = at(4), altPhone = at(5), email = at(6),
        )
    }

    /**
     * Details that bank accounts hold for opening statements, sealed per account id and never stored in the
     * database. The name, mobile and email fall back to the profile's when left empty (see [AccountDetails.effective]).
     */
    fun accountDetails(accountId: Long): Flow<AccountDetails> = store.data.map { p -> readDetails(p)[accountId] ?: AccountDetails() }

    /** How many accounts have unlock details saved, for the profile summary. */
    val accountDetailsCount: Flow<Int> = store.data.map { p -> readDetails(p).count { !it.value.isEmpty } }

    /** Every account's saved details, by account id. */
    suspend fun allAccountDetails(): Map<Long, AccountDetails> = readDetails(store.data.first())

    suspend fun setAccountDetails(accountId: Long, d: AccountDetails) = store.edit { p ->
        val rest = p[DETAILS].orEmpty().filterNot { it.substringBefore(SEP) == accountId.toString() }.toSet()
        p[DETAILS] = if (d.isEmpty) rest else rest + (accountId.toString() + SEP + secret.seal(d.fields().joinToString(SEP.toString())))
    }

    /** Forgets an account's details when the account is removed. */
    suspend fun removeAccountDetails(accountId: Long) = setAccountDetails(accountId, AccountDetails())

    private fun readDetails(p: Preferences): Map<Long, AccountDetails> = buildMap {
        for (entry in p[DETAILS].orEmpty()) {
            val id = entry.substringBefore(SEP).toLongOrNull() ?: continue
            val plain = runCatching { secret.open(entry.substringAfter(SEP)) }.getOrNull() ?: continue
            put(id, AccountDetails.from(plain.split(SEP)))
        }
    }

    /**
     * Guesses for one PDF, best first: exact forms from the details of [accountIds] (every account of the
     * statement's bank, or of all banks when it is unknown), then combinations with names and mobiles when
     * [combos], then the saved passwords.
     */
    suspend fun attempts(cardLast4s: Collection<String>, accountIds: Collection<Long> = emptyList(), combos: Boolean = false): List<String> {
        val me = identity.first()
        val all = allAccountDetails()
        val banks = accountIds.mapNotNull { all[it] }.filterNot { it.isEmpty }.map { it.effective(me) }
        val guesses = if (banks.isEmpty()) com.hisaab.parser.statement.PasswordGuesser.candidates(me, cardLast4s)
        else com.hisaab.parser.statement.PasswordGuesser.forBanks(me, banks, cardLast4s, combos)
        return (guesses + passwords()).distinct()
    }

    /** Every saved password, decrypted for one attempt at opening a PDF. */
    suspend fun passwords(): List<String> = store.data.first()[ENTRIES].orEmpty().mapNotNull { decode(it)?.third?.let(secret::open) }

    private fun decode(entry: String): Triple<String, String, String>? {
        val parts = entry.split(SEP)
        return if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
    }

    private companion object {
        const val ALIAS = "hisaab.statement.passwords"
        const val SEP = '\u001F'
        val ENTRIES = stringSetPreferencesKey("entries")
        val DETAILS = stringSetPreferencesKey("account_details")
        val IDENTITY = androidx.datastore.preferences.core.stringPreferencesKey("identity")
    }
}
