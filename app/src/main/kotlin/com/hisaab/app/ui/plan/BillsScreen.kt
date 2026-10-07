package com.hisaab.app.ui.plan

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.CategoryBadge
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.InfoButton
import com.hisaab.app.ui.format.Money
import com.hisaab.shared.insight.InsuranceKind
import com.hisaab.shared.insight.Policy
import com.hisaab.shared.insight.Recurring
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class BillsViewModel @Inject constructor(source: PlanSource) : ViewModel() {
    val plan = source.snapshot
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy")

val InsuranceKind.icon: ImageVector
    get() = when (this) {
        InsuranceKind.HEALTH -> Icons.Filled.HealthAndSafety
        InsuranceKind.LIFE -> Icons.Filled.Shield
        InsuranceKind.MOTOR -> Icons.Filled.DirectionsCar
        InsuranceKind.OTHER -> Icons.Filled.Umbrella
    }

val InsuranceKind.tint: Color
    get() = when (this) {
        InsuranceKind.HEALTH -> Color(0xFFE53935)
        InsuranceKind.LIFE -> Color(0xFF3949AB)
        InsuranceKind.MOTOR -> Color(0xFF00897B)
        InsuranceKind.OTHER -> Color(0xFF757575)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsRoute(onBack: () -> Unit, onOpenLoan: (String) -> Unit = {}, vm: BillsViewModel = hiltViewModel()) {
    val p by vm.plan.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var adding by remember { mutableStateOf(false) }
    if (adding || editing != null) {
        RecurringSheet(editing?.takeIf { it.manualId != null }, prefill = editing?.takeIf { it.manualId == null },
            onDismiss = { adding = false; editing = null })
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(),
            title = { Text(t("Bills & insurance")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back")) } },
        )
    }, floatingActionButton = {
        androidx.compose.material3.FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, t("Add a regular payment")) }
    }) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val soon = p.upcoming.filter { it.daysLeft in 0..30 }
            item { Summary(soon.sumOf { it.amountMinor }, soon.size, p.recurring.sumOf { it.amountMinor }) }
            item { BillsCalendar(p.recurring, p.policies, p.upcoming, onEdit = { r -> r.loanKey?.let(onOpenLoan) ?: run { editing = r } }) }
            if (p.loaded && p.recurring.isEmpty() && p.policies.isEmpty()) {
                item { EmptyState(Icons.Filled.EventRepeat, t("Nothing found yet"), t("Repeats appear after two months of payments.")) }
            }
        }
    }
}

