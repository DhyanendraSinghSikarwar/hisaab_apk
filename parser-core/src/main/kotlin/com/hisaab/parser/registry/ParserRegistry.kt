package com.hisaab.parser.registry

import com.hisaab.parser.BankParser
import com.hisaab.parser.ParserConfig
import com.hisaab.parser.bank.AuBankParser
import com.hisaab.parser.bank.AxisBankParser
import com.hisaab.parser.bank.BankOfBarodaParser
import com.hisaab.parser.bank.BaseBankParser
import com.hisaab.parser.bank.GenericBankParser
import com.hisaab.parser.bank.HdfcBankParser
import com.hisaab.parser.bank.IciciBankParser
import com.hisaab.parser.bank.IdfcFirstBankParser
import com.hisaab.parser.bank.KotakBankParser
import com.hisaab.parser.bank.PnbParser
import com.hisaab.parser.bank.SbiParser
import com.hisaab.parser.bank.YesBankParser
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source

/** Maps a sender to its parser through a HashMap. The generic parser handles bank-looking senders nobody claims. */
class ParserRegistry(
    val parsers: List<BankParser>,
    private val fallback: BankParser? = null,
) {
    private val byKey: HashMap<String, BankParser> = HashMap<String, BankParser>(parsers.size * 8).apply {
        for (p in parsers) for (k in p.senderKeys) {
            val previous = put(k, p)
            require(previous == null || previous === p) { "Sender key $k claimed by ${previous!!.bankName} and ${p.bankName}" }
        }
    }

    fun resolve(sender: String): BankParser? {
        for (key in SenderKeys.candidates(sender)) byKey[key]?.let { return it }
        return null
    }

    /** SMS pre-filter: true only for senders a dedicated parser claims. Cheap enough to run on every inbox row. */
    fun isKnownSender(sender: String): Boolean = resolve(sender) != null

    fun parse(body: String, sender: String, timestamp: Long, source: Source): ParsedTransaction? {
        val parser = resolve(sender) ?: fallback?.takeIf { it.canHandle(sender, body) } ?: return null
        return parser.parse(body, sender, timestamp, source)
    }

    /** Domains for the default Gmail sender whitelist. */
    val defaultEmailSenders: List<String>
        get() = parsers.filterIsInstance<BaseBankParser>().flatMap { it.emailSenders }.distinct()

    companion object {
        fun default(config: ParserConfig = ParserConfig()): ParserRegistry = ParserRegistry(
            parsers = listOf(
                HdfcBankParser(config), IciciBankParser(config), SbiParser(config), AxisBankParser(config),
                KotakBankParser(config), IdfcFirstBankParser(config), YesBankParser(config), BankOfBarodaParser(config),
                PnbParser(config), AuBankParser(config),
            ),
            fallback = GenericBankParser(config),
        )
    }
}
