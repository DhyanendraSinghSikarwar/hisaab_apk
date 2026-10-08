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
import com.hisaab.parser.bank.LenderParser
import com.hisaab.parser.bank.Lenders
import com.hisaab.parser.bank.LoanStatus
import com.hisaab.parser.bank.IndianBanks
import com.hisaab.parser.bank.ListedBank
import com.hisaab.parser.bank.ListedBankParser
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

    /** True only for senders a dedicated parser claims. */
    fun isKnownSender(sender: String): Boolean = resolve(sender) != null

    /**
     * SMS pre-filter: senders a dedicated parser claims, plus bank and card-issuer senders the generic
     * parser takes. Looks at the sender only, so it is cheap enough to run on every inbox row.
     */
    fun accepts(sender: String): Boolean = isKnownSender(sender) || fallback?.canHandle(sender, "") == true

    /**
     * What a lender's message says about a loan when it is not a payment (disbursal, outstanding, reminder), or what a
     * bank's message says about an FD, RD or PPF account (booking, renewal, maturity, balance; see [LoanStatus.deposit]).
     */
    fun loanStatus(body: String, sender: String, timestamp: Long, source: Source): LoanStatus? {
        val parser = resolve(sender) ?: fallback?.takeIf { it.canHandle(sender, body) }
        if (parser is LenderParser) return parser.status(body, timestamp, source)
        return (parser as? BaseBankParser)?.depositStatus(body, sender, timestamp, source)
    }

    fun parse(body: String, sender: String, timestamp: Long, source: Source): ParsedTransaction? {
        val parser = resolve(sender) ?: fallback?.takeIf { it.canHandle(sender, body) } ?: return null
        return parser.parse(body, sender, timestamp, source)
    }

    /** Domains for the default email sender whitelist: banks, plus the senders of investment and card statements. */
    val defaultEmailSenders: List<String>
        get() = (parsers.filterIsInstance<BaseBankParser>().flatMap { it.emailSenders } + STATEMENT_SENDERS).distinct()

    companion object {
        /** CAS and broker statements (CAMS, KFintech, NSDL, CDSL, brokers), EPFO, and card issuers without a parser. */
        val STATEMENT_SENDERS = listOf(
            "camsonline.com", "kfintech.com", "karvy.com", "nsdl.co.in", "nsdl.com", "cdslindia.com", "cdslstatement.com",
            "indmoney.com", "zerodha.com", "zerodha.net", "groww.in", "upstox.com", "angelone.in", "dhan.co", "kuvera.in",
            "epfindia.gov.in", "sbicard.com", "americanexpress.co.in", "aexp.com", "rblbank.com", "indusind.com", "hsbc.co.in",
            "sc.com", "onecard.in", "federalbank.co.in", "aubank.in",
            "getonecard.app", "slicepay.in", "sliceit.com", "jupiter.money", "fi.money", "scapia.cards", "uni.cards", "bobfinancial.com",
            "bajajfinserv.in", "kfintech.net", "camsonline.co.in", "indusind.bank.in", "canarabank.com", "unionbankofindia.co.in",
            "citibank.com", "dbs.com", "paytmbank.com", "nps-proteantech.in", "proteantech.in",
            "cdslindia.co.in", "cdsl.co.in", "npstrust.org.in", "npscra.nsdl.co.in", "kfintech-cra.com", "camsnps.com",
            "epfo.gov.in", "umang.gov.in",
            // Lenders (NBFC and fintech): EMI receipts and loan statements.
            "propelld.com", "adityabirlacapital.com", "abcd.adityabirlacapital.com", "tatacapital.com", "credila.com", "avanse.com",
            "incred.com", "smfgindiacredit.com", "homecredit.co.in", "kreditbee.in", "navi.com", "poonawallafincorp.com", "hdbfs.com",
            // Mutual fund apps and platforms: SIP instalment and order confirmations.
            "paytmmoney.com", "etmoney.com", "mfcentral.com", "bsestarmf.in", "mfuindia.com", "scripbox.com", "fisdom.com",
        )

        /**
         * Statement mails picked by subject, whoever sends them: a CAS forwarded by INDmoney or another app
         * ("CDSL Consolidated Account Statement (CAS) across Mutual Funds and Depositories"), NPS and EPF statements,
         * and mutual fund purchase confirmations ("SIP instalment processed", "Units allotted").
         */
        val STATEMENT_SUBJECTS = listOf(
            "Consolidated Account Statement", "NPS Transaction Statement", "PRAN", "EPF Passbook", "Member Passbook",
            "SIP installment", "SIP instalment", "units allotted", "allotment of units",
            "Statement of Transaction", "NPS contribution", "Holdings statement", "mutual fund statement", "Holdings report", "Balance statement", "Portfolio statement",
            "Fixed Deposit", "Term Deposit", "Recurring Deposit", "Deposit advice", "PPF statement", "Public Provident Fund", "portfolio summary",
        )
        private val STATEMENT_SUBJECT = com.hisaab.parser.text.rx(
            """consolidated\s+account\s+statement|\bCAS\b|\bNPS\b.{0,40}statement|\bPRAN\b|\bEPF\b.{0,30}(?:passbook|statement)|member\s+passbook""" +
                """|\bSIP\b.{0,60}\b(?:instal+ment|processed|successful|allot+ed)|\bunits?\s+(?:have\s+been\s+)?allot+ed|allotment\s+of\s+units""" +
                """|statement\s+of\s+transactions?|\bNPS\b.{0,40}(?:contribution|holding|transaction)|holdings?\s+(?:statement|report)|(?:balance|portfolio|investment)\s+(?:statement|report)|mutual\s+fund\s+statement""" +
                """|(?:fixed|term|recurring)\s+deposit|deposit\s+advice|\bPPF\b.{0,30}statement|public\s+provident\s+fund|portfolio\s+summary""",
        )

        fun isStatementSubject(subject: String?): Boolean = subject != null && STATEMENT_SUBJECT.containsMatchIn(subject)

        fun default(config: ParserConfig = ParserConfig()): ParserRegistry {
            val core: List<BankParser> = listOf(
                HdfcBankParser(config), IciciBankParser(config), SbiParser(config), AxisBankParser(config),
                KotakBankParser(config), IdfcFirstBankParser(config), YesBankParser(config), BankOfBarodaParser(config),
                PnbParser(config), AuBankParser(config),
            ) + Lenders.parsers(config)
            // The listed banks (Central Bank, Bank of Maharashtra, Canara, co-operative banks …) never take a sender
            // code or mail domain that a parser above already owns, so two parsers can never claim one sender.
            val taken = core.flatMap { it.senderKeys }.toMutableSet()
            val listed = IndianBanks.ALL.mapNotNull { bank ->
                val headers = bank.headers.filter { it.uppercase() !in taken }.toSet()
                val domains = bank.domains.filter { it.lowercase() !in taken }.toSet()
                taken += headers.map { it.uppercase() } + domains.map { it.lowercase() }
                if (headers.isEmpty() && domains.isEmpty()) null else ListedBankParser(ListedBank(bank.name, headers, domains), config)
            }
            return ParserRegistry(parsers = core + listed, fallback = GenericBankParser(config))
        }
    }
}
