package com.hisaab.parser.statement

import java.time.LocalDate

/**
 * What banks build statement passwords from. Everything is optional. [altName] and [altPhone] cover a second
 * name or mobile on record with another bank or card (a spelling variant, a maiden name, an old number).
 */
data class Identity(
    val name: String? = null,
    val dob: LocalDate? = null,
    val pan: String? = null,
    val phone: String? = null,
    val altName: String? = null,
    val altPhone: String? = null,
    /** Kept with the sealed details so accounts can inherit it; not used to build guesses. */
    val email: String? = null,
)

/**
 * What one bank account or card adds to [Identity]: details the bank itself holds. [names], [phones] and [emails]
 * are the account's effective values, its own before the profile's.
 */
data class BankIdentity(
    val customerId: String? = null,
    val accountNumber: String? = null,
    val ifsc: String? = null,
    val names: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
)

/** Maps a bank's name, or an email sender, to a stable key, so accounts and statements of one bank meet. */
object BankKeys {
    private val RULES = listOf(
        "hdfc" to Regex("""hdfc""", RegexOption.IGNORE_CASE), "icici" to Regex("""icici""", RegexOption.IGNORE_CASE),
        "axis" to Regex("""\baxis""", RegexOption.IGNORE_CASE), "hsbc" to Regex("""hsbc""", RegexOption.IGNORE_CASE),
        "sbi" to Regex("""\bsbi|state bank""", RegexOption.IGNORE_CASE), "kotak" to Regex("""kotak""", RegexOption.IGNORE_CASE),
        "yes" to Regex("""\byes\s*bank|yesbank""", RegexOption.IGNORE_CASE), "idfc" to Regex("""idfc""", RegexOption.IGNORE_CASE),
        "indusind" to Regex("""indusind""", RegexOption.IGNORE_CASE), "baroda" to Regex("""baroda|\bbob\b""", RegexOption.IGNORE_CASE),
        "pnb" to Regex("""\bpnb\b|punjab national""", RegexOption.IGNORE_CASE), "federal" to Regex("""federal""", RegexOption.IGNORE_CASE),
        "canara" to Regex("""canara""", RegexOption.IGNORE_CASE), "union" to Regex("""union bank""", RegexOption.IGNORE_CASE),
        "rbl" to Regex("""\brbl""", RegexOption.IGNORE_CASE), "scb" to Regex("""standard chartered|\bscb\b""", RegexOption.IGNORE_CASE),
        "amex" to Regex("""\bamex|american express""", RegexOption.IGNORE_CASE), "au" to Regex("""\bau small|\bau bank""", RegexOption.IGNORE_CASE),
    )

    /** The key of the first bank named in [text] (a bank name, or a sender like `Name <alerts@hdfcbank.net>`), or null. */
    fun of(text: String?): String? = text?.let { t -> RULES.firstOrNull { it.second.containsMatchIn(t) }?.first }
}

/**
 * Likely statement passwords from the user's details, most common formats first:
 * card statements (HDFC, Axis: NAME + DDMM; ICICI, Kotak: name + DDMM; SBI Card: DDMMYYYY + last 4 of card),
 * CAS from CAMS, KFintech, NSDL and CDSL (PAN), NPS and bank statements (DOB, customer details).
 * The primary name and mobile come before the alternates; the list is capped at [MAX] so every PDF stays quick to try.
 */
object PasswordGuesser {
    const val MAX = 240

    /** Cap for [candidates] with bank details: exact forms from every account, then name and date combinations. */
    const val MAX_WITH_BANKS = 300

    /**
     * Exact forms first (HDFC uses the customer ID as it is), then, when [combos], the usual combinations built
     * from each account's own name and mobile before the profile's, then the profile alone. [banks] holds the
     * accounts and cards of the statement's bank, or of every bank when that bank is unknown.
     */
    fun forBanks(profile: Identity, banks: List<BankIdentity>, last4s: Collection<String> = emptyList(), combos: Boolean = true): List<String> {
        val out = LinkedHashSet<String>()
        for (b in banks) out += exactForms(b)
        if (combos) {
            for (b in banks) {
                val names = (b.names + listOfNotNull(profile.name, profile.altName)).filter { it.isNotBlank() }.distinct()
                val phones = (b.phones + listOfNotNull(profile.phone, profile.altPhone)).filter { it.isNotBlank() }.distinct()
                if (b.names.isEmpty() && b.phones.isEmpty()) continue
                out += candidates(
                    profile.copy(name = names.getOrNull(0), altName = names.getOrNull(1), phone = phones.getOrNull(0), altPhone = phones.getOrNull(1)),
                    last4s,
                )
            }
        }
        out += candidates(profile, last4s)
        return out.take(MAX_WITH_BANKS)
    }

