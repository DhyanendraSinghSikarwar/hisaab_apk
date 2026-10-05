package com.hisaab.app.ui.format

import com.hisaab.parser.model.Category
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * "Hide amounts": while on, every amount shows as ₹••••. It is Compose state, so flipping it redraws every
 * screen that shows money, without each screen knowing about it.
 */
object AmountPrivacy {
    var hidden by androidx.compose.runtime.mutableStateOf(false)
}

object Money {
    private val india = Locale.forLanguageTag("en-IN")
    private const val MASK = "••••"

    /** ₹1,23,456.50 (Indian digit grouping). Whole rupees drop the paise. */
    fun format(minor: Long, currency: String = "INR", showPaise: Boolean = minor % 100 != 0L): String {
        if (AmountPrivacy.hidden) return (if (currency == "INR") "₹" else "$currency ") + MASK
        val f = NumberFormat.getCurrencyInstance(india)
        runCatching { f.currency = Currency.getInstance(currency) }
        f.minimumFractionDigits = if (showPaise) 2 else 0
        f.maximumFractionDigits = if (showPaise) 2 else 0
        return f.format(minor / 100.0)
    }

    /** "12,500.50", "₹ 12500", "-300" typed by the user, in paise. Null when it is not an amount. */
    fun parseInput(text: String): Long? {
        val cleaned = text.replace("₹", "").replace(",", "").replace(" ", "").trim()
        if (cleaned.isEmpty() || !INPUT.matches(cleaned)) return null
        return cleaned.toBigDecimal().movePointRight(2).toLong()
    }

    private val INPUT = Regex("""-?\d+(?:\.\d{1,2})?""")

    /** ₹1.2L, ₹45K: for chart axes and tight cards. */
    fun compact(minor: Long): String {
        if (AmountPrivacy.hidden) return "₹$MASK"
        val rupees = minor / 100.0
        return when {
            rupees >= 1_00_00_000 -> "₹%.1fCr".format(rupees / 1_00_00_000)
            rupees >= 1_00_000 -> "₹%.1fL".format(rupees / 1_00_000)
            rupees >= 1_000 -> "₹%.1fK".format(rupees / 1_000)
            else -> "₹%.0f".format(rupees)
        }
    }
}

object Periods {
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun startOfDay(now: Long): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    fun startOfMonth(now: Long): Long = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone)).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun range(month: YearMonth): LongRange =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() until month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun offsetMillis(at: Long = System.currentTimeMillis()): Long = zone.rules.getOffset(Instant.ofEpochMilli(at)).totalSeconds * 1000L
    fun localDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    private val dayHeader = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
    private val time = DateTimeFormatter.ofPattern("h:mm a")
    private val monthName = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val monthShort = DateTimeFormatter.ofPattern("MMM")

    fun dayHeader(date: LocalDate): String = when (date) {
        LocalDate.now(zone) -> "Today"
        LocalDate.now(zone).minusDays(1) -> "Yesterday"
        else -> date.format(dayHeader)
    }
    fun dateTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateTime)
    fun time(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(time)
    fun month(m: YearMonth): String = m.format(monthName)
    fun monthShort(m: YearMonth): String = m.format(monthShort)
}

val Category.shortLabel: String get() = label
