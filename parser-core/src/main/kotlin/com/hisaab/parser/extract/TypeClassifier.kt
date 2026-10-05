package com.hisaab.parser.extract

import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import com.hisaab.parser.text.Guarded
import com.hisaab.parser.text.rx

object TypeClassifier {
    private val DEBIT = rx(
        """\b(?:debited|debit\s+(?:by|for|of|alert)|spent|withdrawn|withdrawal|paid|sent|purchased?|deducted|charged|""" +
            """transferred\s+to|txn\s+of|used\s+(?:for|at|on)|thank\s+you\s+for\s+using|""" +
            // Card alerts: "Transaction of Rs.899 on Kotak Credit Card", "Txn Rs.1,250.00 On HDFC Bank Card".
            """(?:transaction|trxn|txn)\s+(?:of\s+)?(?:INR|USD|EUR|GBP|AED|SGD)|made\s+a\s+(?:transaction|purchase|payment))\b""",
    )
    private val CREDIT = rx("""\b(?:credited|credit\s+(?:by|of|alert|with)|received|deposited|refund(?:ed)?|reversed|reversal)\b""")

    /** A credit-card bill payment or a move between the user's own accounts: neither income nor spend. */
    private val TRANSFER = Guarded(
        """\bpayment\b.{0,60}?\breceived\b.{0,60}?card\b|\breceived\s+(?:your\s+)?payment\b.{0,70}?card\b|""" +
            """\bthank\s+you\s+for\s+(?:your\s+|the\s+)?payment\b|\bself[\s-]transfer\b|\bown\s+account\b|""" +
            """\bbetween\s+your\s+(?:own\s+)?accounts\b|""" +
            // Bank side of a card bill: "debited ... towards your HDFC Bank Credit Card XX5678", "CC bill payment".
            """\btowards\s+(?:your\s+)?(?:[A-Za-z]+\s+){0,3}credit\s+card\b|\b(?:credit\s+card|cc)\s+(?:bill\s+)?payment\b|\bcard\s+bill\b""",
        "payment", "self", "own account", "between your", "towards", "card bill",
    )
    private val INVESTMENT = Guarded(
        """\b(?:mutual\s+fund|MF|SIP|zerodha|groww|upstox|kuvera|iccl|indian\s+clearing|nse\s+clearing|clearing\s+corp|""" +
            """cams|kfin(?:tech)?|smallcase|angel\s+one|paytm\s+money|ppf|nps)\b""",
        "mutual", "mf", "sip", "zerodha", "groww", "upstox", "kuvera", "iccl", "clearing", "cams", "kfin", "smallcase",
        "angel", "paytm", "ppf", "nps",
    )

    fun classify(text: String, hint: TransactionType? = null, lower: String = text.lowercase()): TransactionType? {
        if (TRANSFER.containsMatchIn(text, lower)) return TransactionType.TRANSFER
        if (hint != null) return hint
        // The earlier verb wins, so the credit search only needs the text before the first debit verb.
        val debit = DEBIT.find(text)?.range?.first
        val creditScope = if (debit == null) text else text.substring(0, minOf(text.length, debit + CREDIT_OVERLAP))
        val credit = CREDIT.find(creditScope)?.range?.first
        return when {
            debit == null && credit == null -> null
            credit == null -> TransactionType.DEBIT
            debit == null || credit < debit -> TransactionType.CREDIT
            else -> TransactionType.DEBIT
        }
    }

    // Lets a credit verb that starts at the debit position still be seen whole.
    private const val CREDIT_OVERLAP = 12

    fun looksLikeInvestment(text: String, lower: String = text.lowercase()): Boolean = INVESTMENT.containsMatchIn(text, lower)
}

object ChannelDetector {
    // "Withdrawn Rs.10000 From HDFC Bank Card x2139 At +NALLAGANDLA" is a debit-card cash withdrawal.
    private val ATM = Guarded("""\bATM\b|\bcash\s+withdrawal\b|\bwithdrawn\b""", "atm", "cash", "withdrawn")
    private val UPI = Guarded(
        """\bUPI\b|\bVPA\b|@(?:ok\w+|ybl|ibl|axl|paytm|upi|icici|hdfcbank|sbi|axisbank|apl|ptyes|ptsbi|pthdfc|ptaxis|kotak|idfcbank)\b""",
        "upi", "vpa", "@",
    )
    private val IMPS = Guarded("""\bIMPS\b""", "imps")
    private val NEFT = Guarded("""\bNEFT\b""", "neft")
    private val RTGS = Guarded("""\bRTGS\b""", "rtgs")
    private val AUTO = Guarded("""\b(?:ACH|NACH|ECS|mandate|auto[\s-]?pay|standing\s+instruction)\b""", "ach", "ecs", "mandate", "auto", "standing")

    fun detect(text: String, lower: String = text.lowercase()): Channel = when {
        ATM.containsMatchIn(text, lower) -> Channel.ATM
        AUTO.containsMatchIn(text, lower) -> Channel.AUTO_DEBIT
        UPI.containsMatchIn(text, lower) -> Channel.UPI
        IMPS.containsMatchIn(text, lower) -> Channel.IMPS
        NEFT.containsMatchIn(text, lower) -> Channel.NEFT
        RTGS.containsMatchIn(text, lower) -> Channel.RTGS
        lower.contains("card") -> Channel.CARD
        else -> Channel.OTHER
    }
}
