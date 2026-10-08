package com.hisaab.parser.bank

import com.hisaab.parser.ParserConfig

/**
 * A bank, small finance bank, payments bank or co-operative bank with no templates of its own. Its alerts follow
 * the common "A/c XX1234 debited/credited by Rs. … Avl Bal …" shape, which the shared extractors read; the entry
 * supplies the display name and the sender codes (SMS headers) and mail domains it writes from.
 *
 * Sender codes are the ones banks register for transactional SMS; a code missing here still works when the
 * bank's header ends in BK, BNK or BANK (the generic parser), just under the raw header as its name.
 */
data class ListedBank(val name: String, val headers: Set<String>, val domains: Set<String> = emptySet())

object IndianBanks {
    private fun b(name: String, headers: String, domains: String = "") =
        ListedBank(name, headers.split(' ', ',').filter { it.isNotBlank() }.toSet(), domains.split(' ', ',').filter { it.isNotBlank() }.toSet())

    val ALL: List<ListedBank> = listOf(
        // Public sector banks (State Bank, Baroda and Punjab National have their own parsers).
        b("Central Bank of India", "CENTBK CBOIND CBINBK CENTRL CBISMS", "centralbank.co.in centralbankofindia.co.in"),
        b("Bank of Maharashtra", "MAHABK BOMSMS MAHBNK BOMBNK MAHABN", "bankofmaharashtra.in"),
        b("Canara Bank", "CANBNK CANARA CANBKS CNRBNK CANBSM", "canarabank.com canarabank.in"),
        b("Union Bank of India", "UNIONB UBINBK UBISMS UNIBNK", "unionbankofindia.co.in unionbankofindia.bank.in"),
        b("Bank of India", "BOIIND BOIBNK BOISMS BOIBKS", "bankofindia.co.in"),
        b("Indian Bank", "INDBNK INDIAN INDBKS INDSMS", "indianbank.in indianbank.co.in"),
        b("Indian Overseas Bank", "IOBCHN IOBBNK IOBSMS IOBANK", "iob.in"),
        b("UCO Bank", "UCOBNK UCOBKS UCOSMS UCOBAN", "ucobank.com"),
        b("Punjab & Sind Bank", "PSBANK PSBIND PSBSMS PSBBNK", "psbindia.com"),
        b("IDBI Bank", "IDBIBK IDBIIN IDBISM", "idbibank.in idbi.co.in"),
        // Private banks.
        b("IndusInd Bank", "INDUSB INDUSL", "indusind.com indusind.bank.in"),
        b("Federal Bank", "FEDBNK FEDSMS FEDBKS", "federalbank.co.in"),
        b("South Indian Bank", "SIBSMS SIBBNK SIBANK", "southindianbank.com"),
        b("Karur Vysya Bank", "KVBANK KVBSMS KVBBNK", "kvb.co.in"),
        b("City Union Bank", "CUBANK CUBSMS CUBBNK", "cityunionbank.com"),
        b("CSB Bank", "CSBANK CSBSMS CSBBNK", "csb.co.in"),
        b("DCB Bank", "DCBBNK DCBSMS DCBANK", "dcbbank.com"),
        b("Tamilnad Mercantile Bank", "TMBANK TMBSMS TMBBNK", "tmb.in"),
        b("Jammu & Kashmir Bank", "JKBANK JKBSMS JKBBNK", "jkbank.com"),
        b("Dhanlaxmi Bank", "DHANBK DLXBNK DHNLXM", "dhanbank.com"),
        b("Nainital Bank", "NTLBNK NAINBK NAINTL", "nainitalbank.co.in"),
        b("Karnataka Bank", "KTKBNK KARBNK KBLSMS", "karnatakabank.com"),
        b("RBL Bank", "RBLBNK RBLCRD RBLSMS", "rblbank.com"),
        b("Bandhan Bank", "BANDHN BDNBNK BNDHAN", "bandhanbank.com"),
        // Small finance banks.
        b("Equitas Small Finance Bank", "EQUTAS EQSFBL EQUITS", "equitasbank.com"),
        b("Ujjivan Small Finance Bank", "UJJSFB UJVNSB UJJIVN", "ujjivansfb.in"),
        b("Jana Small Finance Bank", "JANASF JANABK JANASM", "janabank.com"),
        b("Suryoday Small Finance Bank", "SURYDY SURYOD", "suryodaybank.com"),
        b("Utkarsh Small Finance Bank", "UTKSFB UTKRSH", "utkarsh.bank"),
        b("ESAF Small Finance Bank", "ESAFSF ESAFBK", "esafbank.com"),
        b("Capital Small Finance Bank", "CAPSFB CAPITL", "capitalbank.co.in"),
        // Foreign banks.
        b("HSBC", "HSBCIN HSBCCC HSBCBK", "hsbc.co.in"),
        b("Standard Chartered", "SCBNKS STANCB SCBANK", "sc.com"),
        b("Citibank", "CITIIN CITIBK CITIBN", "citibank.com citi.com"),
        b("DBS Bank", "DBSIND DBSBNK", "dbs.com"),
        // Payments banks.
        b("Paytm Payments Bank", "PYTMPB PAYTMB PAYTMP", "paytmbank.com"),
        b("Airtel Payments Bank", "AIRBNK APBLTD AIRPBL", "airtelbank.com"),
        b("India Post Payments Bank", "IPPBNK IPPBSM IPPBLT", "ippbonline.com"),
        b("Fino Payments Bank", "FINOPB FINOBK", "finobank.com"),
        b("Jio Payments Bank", "JIOPBL JIOPAY", "jiopaymentsbank.com"),
        // Co-operative banks.
        b("Nagrik Sahakari Bank", "NAGRIK NAGRIKB NAGRKB NSBANK NSBLTD NSBGWL NSBMPL NSBIDR NSBAHM NGRKSB NAGSBK NSBSMS", "nagriksahakaribank.com nagrikbank.com nagriksahkaribank.com"),
        b("Saraswat Bank", "SARBNK SARSWT SRSWAT", "saraswatbank.com"),
        b("Cosmos Bank", "COSMOS COSBNK COSMBK", "cosmosbank.com"),
        b("SVC Bank", "SVCBNK SVCBAN", "svcbank.com"),
        b("Abhyudaya Bank", "ABHYUD ABHYBK", "abhyudayabank.co.in"),
        b("TJSB Sahakari Bank", "TJSBNK TJSBSM", "tjsb.in"),
        b("Bharat Co-operative Bank", "BCBLTD BHARBK", "bharatbank.com"),
        b("Kalupur Commercial Co-operative Bank", "KALUPR KALPUR", "kalupurbank.com"),
        b("NKGSB Co-operative Bank", "NKGSBK NKGSBB", "nkgsb.co.in"),
        b("Janata Sahakari Bank", "JSBLTD JANSBK", "janatabank.com"),
        b("Apna Sahakari Bank", "APNABK APNSBK", "apnabank.co.in"),
    )
}

/** One [ListedBank] as a parser: no templates, so everything is read by the shared extractors. */
class ListedBankParser(private val bank: ListedBank, config: ParserConfig = ParserConfig()) : BaseBankParser(config) {
    override val bankName: String = bank.name
    override val smsHeaders: Set<String> = bank.headers
    override val emailDomains: Set<String> = bank.domains
}
