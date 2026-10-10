package com.hisaab.parser.statement

import com.hisaab.parser.text.rx

/**
 * The apps and brokers whose holdings exports are read, with the header row each is known to use. [HoldingColumns]
 * reads any of them by column names; the profiles name the platform from the sender and document the layouts (they are
 * the fixtures of the profile tests).
 *
 * [Profile.verified] is true where the layout was confirmed from a real export or the platform's own page; the others
 * follow the platform's usual export and are matched by column names, so a renamed column still maps.
 */
object HoldingProfiles {

    class Profile(val platform: String, val sender: Regex, val header: List<String>, val verified: Boolean, val label: String = platform)

    val ALL: List<Profile> = listOf(
        Profile("Groww", rx("""groww"""), listOf("Scheme Name", "AMC", "Category", "Sub-category", "Folio No.", "Source", "Units", "Invested Value", "Current Value", "Returns", "XIRR"), true, "Groww mutual funds"),
        Profile("Groww", rx("""groww"""), listOf("Stock Name", "ISIN", "Quantity", "Average buy price", "Buy value", "Closing price", "Closing value", "Unrealised P&L"), true, "Groww stocks"),
        Profile(
            "Zerodha", rx("""zerodha|kite\.|\bcoin\b"""),
            listOf("Symbol", "ISIN", "Sector", "Quantity Available", "Quantity Discrepant", "Quantity Long Term", "Quantity Pledged (Margin)", "Quantity Pledged (Loan)", "Average Price", "Previous Closing Price", "Unrealized P&L", "Unrealized P&L Pct."),
            true, "Zerodha Console",
        ),
        Profile("Zerodha", rx("""zerodha|\bcoin\b"""), listOf("Instrument", "Qty.", "Avg. cost", "LTP", "Invested", "Cur. val", "P&L", "Net chg.", "Day chg."), false, "Zerodha Coin"),
        Profile("Kuvera", rx("""kuvera"""), listOf("Scheme Name", "Folio Number", "Units", "Average NAV", "Invested Amount", "Current NAV", "Current Value", "Gain/Loss", "XIRR"), false),
        Profile("INDmoney", rx("""indmoney"""), listOf("Fund Name", "Folio Number", "Units", "Invested Value", "Current Value", "Returns", "XIRR"), false),
        Profile("Paytm Money", rx("""paytm"""), listOf("Scheme Name", "Folio No", "Units", "Invested Amount", "Current Value", "Returns (%)", "XIRR"), false),
        Profile("ET Money", rx("""etmoney"""), listOf("Scheme Name", "Folio Number", "Units", "Invested Amount", "Current Value", "Absolute Returns", "XIRR"), false),
        Profile("Upstox", rx("""upstox"""), listOf("Company Name", "ISIN", "Quantity", "Average Price", "Last Traded Price", "Invested Value", "Current Value", "P&L", "P&L %"), false),
        Profile("Angel One", rx("""angel"""), listOf("Symbol", "ISIN", "Quantity", "Average Price", "LTP", "Invested Value", "Current Value", "Unrealized P&L", "Net Change %"), false),
        Profile("Dhan", rx("""\bdhan"""), listOf("Trading Symbol", "ISIN", "Quantity", "Avg Cost Price", "LTP", "Total Value", "P&L", "P&L %"), false),
        Profile("ICICI Direct", rx("""icici ?direct|icicisecurities"""), listOf("Stock Name", "Stock Symbol", "ISIN", "Qty", "Average Cost Price", "Current Market Price", "Value At Cost", "Value At Market Price", "Unrealized P&L"), false),
        Profile("HDFC Securities", rx("""hdfcsec"""), listOf("Scrip Name", "ISIN", "Qty", "Avg Price", "CMP", "Buy Value", "Market Value", "Unrealised Profit/Loss"), false),
        Profile("Kotak Securities", rx("""kotak ?sec|kotakneo"""), listOf("Security Name", "ISIN", "Quantity", "Average Price", "Closing Price", "Total Cost", "Market Value", "Unrealised P&L"), false),
        Profile("MFCentral", rx("""mfcentral"""), listOf("Scheme Name", "ISIN", "Folio No", "AMC", "Closing Unit Balance", "NAV", "Cost Value", "Market Value"), false),
        Profile("CAMS", rx("""cams"""), listOf("Scheme Name", "Folio No", "ISIN", "Closing Unit Balance", "NAV", "Cost Value (INR)", "Market Value (INR)"), false),
        Profile("KFintech", rx("""kfin|karvy"""), listOf("Fund Name", "Scheme Name", "Folio", "Units", "NAV", "Cost Value", "Current Value"), false),
    )

    /** The platform a sender (or file name) belongs to, or null. */
    fun platformOf(sender: String): String? = ALL.firstOrNull { it.sender.containsMatchIn(sender) }?.platform
}
