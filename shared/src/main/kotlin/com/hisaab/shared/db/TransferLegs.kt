package com.hisaab.shared.db

/**
 * A transfer between the user's own accounts or cards, entered by hand, is stored as two TRANSFER rows (one per side).
 * Their direction is kept in the source message id of the row's MANUAL source, so no column is needed: [OUT_PREFIX]
 * marks the leg money left, [IN_PREFIX] the leg it arrived on. The id after the prefix is shared by both legs.
 */
object TransferLegs {
    const val OUT_PREFIX = "xfer-out:"
    const val IN_PREFIX = "xfer-in:"

    /** SQL (rows aliased `t`): a transfer leg's signed rupee value on a bank account, 0 when its direction is not known. */
    const val SIGNED_SQL = "(CASE WHEN EXISTS (SELECT 1 FROM transaction_sources xs WHERE xs.transactionId = t.id AND xs.sourceMessageId LIKE 'xfer-out:%') THEN -COALESCE(t.inrMinor, 0) " +
        "WHEN EXISTS (SELECT 1 FROM transaction_sources xs WHERE xs.transactionId = t.id AND xs.sourceMessageId LIKE 'xfer-in:%') THEN COALESCE(t.inrMinor, 0) " +
        // A credit card bill paid from this account (see CardBill): the money left it.
        "WHEN t.note LIKE 'Credit card bill%' THEN -COALESCE(t.inrMinor, 0) ELSE 0 END)"
}
