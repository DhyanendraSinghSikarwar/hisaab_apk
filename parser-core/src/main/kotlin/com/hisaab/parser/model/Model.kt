package com.hisaab.parser.model

/** Where a message came from. */
enum class Source { SMS, EMAIL }

enum class TransactionType {
    DEBIT, CREDIT, TRANSFER, INVESTMENT;

    /**
     * Money direction, used in the dedup hash instead of the raw type. The SMS and the email for one
     * transaction can word it differently (one says "ZERODHA", the other only "ACH"), so one may become
     * INVESTMENT while the other stays DEBIT. Both still move money out, so they must hash the same.
     */
    val direction: String
        get() = when (this) {
            DEBIT, INVESTMENT -> "OUT"
            CREDIT -> "IN"
            TRANSFER -> "XFER"
        }
}

enum class AccountKind { ACCOUNT, CARD }

enum class Channel { UPI, IMPS, NEFT, RTGS, CARD, ATM, AUTO_DEBIT, OTHER }

enum class Category(val label: String) {
    FOOD("Food & Dining"),
    GROCERIES("Groceries"),
    TRANSPORT("Transport"),
    FUEL("Fuel"),
    SHOPPING("Shopping"),
    BILLS("Bills & Utilities"),
    ENTERTAINMENT("Entertainment"),
    TRAVEL("Travel"),
    HEALTH("Health"),
    EDUCATION("Education"),
    RENT("Rent"),
    INSURANCE("Insurance"),
    EMI_LOAN("EMI & Loans"),
    INVESTMENT("Investment"),
    CASH("Cash Withdrawal"),
    SALARY("Salary"),
    INCOME("Income"),
    REFUND("Refund"),
    TRANSFER("Transfer"),
    OTHER("Other"),
}

/**
 * One bank transaction pulled out of an SMS or an email.
 *
 * Amounts are kept in minor units (paise) so equality and hashing are exact.
 * [transactionTime] is epoch millis. [hasExplicitTime] is false when the message gave only a date
 * and the time of day was taken from the message timestamp.
 */
data class ParsedTransaction(
    val amountMinor: Long,
    val currency: String,
    val type: TransactionType,
    val bankName: String,
    val accountLast4: String?,
    val accountKind: AccountKind,
    val merchant: String?,
    val upiId: String?,
    val referenceNumber: String?,
    val channel: Channel,
    val balanceMinor: Long?,
    val availableLimitMinor: Long?,
    val transactionTime: Long,
    val hasExplicitTime: Boolean,
    val category: Category,
    val source: Source,
    val sender: String,
    val messageTimestamp: Long,
    val confidence: Float,
    val transactionHash: String,
)
