package com.hisaab.parser.statement

import java.time.LocalDate

/** What banks build statement passwords from. Everything is optional. */
data class Identity(
    val name: String? = null,
    val dob: LocalDate? = null,
    val pan: String? = null,
    val phone: String? = null,
)

/**
 * Likely statement passwords from the user's details, most common formats first:
 * card statements (HDFC, Axis: NAME + DDMM; ICICI, Kotak: name + DDMM; SBI Card: DDMMYYYY + last 4 of card),
 * CAS from CAMS, KFintech, NSDL and CDSL (PAN), NPS and bank statements (DOB, customer details).
 */
object PasswordGuesser {
    fun candidates(id: Identity, last4s: Collection<String> = emptyList()): List<String> {
        val out = LinkedHashSet<String>()
        val pan = id.pan?.uppercase()?.filter(Char::isLetterOrDigit)?.takeIf { it.length == 10 }
        val phone = id.phone?.filter(Char::isDigit)?.takeLast(10)?.takeIf { it.length == 10 }
        val names = nameParts(id.name)
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
        for (n in names) {
            val four = n.take(4)
            for (base in listOf(four.uppercase(), four.lowercase(), four.lowercase().replaceFirstChar { it.uppercase() })) {
                ddmm?.let { out += base + it }
                ddmmyy?.let { out += base + it }
                ddmmyyyy?.let { out += base + it }
                last4s.forEach { out += base + it }
                phone?.let { out += base + it.takeLast(4) }
                dob?.let { out += base + it.year }
            }
        }
        // SBI Card: DDMMYYYY + last 4 of the card; some banks put the digits first.
        for (l4 in last4s) {
            ddmmyyyy?.let { out += it + l4 }
            ddmm?.let { out += it + l4; out += l4 + it }
        }
        out += dates
        phone?.let { out += it; out += it.takeLast(4); out += it.takeLast(5); out += it.takeLast(6) }
        // PAN with the DOB (some insurers and brokers), and the first five letters of the PAN.
        if (pan != null) {
            ddmmyyyy?.let { out += pan + it; out += pan.lowercase() + it }
            ddmm?.let { out += pan.take(5) + it; out += pan.take(5).lowercase() + it }
        }
        phone?.let { p -> ddmm?.let { out += p.takeLast(4) + it; out += it + p.takeLast(4) } }
        return out.filter { it.length >= 4 }
    }

    private fun nameParts(name: String?): List<String> {
        val words = name?.trim()?.split(Regex("\\s+"))?.map { w -> w.filter(Char::isLetter) }?.filter { it.isNotEmpty() }.orEmpty()
        if (words.isEmpty()) return emptyList()
        // First name first; then the last name and the whole name run together.
        return listOf(words.first(), words.last(), words.joinToString("")).distinct().filter { it.length >= 2 }
    }
}
