package com.hisaab.parser.statement

/** What a column of a holdings sheet holds, whichever platform exported it. */
enum class HoldingField { NAME, SYMBOL, ISIN, AMC, CATEGORY, SUB_CATEGORY, FOLIO, SOURCE, UNITS, AVG_PRICE, INVESTED, CURRENT, PRICE, GAIN, GAIN_PCT, XIRR, AS_OF }

/**
 * Header synonyms for holdings sheets (Groww, Zerodha, Kuvera, INDmoney, brokers, CAMS/KFin ...), one registry for all
 * platforms. A header is normalised ([key]: lower case, brackets with units dropped, only letters and digits kept, "%"
 * as "pct") and looked up in [SYNONYMS]; a header not listed falls back to [FRAGMENTS]. The first column of a field wins.
 */
object HoldingColumns {

    private val SYNONYMS: Map<HoldingField, Set<String>> = mapOf(
        HoldingField.NAME to setOf(
            "schemename", "scheme", "fundname", "fund", "stockname", "security", "securityname", "instrument", "instrumentname",
            "companyname", "company", "holdingname", "holding", "name", "assetname", "asset", "scripname", "scrip", "script", "productname", "schemenameamc",
        ),
        HoldingField.SYMBOL to setOf("symbol", "ticker", "tradingsymbol", "scripsymbol", "stocksymbol", "scripcode", "tickersymbol"),
        HoldingField.ISIN to setOf("isin", "isincode", "isinno", "isinnumber", "isinnum"),
        HoldingField.AMC to setOf("amc", "amcname", "fundhouse", "fundhousename", "assetmanagementcompany", "mutualfund"),
        HoldingField.CATEGORY to setOf("category", "assetclass", "assettype", "schemetype", "schemecategory", "sector", "type"),
        HoldingField.SUB_CATEGORY to setOf("subcategory", "subcat", "schemesubcategory", "subassetclass"),
        HoldingField.FOLIO to setOf("foliono", "folio", "folionumber", "folioid", "folionos", "foliono"),
        HoldingField.SOURCE to setOf("source", "platform", "broker", "distributor", "channel"),
        HoldingField.UNITS to setOf(
            "units", "unit", "closingunits", "closingunitbalance", "unitbalance", "balanceunits", "availableunits", "totalunits", "currentunits",
            "quantity", "qty", "quantityavailable", "netquantity", "netqty", "holdingqty", "holdingquantity", "totalquantity", "totalqty", "currentquantity", "openquantity", "shares", "noofshares",
        ),
        HoldingField.AVG_PRICE to setOf(
            "averageprice", "avgprice", "avgcost", "averagecost", "averagebuyprice", "avgbuyprice", "averagenav", "avgnav", "buyaverage", "buyavg",
            "avgcostprice", "averagecostprice", "purchasenav", "buyprice", "costprice", "unitcost", "averagepurchaseprice", "avgpurchaseprice",
        ),
        HoldingField.INVESTED to setOf(
            "investedvalue", "investedamount", "invested", "investment", "investmentvalue", "investmentamount", "totalinvestment", "totalinvestments", "totalinvested",
            "totalcost", "costvalue", "cost", "purchasevalue", "purchaseamount", "buyvalue", "buyamount", "amountinvested", "investedamt", "valueatcost",
            "holdingcost", "acquisitioncost", "costofinvestment", "totalbuyvalue", "totalpurchasecost",
        ),
        HoldingField.CURRENT to setOf(
            "currentvalue", "currentamount", "currentamt", "curval", "marketvalue", "currentmarketvalue", "valuation", "closingvalue", "presentvalue",
            "current", "value", "totalvalue", "latestvalue", "marketval", "valueatmarketprice", "valueatmarket", "openvalue", "portfoliovalue", "currentinvestmentvalue",
        ),
        HoldingField.PRICE to setOf(
            "nav", "currentnav", "latestnav", "ltp", "lastprice", "closingprice", "previousclosingprice", "prevclosingprice", "previousclose", "closeprice",
            "marketprice", "currentprice", "price", "lasttradedprice", "cmp", "latestprice", "currentmarketprice", "navperunit",
        ),
        HoldingField.GAIN to setOf(
            "returns", "return", "gain", "gains", "gainloss", "profitloss", "profitandloss", "pl", "pnl", "unrealizedpl", "unrealisedpl", "unrealizedgain", "unrealisedgain",
            "unrealizedprofitloss", "unrealisedprofitloss", "unrealizedgainloss", "unrealisedgainloss", "totalreturns", "absolutereturns", "absolutereturn", "netpl",
            "totalpl", "overallgain", "overallgainloss", "gainlossamount", "profitlossamount", "profit", "unrealized", "unrealised", "unrealizedprofit", "unrealisedprofit",
        ),
        HoldingField.GAIN_PCT to setOf(
            "returnspct", "returnpct", "returnspercent", "returnspercentage", "plpct", "pnlpct", "unrealizedplpct", "unrealisedplpct", "profitlosspct", "gainlosspct",
            "gainpct", "absolutereturnspct", "absolutereturnpct", "totalreturnpct", "totalreturnspct", "gainlosspercent", "percentagereturn", "netchg", "netchange",
        ),
        HoldingField.XIRR to setOf("xirr", "xirrpct", "annualisedreturns", "annualizedreturns", "annualisedreturn", "annualizedreturn", "cagr", "irr", "xirrreturns"),
        HoldingField.AS_OF to setOf("asondate", "asofdate", "valuationdate", "navdate", "holdingdate", "asof", "asat"),
    )