    private fun exactForms(b: BankIdentity): List<String> {
        val out = LinkedHashSet<String>()
        b.customerId?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it; out += it.filter(Char::isLetterOrDigit) }
        val acct = b.accountNumber?.filter(Char::isLetterOrDigit)?.takeIf { it.isNotEmpty() }
        if (acct != null) {
            out += acct
            for (n in listOf(4, 5, 6, 8)) if (acct.length > n) out += acct.takeLast(n)
        }
        b.ifsc?.filter(Char::isLetterOrDigit)?.takeIf { it.isNotEmpty() }?.let { out += it.uppercase(); out += it.lowercase() }
        return out.filter { it.length >= 4 }
    }

    fun candidates(id: Identity, last4s: Collection<String> = emptyList()): List<String> {
        val out = LinkedHashSet<String>()
        val pan = id.pan?.uppercase()?.filter(Char::isLetterOrDigit)?.takeIf { it.length == 10 }
        val phones = listOfNotNull(id.phone, id.altPhone)
            .mapNotNull { p -> p.filter(Char::isDigit).takeLast(10).takeIf { it.length == 10 } }.distinct()
        // Four-letter name prefixes, primary name first; the same prefix from both names is tried once.
        val prefixes = (nameParts(id.name) + nameParts(id.altName)).map { it.take(4) }.distinctBy { it.lowercase() }
        val dob = id.dob
        val ddmm = dob?.let { "%02d%02d".format(it.dayOfMonth, it.monthValue) }
        val ddmmyy = dob?.let { ddmm + "%02d".format(it.year % 100) }
        val ddmmyyyy = dob?.let { ddmm + it.year }
        val dates = listOfNotNull(
            ddmm, ddmmyyyy, ddmmyy,
            dob?.let { "%04d%02d%02d".format(it.year, it.monthValue, it.dayOfMonth) },
            dob?.let { "%02d%02d%04d".format(it.monthValue, it.dayOfMonth, it.year) },
            dob?.let { "%02d%02d".format(it.monthValue, it.dayOfMonth) },
            dob?.year?.toString(),
        )

        // CAS (CAMS, KFintech, NSDL, CDSL) and many tax and NPS documents: the PAN.
        pan?.let { out += it; out += it.lowercase() }

        // NAME + DDMM and its relatives: the usual card statement password.
        for (four in prefixes) {
            for (base in listOf(four.uppercase(), four.lowercase(), four.lowercase().replaceFirstChar { it.uppercase() })) {
                ddmm?.let { out += base + it }
                ddmmyy?.let { out += base + it }
                ddmmyyyy?.let { out += base + it }
                last4s.forEach { out += base + it }
                phones.forEach { out += base + it.takeLast(4) }
                dob?.let { out += base + it.year }
            }
        }
        // SBI Card: DDMMYYYY + last 4 of the card; some banks put the digits first.
        for (l4 in last4s) {
            ddmmyyyy?.let { out += it + l4 }
            ddmm?.let { out += it + l4; out += l4 + it }
        }
        out += dates
        for (p in phones) { out += p; out += p.takeLast(4); out += p.takeLast(5); out += p.takeLast(6) }
        // PAN with the DOB (some insurers and brokers), and the first five letters of the PAN.
        if (pan != null) {
            ddmmyyyy?.let { out += pan + it; out += pan.lowercase() + it }
            ddmm?.let { out += pan.take(5) + it; out += pan.take(5).lowercase() + it }
        }
        for (p in phones) ddmm?.let { out += p.takeLast(4) + it; out += it + p.takeLast(4) }
        return out.filter { it.length >= 4 }.take(MAX)
    }

    private fun nameParts(name: String?): List<String> {
        val words = name?.trim()?.split(Regex("\\s+"))?.map { w -> w.filter(Char::isLetter) }?.filter { it.isNotEmpty() }.orEmpty()
        if (words.isEmpty()) return emptyList()
        // First name first; then the last name and the whole name run together.
        return listOf(words.first(), words.last(), words.joinToString("")).distinct().filter { it.length >= 2 }
    }
}
