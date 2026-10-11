package com.hisaab.shared.repo

import com.hisaab.parser.model.Category
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity

/**
 * Folds a duplicate message into the stored record without losing anything: fields the record is
 * missing are filled from the message (the merchant usually from the email, the balance from the SMS).
 * Fields already set are kept, except where the message is strictly more specific.
 */
fun TransactionEntity.mergedWith(tx: ParsedTransaction): TransactionEntity = copy(
    // A payment app often drops the paise ("₹144" for 144.93): the message that has them is the exact figure.
    amountMinor = if (amountMinor % 100 == 0L && tx.amountMinor % 100 != 0L && tx.amountMinor / 100 == amountMinor / 100 &&
        currency == tx.currency) tx.amountMinor else amountMinor,
    merchant = betterMerchant(merchant, tx.merchant),
    upiId = upiId ?: tx.upiId,
    referenceNumber = referenceNumber ?: tx.referenceNumber,
    accountLast4 = longerOf(accountLast4, tx.accountLast4),
    balanceMinor = balanceMinor ?: tx.balanceMinor,
    availableLimitMinor = availableLimitMinor ?: tx.availableLimitMinor,
    // "Investment" is a more specific reading of the same outgoing money.
    type = if (type == TransactionType.DEBIT && tx.type == TransactionType.INVESTMENT) tx.type else type,
    category = if (category == Category.OTHER || category == Category.INCOME) {
        tx.category.takeIf { it != Category.OTHER && it != Category.INCOME } ?: category
    } else {
        category
    },
    // Prefer a time printed in a message over the time a message arrived.
    timestamp = mergedTime(tx),
    hasExplicitTime = hasExplicitTime || tx.hasExplicitTime,
    confidence = maxOf(confidence, tx.confidence),
)

private const val SAME_PAYMENT_MILLIS = 30 * 60 * 1000L

/**
 * The time of the merged record: a printed time beats an arrival time. A message that states a balance or limit
 * is the reference point for it, so the record never sits after that message's time (the payment would
 * otherwise be applied on top of a balance that already includes it).
 */
private fun TransactionEntity.mergedTime(tx: ParsedTransaction): Long {
    val chosen = if (!hasExplicitTime && tx.hasExplicitTime) tx.transactionTime else timestamp
    val states = tx.balanceMinor != null || tx.availableLimitMinor != null
    return if (states && tx.source.name != "STATEMENT" && chosen > tx.transactionTime && chosen - tx.transactionTime <= SAME_PAYMENT_MILLIS) tx.transactionTime else chosen
}

/**
 * The better of two merchant names for one payment: a real payee name beats a UPI id or a generic phrase,
 * otherwise the stored one stays.
 */
internal fun betterMerchant(stored: String?, incoming: String?): String? {
    if (stored == null) return incoming
    if (incoming == null) return stored
    fun weak(s: String) = '@' in s || s.count(Char::isDigit) > s.length / 2 || com.hisaab.parser.extract.GenericPhrases.isGeneric(s)
    return if (weak(stored) && !weak(incoming)) incoming else stored
}

fun TransactionEntity.mergedWith(other: TransactionEntity): TransactionEntity = copy(
    merchant = betterMerchant(merchant, other.merchant),
    upiId = upiId ?: other.upiId,
    referenceNumber = referenceNumber ?: other.referenceNumber,
    accountLast4 = accountLast4 ?: other.accountLast4,
    accountId = accountId ?: other.accountId,
    balanceMinor = balanceMinor ?: other.balanceMinor,
    availableLimitMinor = availableLimitMinor ?: other.availableLimitMinor,
    note = note ?: other.note,
    confidence = maxOf(confidence, other.confidence),
)

fun ParsedTransaction.toEntity(accountId: Long?, now: Long, hash: String = transactionHash) = TransactionEntity(
    amountMinor = amountMinor,
    currency = currency,
    type = type,
    bankName = bankName,
    accountLast4 = accountLast4,
    accountKind = accountKind,
    accountId = accountId,
    merchant = merchant,
    upiId = upiId,
    referenceNumber = referenceNumber,
    channel = channel,
    balanceMinor = balanceMinor,
    availableLimitMinor = availableLimitMinor,
    timestamp = transactionTime,
    hasExplicitTime = hasExplicitTime,
    category = category,
    transactionHash = hash,
    confidence = confidence,
    createdAt = now,
)

/** "0123" is more informative than "123" (ICICI's 3-digit mask). */
private fun longerOf(a: String?, b: String?): String? = when {
    a == null -> b
    b == null -> a
    b.length > a.length -> b
    else -> a
}
