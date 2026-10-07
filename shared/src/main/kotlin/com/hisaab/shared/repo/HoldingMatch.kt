package com.hisaab.shared.repo

import com.hisaab.parser.model.HoldingKind
import com.hisaab.parser.statement.InvestmentParser
import com.hisaab.parser.statement.PortfolioSummary
import java.util.Locale

/**
 * Whether two holdings are one investment read from different places: a CAS (ISIN), a Groww or broker holdings table
 * ("MF:<scheme>", "STOCK:<name>") and SIP order emails ("MF:<scheme>") name the same fund differently. One of them is
 * kept, so Equity and Retirement count each investment once.
 */
object HoldingMatch {
    /** A holding's identity: what [same] compares. */
    data class Key(val kind: HoldingKind, val identifier: String, val name: String)

    private val ISIN = Regex("""^[A-Z]{2}[A-Z0-9]{9}\d$""")

    /** Words that say nothing about which fund or company it is. */
    private val STOP = setOf(
        "direct", "dir", "plan", "growth", "gr", "g", "option", "opt", "fund", "funds", "the", "scheme", "dp", "gw", "mf", "mutual",
        "ltd", "limited", "inc", "corp", "corporation", "co", "company", "equity", "shares", "share", "eq", "sh", "of", "and", "a", "nse", "bse",
    )

    /** Words that make two otherwise equal names different investments: the plan or payout kind, a share class. */
    private val MARKERS = setOf("regular", "reg", "idcw", "dividend", "div", "payout", "reinvestment", "bonus", "dvr", "pp", "partly", "paid", "segregated")

    /** Words a longer name may carry beyond the shorter one: the AMC's name ("PPFAS Mutual Fund - Parag Parikh ..."). */
    private val AMC_WORDS = setOf(
        "ppfas", "hdfc", "icici", "prudential", "sbi", "axis", "kotak", "mahindra", "nippon", "india", "aditya", "birla", "sun", "life", "uti",
        "dsp", "tata", "mirae", "asset", "franklin", "templeton", "invesco", "motilal", "oswal", "quant", "canara", "robeco", "edelweiss",
        "hsbc", "idfc", "bandhan", "pgim", "baroda", "bnp", "paribas", "lic", "union", "jm", "financial", "navi", "groww", "whiteoak",
        "capital", "samco", "zerodha", "helios", "nj", "trust", "iti", "quantum", "manulife", "shriram", "amc", "investment", "managers",
        "management", "company", "nippon", "reliance", "l", "t", "lt", "360", "one", "old", "bridge", "trustee",
    )

    /** "MH/BAN/0012345/000/0001234" and "MHBAN00123450000001234": the same EPF member id. */
    fun normalisedId(id: String): String = if (id.startsWith("EPF:") && id != InvestmentParser.EPF_DEFAULT) {
        "EPF:" + InvestmentParser.epfMemberKey(id.removePrefix("EPF:"))
    } else id

    fun isAggregate(id: String): Boolean = id.startsWith(PortfolioSummary.AGGREGATE)

    /** Name words that identify the investment, lower case. */
    fun words(name: String): Set<String> = name.lowercase(Locale.ROOT)
        .replace("&", " and ")
        .replace(Regex("""[^a-z0-9 ]"""), " ")
        .split(' ').filter { it.isNotEmpty() && it !in STOP }
        .toSet()

    private fun family(k: HoldingKind): Set<String> = when (k) {
        HoldingKind.MUTUAL_FUND -> setOf("fund")
        HoldingKind.ETF -> setOf("fund", "stock")
        HoldingKind.STOCK, HoldingKind.BOND -> setOf("stock")
        // Gold is a fund of funds in one statement and an exchange-traded unit in another.
        HoldingKind.GOLD -> setOf("stock", "fund")
        else -> setOf(k.name)
    }

    /** True when [a] and [b] are the same investment. */
    fun same(a: Key, b: Key): Boolean {
        val ia = normalisedId(a.identifier)
        val ib = normalisedId(b.identifier)
        if (ia == ib) return true
        if (isAggregate(ia) || isAggregate(ib)) return false
        if (family(a.kind).intersect(family(b.kind)).isEmpty()) return false
        // Two ISINs, or two ids of a kind with no names to go by (NPS tiers, EPF members), differ when the ids do.
        if (ISIN.matches(ia) && ISIN.matches(ib)) return false
        if (a.kind == HoldingKind.EPF) return ia == InvestmentParser.EPF_DEFAULT || ib == InvestmentParser.EPF_DEFAULT
        if (a.kind == HoldingKind.NPS || a.kind == HoldingKind.PPF || a.kind == HoldingKind.FD) return false
        // A US stock is never an Indian one of the same name.
        if (ia.startsWith("US:") != ib.startsWith("US:") && !ISIN.matches(ia) && !ISIN.matches(ib)) return false
        return sameName(a.name, b.name)
    }

    /** Same words, or the longer name is the shorter one plus the AMC's name; the plan and payout kind must agree. */
    fun sameName(a: String, b: String): Boolean {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return false
        if (wa.filter { it in MARKERS }.toSet() != wb.filter { it in MARKERS }.toSet()) return false
        if (wa == wb) return true
        val (small, large) = if (wa.size <= wb.size) wa to wb else wb to wa
        if (small.size < 2 || !large.containsAll(small)) return false
        return (large - small).all { it in AMC_WORDS }
    }

    /** Of two copies of one investment, the identifier to keep: an ISIN, then an EPF member id, then the first. */
    fun preferredId(a: String, b: String): String = when {
        ISIN.matches(a) -> a
        ISIN.matches(b) -> b
        a == InvestmentParser.EPF_DEFAULT -> normalisedId(b)
        b == InvestmentParser.EPF_DEFAULT -> normalisedId(a)
        else -> normalisedId(a)
    }

    /**
     * Groups of holdings that are one investment, each group in [rows] order. An aggregate ([isAggregate]) is never
     * grouped; [aggregatesCovered] says which aggregates the itemised holdings now replace.
     */
    fun <T> duplicates(rows: List<T>, key: (T) -> Key): List<List<T>> {
        val groups = ArrayList<MutableList<T>>()
        for (r in rows) {
            val k = key(r)
            if (isAggregate(k.identifier)) continue
            val g = groups.firstOrNull { grp -> same(key(grp.first()), k) }
            if (g != null) g += r else groups += mutableListOf(r)
        }
        return groups.filter { it.size > 1 }
    }

    /**
     * Aggregates ("Mutual funds (INDmoney)") made redundant by itemised holdings of the same class: funds itemised make
     * the MF aggregate redundant; Indian stocks the stocks aggregate; US stocks the US aggregate.
     */
    fun aggregatesCovered(itemised: List<Key>): Set<String> {
        val out = HashSet<String>()
        for (k in itemised) {
            if (isAggregate(k.identifier)) continue
            when {
                k.identifier.startsWith("US:") || (ISIN.matches(k.identifier) && !k.identifier.startsWith("IN")) -> out += "US"
                k.kind == HoldingKind.MUTUAL_FUND -> out += "MF"
                k.kind == HoldingKind.STOCK || k.kind == HoldingKind.ETF -> out += "STOCKS"
            }
        }
        return out
    }

    /** The class an aggregate identifier stands for: "MF", "STOCKS" or "US". */
    fun aggregateClass(id: String): String = id.substringAfterLast(':')
}
