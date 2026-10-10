package com.hisaab.shared.repo

/**
 * Which loan account a payment belongs to when the user said so, without moving it off the bank account that paid it.
 * [byTx] links one transaction, [byPayee] every payment to a payee key (see `Loans.keyOf`), so future payments from
 * the same merchant attach by themselves. [manualPayees] are payees the user filed under EMI & Loans by hand: they
 * show in Loans from their first payment. Values are loan account ids.
 */
data class LoanLinks(
    val byTx: Map<Long, Long> = emptyMap(),
    val byPayee: Map<String, Long> = emptyMap(),
    val manualPayees: Set<String> = emptySet(),
) {
    companion object {
        val NONE = LoanLinks()

        /** "id:loanId" per transaction. */
        fun encodeTx(tx: Map<Long, Long>): Set<String> = tx.mapTo(HashSet()) { (k, v) -> "$k:$v" }

        fun decodeTx(raw: Set<String>): Map<Long, Long> = raw.mapNotNull { s ->
            val i = s.indexOf(':')
            if (i < 0) return@mapNotNull null
            val k = s.substring(0, i).toLongOrNull() ?: return@mapNotNull null
            val v = s.substring(i + 1).toLongOrNull() ?: return@mapNotNull null
            k to v
        }.toMap()

        /** "loanId:payee" per payee (a payee may itself contain ':'). */
        fun encodePayee(m: Map<String, Long>): Set<String> = m.mapTo(HashSet()) { (k, v) -> "$v:$k" }

        fun decodePayee(raw: Set<String>): Map<String, Long> = raw.mapNotNull { s ->
            val i = s.indexOf(':')
            if (i < 0) return@mapNotNull null
            val v = s.substring(0, i).toLongOrNull() ?: return@mapNotNull null
            s.substring(i + 1) to v
        }.toMap()
    }
}
