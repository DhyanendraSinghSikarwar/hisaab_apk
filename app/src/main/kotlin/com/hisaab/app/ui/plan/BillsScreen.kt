package com.hisaab.app.ui.plan

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun BillsRoute(onBack: () -> Unit, vm: BillsViewModel = hiltViewModel()) {
    val p by vm.plan.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var adding by remember { mutableStateOf(false) }
    if (adding || editing != null) {
        RecurringSheet(editing?.takeIf { it.manualId != null }, prefill = editing?.takeIf { it.manualId == null },
            onDismiss = { adding = false; editing = null })
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(colors = com.hisaab.app.ui.theme.clearTopBar(), 
            title = { Text("Bills & insurance") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                InfoButton(
                    "Bills & insurance",
                    "Hisaab finds payments that repeat every month (rent, EMIs, SIPs, subscriptions, premiums) from your transactions, and the insurance premiums you pay.",
                    "Three days before a monthly payment, if the account it's paid from doesn't have enough, you get an alert. Insurance renewals are reminded a week ahead.",
                    "It needs two or more months of history to spot a repeat.",
                )
            },
        )
    }, floatingActionButton = {
        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Recurring") },
        )
    }) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val soon = p.upcoming.filter { it.daysLeft in 0..30 }
            item { Summary(soon.sumOf { it.amountMinor }, soon.size, p.recurring.sumOf { it.amountMinor }) }
            if (p.loaded && p.recurring.isEmpty() && p.policies.isEmpty()) {
                item { EmptyState(Icons.Filled.EventRepeat, "Nothing found yet", "Regular payments and insurance show up once they've been paid in two or more months.") }
            }
            if (soon.isNotEmpty()) {
                item { Header("Coming up") }
                items(soon, key = { "u-${it.name}-${it.due}" }) { u -> UpcomingRow(u) }
            }
            val outgoing = p.recurring.filter { !it.income }
            val incoming = p.recurring.filter { it.income }
            if (outgoing.isNotEmpty()) {
                item { Header("Regular payments") }
                items(outgoing, key = { "r-${it.manualId ?: it.name}" }) { r -> RecurringRow(r, onEdit = { editing = r }) }
            }
            if (incoming.isNotEmpty()) {
                item { Header("Expected income") }
                items(incoming, key = { "i-${it.manualId ?: it.name}" }) { r -> RecurringRow(r, onEdit = { editing = r }) }
            }
            if (p.policies.isNotEmpty()) {
                item { Header("Insurance") }
                items(p.policies, key = { "p-${it.insurer}" }) { pol -> PolicyRow(pol) }
            }
        }
    }
}

@Composable
private fun Summary(dueSoon: Long, count: Int, monthly: Long) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Due in 30 days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Money.format(dueSoon, showPaise = false), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("$count payment${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text("Every month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Money.format(monthly, showPaise = false), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("in regular payments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
            Text(date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = tint)
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
                val whenText = when (u.daysLeft) { 0L -> "Today"; 1L -> "Tomorrow"; else -> "In ${u.daysLeft} days" }
                val from = u.account?.let { " · ${it.nickname ?: it.bankName} ••${it.last4}" }.orEmpty()
                Text(whenText + (if (u.kind == Upcoming.Kind.INSURANCE) " · renewal" else "") + from,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (u.short) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = warn, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Low balance: ${u.account?.currentBalanceMinor?.let { Money.format(it, showPaise = false) } ?: ""}",
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
                    (if (r.yearly) "Every year, ${r.nextDue.format(DAY)}" else "Around the ${ordinal(r.dayOfMonth)}") +
                        (r.lastPaid?.let { " · last paid ${it.format(DAY)}" } ?: if (r.manualId != null) " · added by you" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (r.income) "+" else "") + Money.format(r.amountMinor, showPaise = false), style = MaterialTheme.typography.titleMedium,
                    color = if (r.income) com.hisaab.app.ui.theme.MoneyColors.credit else MaterialTheme.colorScheme.onSurface)
                Text(if (r.yearly) "a year" else "a month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("${p.kind.label} · ${if (p.monthly) "next premium" else "renews"} ${p.nextDue.format(DAY)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(p.premiumMinor, showPaise = false), style = MaterialTheme.typography.titleMedium)
                Text(if (p.monthly) "a month" else "a year", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"
    else -> "th"
}