    /** Parts of a header that map it when it is not listed; checked in this order, so "avg price" is not read as a price. */
    private val FRAGMENTS: List<Pair<HoldingField?, List<String>>> = listOf(
        null to listOf("longterm", "pledged", "collateral", "discrepant", "daychange", "daychg", "daypl", "todays", "weight", "allocation", "ofportfolio", "dayschange"),
        HoldingField.AVG_PRICE to listOf("avgprice", "avgcost", "averageprice", "averagecost", "avgbuy", "averagebuy", "averagenav", "avgnav"),
        HoldingField.XIRR to listOf("xirr", "cagr"),
        HoldingField.INVESTED to listOf("invested", "investment", "buyvalue", "costvalue", "purchasevalue", "totalcost", "valueatcost"),
        HoldingField.CURRENT to listOf("currentvalue", "marketvalue", "closingvalue", "curval", "presentvalue", "currentamount", "valuation", "valueatmarket"),
        HoldingField.PRICE to listOf("ltp", "closingprice", "currentprice", "marketprice", "currentnav", "latestnav"),
        HoldingField.GAIN_PCT to listOf("returnspercent", "gainpercent", "profitpercent", "returnspct", "gainpct", "profitpct", "plpct"),
        HoldingField.GAIN to listOf("gain", "profit", "returns", "unrealized", "unrealised"),
        HoldingField.UNITS to listOf("units", "quantity", "qty"),
        HoldingField.FOLIO to listOf("folio"),
        HoldingField.ISIN to listOf("isin"),
        HoldingField.AMC to listOf("amc"),
        HoldingField.CATEGORY to listOf("category"),
        HoldingField.SOURCE to listOf("platform"),
        HoldingField.NAME to listOf("schemename", "fundname", "stockname", "securityname", "companyname"),
    )

    private val BRACKET = Regex("""\(([^)]*)\)""")
    private val NON_ALNUM = Regex("""[^a-z0-9]""")

    /** A header cell reduced to letters and digits: "Unrealized P&L Pct." -> "unrealizedplpct"; "Returns (%)" -> "returnspct". */
    fun key(header: String): String {
        var s = header.lowercase().replace(' ', ' ')
        s = BRACKET.replace(s) { m -> if ('%' in m.groupValues[1]) " pct" else "" }
        s = s.replace("%", " pct ")
        return NON_ALNUM.replace(s, "")
    }

    /** The field a header names, or null. */
    fun fieldOf(header: String): HoldingField? {
        val k = key(header)
        if (k.isEmpty() || k.length > 40) return null
        SYNONYMS.entries.firstOrNull { k in it.value }?.let { return it.key }
        for ((field, parts) in FRAGMENTS) if (parts.any { it in k }) return field
        return null
    }

    /** Column index per field in a header row; the first column of a field wins. */
    fun map(cells: List<String>): Map<HoldingField, Int> {
        val out = LinkedHashMap<HoldingField, Int>()
        for ((i, c) in cells.withIndex()) {
            val f = fieldOf(c) ?: continue
            if (f !in out) out[f] = i
        }
        return out
    }

    /**
     * True when [cells] head a holdings table: at least three known fields, a name (name, symbol or ISIN), and enough to
     * work out a current value (a value, units with a price, or invested with a gain). A table in dollars is left to the
     * text reader, which converts at the statement's rate.
     */
    fun isHeader(cells: List<String>): Boolean {
        if (cells.any { it.contains('$') || key(it).let { k -> k == "usd" || k.endsWith("usd") } }) return false
        val m = map(cells)
        if (m.size < 3) return false
        val named = HoldingField.NAME in m || HoldingField.SYMBOL in m || HoldingField.ISIN in m
        val f = m.keys
        val value = HoldingField.CURRENT in f || (HoldingField.UNITS in f && HoldingField.PRICE in f) || (HoldingField.INVESTED in f && HoldingField.GAIN in f)
        // A table of dated transactions (units x NAV = amount) is not a holding unless it states a current value.
        if (HoldingField.CURRENT !in f && cells.any { key(it).let { k -> k.endsWith("date") && k !in SYNONYMS.getValue(HoldingField.AS_OF) || k == "transactiontype" } }) return false
        // Every cell of a data row is a figure or a long name; a header is short words.
        return named && value && cells.none { it.length > 60 }
    }
}
