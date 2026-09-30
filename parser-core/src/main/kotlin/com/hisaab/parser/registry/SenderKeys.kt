package com.hisaab.parser.registry

import com.hisaab.parser.text.rx
/**
 * Registry lookup keys for a sender, most specific first.
 *
 * SMS "VM-HDFCBK-S" -> [HDFCBK] (TRAI header: 2-char operator prefix, 6-char entity, optional suffix).
 * Email "HDFC Bank <alerts@hdfcbank.net>" -> [alerts@hdfcbank.net, hdfcbank.net, net]
 * Email "x@alerts.sbi.co.in" -> [x@alerts.sbi.co.in, alerts.sbi.co.in, sbi.co.in, co.in]
 */
object SenderKeys {
    private val ANGLE_ADDRESS = rx("<([^>]+)>")

    fun candidates(sender: String): List<String> {
        val s = sender.trim()
        if (s.isEmpty()) return emptyList()
        if ('@' in s) {
            val address = (ANGLE_ADDRESS.find(s)?.groupValues?.get(1) ?: s).trim().lowercase()
            val out = ArrayList<String>(5)
            out += address
            var domain = address.substringAfterLast('@')
            while ('.' in domain) {
                out += domain
                domain = domain.substringAfter('.')
            }
            return out
        }
        return listOf(smsHeader(s))
    }

    fun smsHeader(sender: String): String {
        val parts = sender.uppercase().split('-').map { p -> p.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> ""
            parts.size >= 2 && parts[0].length <= 2 -> parts[1]
            else -> parts[0]
        }
    }
}
