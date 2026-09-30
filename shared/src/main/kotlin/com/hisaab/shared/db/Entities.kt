package com.hisaab.shared.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["transactionHash"], unique = true),
        // Keeps findPotentialDuplicates an index range scan instead of a table scan.
        Index(value = ["amountMinor", "accountLast4", "timestamp"]),
        Index(value = ["referenceNumber"]),
        Index(value = ["timestamp"]),
        Index(value = ["accountId"]),
        Index(value = ["needsReview"]),
    ],
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val currency: String,
    val type: TransactionType,
    val bankName: String,
    val accountLast4: String?,
    val accountKind: AccountKind,
    val accountId: Long?,
    val merchant: String?,
    val upiId: String?,
    val referenceNumber: String?,
    val channel: Channel,
    val balanceMinor: Long?,
    val availableLimitMinor: Long?,
    /** When the money moved, epoch millis. */
    val timestamp: Long,
    val hasExplicitTime: Boolean,
    val category: Category,
    val transactionHash: String,
    val confidence: Float,
    /** Set when the dedup layer was not sure; the review screen clears it. */
    val needsReview: Boolean = false,
    val duplicateOfId: Long? = null,
    val reviewReason: String? = null,
    val note: String? = null,
    val createdAt: Long,
)

/** Every message that contributed to a transaction. One transaction can have an SMS and an email. */
@Entity(
    tableName = "transaction_sources",
    indices = [
        Index(value = ["source", "sourceMessageId"], unique = true),
        Index(value = ["transactionId"]),
        Index(value = ["parsedHash"]),
    ],
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class TransactionSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    /** SMS, EMAIL, CSV or MANUAL. */
    val source: String,
    val sourceMessageId: String,
    val sender: String,
    /** The hash this message produced, so a later third copy still matches after a merge. */
    val parsedHash: String,
    /** Kept, capped, for re-parsing when the user splits a merge. Stays on the device. */
    val rawText: String?,
    val receivedAt: Long,
)

/** Gmail message ids already handled, whatever the outcome, so no email is parsed twice. */
@Entity(tableName = "processed_emails")
data class ProcessedEmailEntity(
    @PrimaryKey val messageId: String,
    val processedAt: Long,
    /** PARSED, REJECTED, DUPLICATE, or ERROR. */
    val outcome: String,
    val transactionId: Long?,
)

@Entity(tableName = "accounts", indices = [Index(value = ["bankName", "last4"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val last4: String,
    val kind: AccountKind,
    val nickname: String? = null,
    @ColumnInfo(defaultValue = "NULL") val colorArgb: Int? = null,
    val latestBalanceMinor: Long? = null,
    val availableLimitMinor: Long? = null,
    val balanceUpdatedAt: Long? = null,
    val createdAt: Long,
    /** Balance (or a card's available limit) the user typed in. Optional; see [AccountBalances]. */
    @ColumnInfo(defaultValue = "NULL") val manualBalanceMinor: Long? = null,
    /** When the user set [manualBalanceMinor]; later transactions move it on. */
    @ColumnInfo(defaultValue = "NULL") val manualBalanceAt: Long? = null,
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: Category,
    val monthlyLimitMinor: Long,
)

// Query result shapes.
data class CategoryTotal(val category: Category, val total: Long)
data class DayTotal(val day: Long, val total: Long)
data class MonthTotal(val month: String, val spent: Long, val income: Long)
data class SourceOfTransaction(val transactionId: Long, val source: String)
data class AccountWithActivity(
    val id: Long,
    val bankName: String,
    val last4: String,
    val kind: AccountKind,
    val nickname: String?,
    val colorArgb: Int?,
    val latestBalanceMinor: Long?,
    val availableLimitMinor: Long?,
    val balanceUpdatedAt: Long?,
    val monthSpent: Long,
    val transactionCount: Int,
    val manualBalanceMinor: Long? = null,
    val manualBalanceAt: Long? = null,
    /** Net effect on the balance of the transactions after [manualBalanceAt]: credits minus spends. */
    val changeSinceManual: Long = 0,
) {
    /** A bank account's balance, or a card's available limit. Null when neither the bank nor the user gave one. */
    val currentBalanceMinor: Long?
        get() = AccountBalances.current(kind, latestBalanceMinor, availableLimitMinor, balanceUpdatedAt, manualBalanceMinor, manualBalanceAt, changeSinceManual)

    /** True when [currentBalanceMinor] comes from the balance the user set rather than a bank message. */
    val balanceIsManual: Boolean
        get() = AccountBalances.usesManual(kind, latestBalanceMinor, availableLimitMinor, balanceUpdatedAt, manualBalanceMinor, manualBalanceAt)

    /** When [currentBalanceMinor] was last stated, by the bank or the user. */
    val balanceAsOf: Long?
        get() = if (balanceIsManual) manualBalanceAt else balanceUpdatedAt
}

/**
 * Picks the balance to show. A bank message's balance wins when it is newer than the one the user set;
 * otherwise the user's figure is carried forward by every transaction on the account since then.
 */
object AccountBalances {
    fun usesManual(kind: AccountKind, reportedBalance: Long?, reportedLimit: Long?, reportedAt: Long?, manual: Long?, manualAt: Long?): Boolean {
        if (manual == null || manualAt == null) return false
        val reported = if (kind == AccountKind.CARD) reportedLimit else reportedBalance
        return reported == null || reportedAt == null || manualAt >= reportedAt
    }

    fun current(
        kind: AccountKind, reportedBalance: Long?, reportedLimit: Long?, reportedAt: Long?,
        manual: Long?, manualAt: Long?, changeSinceManual: Long,
    ): Long? = if (usesManual(kind, reportedBalance, reportedLimit, reportedAt, manual, manualAt)) manual!! + changeSinceManual
    else if (kind == AccountKind.CARD) reportedLimit else reportedBalance
}
