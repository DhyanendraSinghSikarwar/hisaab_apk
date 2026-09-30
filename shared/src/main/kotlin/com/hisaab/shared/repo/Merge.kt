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
    merchant = merchant ?: tx.merchant,
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
    timestamp = if (!hasExplicitTime && tx.hasExplicitTime) tx.transactionTime else timestamp,
    hasExplicitTime = hasExplicitTime || tx.hasExplicitTime,
    confidence = maxOf(confidence, tx.confidence),
)

fun TransactionEntity.mergedWith(other: TransactionEntity): TransactionEntity = copy(
    merchant = merchant ?: other.merchant,
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
