package com.hisaab.parser.rules

import com.hisaab.parser.text.Guarded
import com.hisaab.parser.text.rx

/**
 * Decides whether a normalized message is not a completed transaction: an OTP, promotion,
 * failed or declined attempt, reminder or bill, future debit, mandate setup, or money request.
 */
object RejectionRules {
    /** Past-tense money movement. Its presence rescues messages that merely mention OTPs or dues. */
    private val PAST_MOVEMENT = rx(
        """\b(?:debited|credited|spent|withdrawn|deposited|received|sent|paid|deducted|transferred|refunded|reversed)\b""",
    )

    private val OTP_WORD = Guarded(
        """\b(?:otp|one[\s-]time[\s-]password|verification\s+code|security\s+code|passcode)\b""",
        "otp", "password", "verification", "security", "passcode",
    )
    private val OTP_CODE = rx(
        """(?:otp|code|password)\s*(?:is|:|-)?\s*\d{4,8}\b|\b\d{4,8}\s+is\s+(?:your\s+|the\s+)?(?:otp|one[\s-]time|code|verification)|""" +
            """\b(?:otp|code)\s+for\b|\buse\s+(?:otp\s+)?\d{4,8}\b""",
    )

    private val FAILED = Guarded(
        """\b(?:failed|failure|declined|unsuccessful|not\s+successful|could\s+not\s+be\s+(?:processed|completed)|""" +
            """(?:has\s+been|was|is)\s+rejected|insufficient\s+(?:funds|balance|bal)|incorrect\s+pin|invalid\s+pin|""" +
            """returned\s+unpaid|dishonou?red|bounced)\b""",
        "fail", "declin", "unsuccess", "success", "could", "reject", "insufficient", "pin", "returned", "dishono", "bounce",
    )
    private val REVERSAL = rx("""\b(?:reversed|refunded|reversal|refund)\b""")
    private val CREDITED = rx("""\bcredited\b""")

    private val FUTURE = Guarded(
        """\bwill\s+be\s+(?:auto[\s-]?)?(?:debited|deducted|charged|credited|processed|presented|paid|transferred)\b|""" +
            """\b(?:is\s+scheduled|scheduled\s+(?:for|on)|upcoming)\b""",
        "will", "scheduled", "upcoming",
    )
    private val DUE = Guarded(
        """\b(?:is\s+due|due\s+(?:on|by|date)|payment\s+due|(?:amount|amt)\s+due|min(?:imum)?\.?\s+(?:amount|amt)|overdue|""" +
            """bill\s+(?:is\s+)?generated|statement\s+(?:is\s+)?(?:generated|ready)|statement\s+for|pay\s+now|""" +
            """payment\s+reminder|gentle\s+reminder|reminder)\b""",
        "due", "min", "bill", "statement", "pay", "reminder",
    )
    private val SETTLED = rx("""\b(?:debited|credited|spent|withdrawn|deposited|received)\b""")

    private val MANDATE_SETUP = Guarded(
        """\b(?:e-?mandate|mandate|auto[\s-]?pay|standing\s+instruction|e-?nach)\b.{0,80}?""" +
            """\b(?:registered|created|set\s*up|setup|activated|approved|revoked|cancelled|canceled|modified|paused)\b|""" +
            """\b(?:set\s*up|created|registered|activated)\s+(?:an?\s+|your\s+)?(?:e-?mandate|mandate|auto[\s-]?pay)\b""",
        "mandate", "auto", "standing", "nach",
    )
    private val COLLECT = Guarded(
        """\b(?:has\s+requested|requested\s+(?:money|INR|payment)|collect\s+request|payment\s+request)\b""",
        "request",
    )

    private val PROMO = Guarded(
        """\b(?:pre[\s-]?approved|apply\s+now|you\s+are\s+eligible|eligible\s+for|avail\s+(?:now|the\s+offer)|get\s+up\s*to|""" +
            """limited[\s-]period|exclusive\s+offer|special\s+offer|click\s+here|congratulations)\b""",
        "approved", "apply", "eligible", "avail", "get up", "limited", "offer", "click", "congratulations",
    )

    fun shouldReject(text: String, lower: String = text.lowercase()): Boolean = reasonFor(text, lower) != null

    /** The rule that rejects [text], or null when it may be a transaction. Exposed for tests and debugging. */
    fun reasonFor(text: String, lower: String = text.lowercase()): String? {
        if (OTP_WORD.containsMatchIn(text, lower) && (OTP_CODE.containsMatchIn(text) || !PAST_MOVEMENT.containsMatchIn(text))) return "otp"
        if (FAILED.containsMatchIn(text, lower) && !(REVERSAL.containsMatchIn(text) && CREDITED.containsMatchIn(text))) return "failed"
        if (FUTURE.containsMatchIn(text, lower)) return "future"
        if (MANDATE_SETUP.containsMatchIn(text, lower)) return "mandate"
        if (COLLECT.containsMatchIn(text, lower)) return "collect"
        if (PROMO.containsMatchIn(text, lower)) return "promo"
        if (DUE.containsMatchIn(text, lower) && !SETTLED.containsMatchIn(text)) return "reminder"
        return null
    }
}
