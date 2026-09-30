package com.hisaab.parser.bank

import com.hisaab.parser.text.rx
import com.hisaab.parser.ParserConfig
import com.hisaab.parser.registry.SenderKeys

/**
 * Last resort for a bank sender with no dedicated parser. Accepts only senders that look like banks or
 * card issuers, so a stray courier or shopping SMS that mentions an amount is never counted.
 * Decides on the sender alone; the body is not needed.
 */
class GenericBankParser(config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName = "Bank"
    override val smsHeaders = emptySet<String>()
    override val emailDomains = emptySet<String>()

    override fun canHandle(sender: String, body: String): Boolean {
        val key = SenderKeys.candidates(sender).lastOrNull() ?: return false
        return if ('.' in key || '@' in key) BANKISH_DOMAIN.containsMatchIn(key)
        else key.uppercase() in CARD_ISSUERS || BANKISH_HEADER.containsMatchIn(key)
    }

    override fun bankNameFor(sender: String): String {
        val key = SenderKeys.candidates(sender).lastOrNull() ?: return bankName
        return if ('.' in key) key.substringBefore('.').replaceFirstChar { it.titlecase() } else key
    }

    private companion object {
        val BANKISH_HEADER = rx("""(?:BK|BNK|BANK|BNKK|CRD)$|BANK|CARD""")
        /** Card issuers and banks whose SMS headers do not look like "...BNK" or "...CRD". */
        val CARD_ISSUERS = setOf(
            "AMEXIN", "AMEXCC", "INDUSB", "INDUSL", "HSBCIN", "HSBCCC", "SCBNKS", "STANCB", "ONECRD", "BOBFIN",
            "BOIIND", "SIBSMS", "PAYTMB", "SLCEIT", "UNIONB", "RBLCRD", "DBSIND", "CITIIN", "IDBIIN", "FEDSMS",
        )
        val BANKISH_DOMAIN = rx("""bank""")
    }
}
