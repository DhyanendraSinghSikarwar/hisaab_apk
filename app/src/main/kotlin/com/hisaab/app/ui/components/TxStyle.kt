package com.hisaab.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.AccountKind
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.Channel
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.Subcategories
import java.time.format.DateTimeFormatter

/** How every transaction row reads: a clean merchant name, "time · Category › Sub · method", and a coloured amount. */
object TxStyle {
    /** Payment-gateway prefixes banks put before the merchant: "PAY*SWIGGY", "Raz*swiggy", "RZP*Zomato", "PYU*Uber". */
    private val GATEWAY = Regex("""^(?:pay|raz|rzp|pyu|payu|ccav(?:enue)?|bil|bd|pg|ipay|cf|cashfree|jp|easebuzz)\s*\*\s*""", RegexOption.IGNORE_CASE)
    private val DATE = DateTimeFormatter.ofPattern("d MMM")

    fun merchant(tx: TransactionEntity): String {
        val raw = tx.merchant?.replace(GATEWAY, "")?.trim()?.takeIf { it.isNotEmpty() } ?: return t(tx.category.label)
        // "swiggy grocery" → "Swiggy Grocery"; names already in mixed case are kept as written.
        return if (raw == raw.lowercase()) raw.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } else raw
    }

    /** "Card", "UPI", "Bank": how the money moved. */
    fun method(tx: TransactionEntity): String? = when {
        tx.accountKind == AccountKind.CARD -> t("Card")
        tx.channel == Channel.UPI -> t("UPI")
        tx.channel == Channel.ATM -> t("ATM")
        tx.channel == Channel.NEFT || tx.channel == Channel.IMPS || tx.channel == Channel.RTGS -> tx.channel.name
        tx.accountLast4 != null -> t("Bank")
        else -> null
    }

    /** "Food & Dining › Food delivery", or just the category when there is no telling sub-category. */
    fun category(tx: TransactionEntity): String {
        if (tx.type == TransactionType.TRANSFER || tx.category == Category.TRANSFER) return t("Transfer")
        val sub = Subcategories.of(tx.category, tx.subcategory, tx.merchant, tx.upiId)
        return if (sub == Subcategories.OTHER || sub.equals(tx.category.label, ignoreCase = true)) t(tx.category.label) else "${t(tx.category.label)} › ${t(sub)}"
    }

    fun subtitle(tx: TransactionEntity, showDate: Boolean = false): String {
        val time = Periods.time(tx.timestamp)
        val whenText = if (showDate) Periods.localDate(tx.timestamp).format(DATE) + ", " + time else time
        // Payment method and sources show on the transaction's own page, not in lists.
        return listOfNotNull(whenText, category(tx)).joinToString(" · ")
    }

    private fun isTransfer(tx: TransactionEntity) = tx.type == TransactionType.TRANSFER || tx.category == Category.TRANSFER

    /** Signed amount and its colour: income green, card spends amber, bank/UPI spends red, investments accent, transfers grey. */
    @Composable
    fun amount(tx: TransactionEntity): Pair<String, Color> {
        val m = Money.format(tx.amountMinor, tx.currency)
        return when {
            isTransfer(tx) -> m to Hx.transfer
            tx.type == TransactionType.CREDIT -> "+$m" to Hx.pos
            tx.type == TransactionType.INVESTMENT -> "−$m" to Hx.accent
            tx.accountKind == AccountKind.CARD -> "−$m" to Hx.warn
            else -> "−$m" to Hx.neg
        }
    }
}