@Composable
private fun Summary(dueSoon: Long, count: Int, monthly: Long) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t("Due in 30 days"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Money.format(dueSoon, showPaise = false), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if (count == 1) t("{n} payment", "n" to count) else t("{n} payments", "n" to count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(t("Every month"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Money.format(monthly, showPaise = false), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(t("in regular payments"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
}

@Composable
private fun DateBadge(date: LocalDate, tint: Color) {
    Box(Modifier.size(46.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = tint)
            Text(t(date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }), style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@Composable
fun UpcomingRow(u: Upcoming, modifier: Modifier = Modifier) {
    val warn = MaterialTheme.colorScheme.error
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DateBadge(u.due, if (u.short) warn else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(u.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                val whenText = when (u.daysLeft) { 0L -> t("Today"); 1L -> t("Tomorrow"); else -> t("In {n} days", "n" to u.daysLeft) }
                val from = u.account?.let { " · ${it.nickname ?: it.bankName} ••${it.last4}" }.orEmpty()
                Text(whenText + (when (u.kind) { Upcoming.Kind.INSURANCE -> " · " + t("renewal"); Upcoming.Kind.EMI -> " · " + t("EMI"); else -> "" }) + from,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (u.short) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = warn, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Low balance: {amount}", "amount" to (u.account?.currentBalanceMinor?.let { Money.format(it, showPaise = false) } ?: "")),
                            style = MaterialTheme.typography.labelMedium, color = warn)
                    }
                }
            }
            Text(Money.format(u.amountMinor, showPaise = false), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun RecurringRow(r: Recurring, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(r.category, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    (if (r.yearly) t("Every year, {date}", "date" to r.nextDue.format(DAY)) else t("Around the {day}", "day" to ordinal(r.dayOfMonth))) +
                        (r.lastPaid?.let { " · " + t("last paid {date}", "date" to it.format(DAY)) } ?: if (r.manualId != null) " · " + t("added by you") else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (r.income) "+" else "") + Money.format(r.amountMinor, showPaise = false), style = MaterialTheme.typography.titleMedium,
                    color = if (r.income) com.hisaab.app.ui.theme.MoneyColors.credit else MaterialTheme.colorScheme.onSurface)
                Text(if (r.yearly) t("a year") else t("a month"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PolicyRow(p: Policy) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(p.kind.tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(p.kind.icon, null, tint = p.kind.tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.insurer, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(t(p.kind.label) + " · " + (if (p.monthly) t("next premium {date}", "date" to p.nextDue.format(DAY)) else t("renews {date}", "date" to p.nextDue.format(DAY))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(p.premiumMinor, showPaise = false), style = MaterialTheme.typography.titleMedium)
                Text(if (p.monthly) t("a month") else t("a year"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"
    else -> "th"
}

/** What a calendar day carries: a payment going out, income coming in, an insurance premium, or a loan EMI. */
private enum class Mark { PAYMENT, INCOME, INSURANCE, EMI }

private data class DayEvent(val date: LocalDate, val name: String, val amountMinor: Long, val mark: Mark, val short: Boolean, val recurring: Recurring? = null)

private fun eventsIn(month: java.time.YearMonth, recurring: List<Recurring>, policies: List<com.hisaab.shared.insight.Policy>, upcoming: List<Upcoming>): List<DayEvent> {
    val shortOn = upcoming.filter { it.short }.map { it.name to it.due }.toSet()
    fun day(d: Int) = month.atDay(d.coerceIn(1, month.lengthOfMonth()))
    val out = ArrayList<DayEvent>()
    for (r in recurring) {
        val date = if (r.yearly) r.nextDue.takeIf { it.monthValue == month.monthValue }?.let { day(it.dayOfMonth) } else day(r.dayOfMonth)
        val mark = when {
            r.income -> Mark.INCOME
            r.loanKey != null || r.category == com.hisaab.parser.model.Category.EMI_LOAN -> Mark.EMI
            else -> Mark.PAYMENT
        }
        if (date != null) out += DayEvent(date, r.name, r.amountMinor, mark, (r.name to date) in shortOn, r)
    }
    for (pol in policies) {
        val date = if (pol.monthly) day(pol.nextDue.dayOfMonth) else pol.nextDue.takeIf { it.monthValue == month.monthValue }?.let { day(it.dayOfMonth) }
        if (date != null) out += DayEvent(date, pol.insurer, pol.premiumMinor, Mark.INSURANCE, false)
    }
    return out.sortedBy { it.date }
}

/** A month at a time: each day marked by what falls on it; tap a day for its list. */
@Composable
private fun BillsCalendar(recurring: List<Recurring>, policies: List<com.hisaab.shared.insight.Policy>, upcoming: List<Upcoming>, onEdit: (Recurring) -> Unit) {
    val today = LocalDate.now(com.hisaab.app.ui.format.Periods.zone)
    var month by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(java.time.YearMonth.from(today).toString()) }
    val ym = java.time.YearMonth.parse(month)
    var selected by remember(month) { mutableStateOf<LocalDate?>(null) }
    val events = remember(ym, recurring, policies, upcoming) { eventsIn(ym, recurring, policies, upcoming) }
    val byDay = events.groupBy { it.date }
    val c = MaterialTheme.colorScheme
    val colors = mapOf(
        Mark.PAYMENT to c.primary, Mark.INCOME to com.hisaab.app.ui.theme.MoneyColors.credit, Mark.INSURANCE to c.tertiary,
        Mark.EMI to com.hisaab.app.ui.theme.Hx.palette[4],
    )
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Previous month")) }
                Text(com.hisaab.app.ui.format.Periods.month(ym), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Next month")) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(t(it), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
            }
            val lead = ym.atDay(1).dayOfWeek.value - 1
            val cells = lead + ym.lengthOfMonth()
            for (week in 0 until (cells + 6) / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (dow in 0 until 7) {
                        val n = week * 7 + dow - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (n in 1..ym.lengthOfMonth()) {
                                val date = ym.atDay(n)
                                val marks = byDay[date].orEmpty()
                                val isSel = date == selected
                                Column(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                        .background(if (isSel) c.primaryContainer else Color.Transparent)
                                        .then(if (date == today) Modifier.border(1.5.dp, c.primary, RoundedCornerShape(12.dp)) else Modifier)
                                        .clickable(enabled = marks.isNotEmpty()) { selected = if (isSel) null else date },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                ) {
                                    Text("$n", style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (marks.isNotEmpty()) FontWeight.SemiBold else null,
                                        color = if (marks.any { it.short }) c.error else c.onSurface)
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp).height(6.dp)) {
                                        marks.map { it.mark }.distinct().forEach { m -> Box(Modifier.size(6.dp).background(colors.getValue(m), CircleShape)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(Mark.PAYMENT to t("Payment"), Mark.EMI to t("EMI"), Mark.INCOME to t("Income"), Mark.INSURANCE to t("Insurance")).forEach { (m, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(colors.getValue(m), CircleShape))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            val shown = selected?.let { byDay[it].orEmpty() } ?: events
            if (shown.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                shown.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = e.recurring != null) { e.recurring?.let(onEdit) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(colors.getValue(e.mark), CircleShape))
                        Text("${e.date.dayOfMonth}", Modifier.width(36.dp).padding(start = 10.dp), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                        Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text((if (e.mark == Mark.INCOME) "+" else "") + Money.format(e.amountMinor, showPaise = false),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            color = if (e.short) c.error else if (e.mark == Mark.INCOME) com.hisaab.app.ui.theme.MoneyColors.credit else c.onSurface)
                    }
                }
            }
        }
    }
}
