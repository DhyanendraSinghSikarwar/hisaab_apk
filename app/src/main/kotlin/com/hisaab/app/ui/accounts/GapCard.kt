package com.hisaab.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.Hx
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.insight.UntrackedGap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs

/** An open gap with the existing rows that may explain it. */
data class GapUi(val gap: UntrackedGap, val candidates: List<List<TransactionEntity>>)

/** The accounts that have an open gap, for the small dot in the Accounts list. */
@HiltViewModel
class GapDotsViewModel @Inject constructor(tracker: GapTracker) : ViewModel() {
    val accountIds = tracker.open.map { gaps -> gaps.map { it.accountId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}

/** The Add transaction route for [g], prefilled. */
fun gapRoute(g: UntrackedGap): String = "add?gap=${g.accountId}_${g.fromTime}_${g.toTime}_${g.deltaMinor}_${if (g.card) 1 else 0}"

/** Parses the argument of [gapRoute]; null for a plain Add. */
fun parseGap(arg: String?): UntrackedGap? {
    val p = arg?.takeIf { it.isNotBlank() }?.split('_') ?: return null
    if (p.size != 5) return null
    return runCatching { UntrackedGap(p[0].toLong(), p[1].toLong(), p[2].toLong(), p[3].toLong(), 0, 0, p[4] == "1") }.getOrNull()
}

@Composable
internal fun GapDot() {
    Spacer(Modifier.width(6.dp))
    Spacer(Modifier.size(8.dp).clip(CircleShape).background(Hx.warn))
}

private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun day(at: Long): String = Periods.localDate(at).format(dayMonth)

/** "₹250 · Ravi Kumar · 5:20 pm" for one row. */
internal fun rowLine(r: TransactionEntity): String =
    listOfNotNull(Money.format(r.amountMinor, showPaise = false), r.merchant?.takeIf { it.isNotBlank() }, Periods.time(r.timestamp)).joinToString(" · ")

/** What the gap says, e.g. "₹4,000 left between 9 Oct and 10 Oct". */
internal fun gapSummary(g: UntrackedGap): String {
    val amount = Money.format(abs(g.deltaMinor), showPaise = false)
    val from = day(g.fromTime)
    val to = day(g.toTime)
    return when {
        g.card && g.moneyLeft -> t("{amount} of limit used between {from} and {to}", "amount" to amount, "from" to from, "to" to to)
        g.card -> t("{amount} of limit freed between {from} and {to}", "amount" to amount, "from" to from, "to" to to)
        g.moneyLeft -> t("{amount} left between {from} and {to}", "amount" to amount, "from" to from, "to" to to)
        else -> t("{amount} came in between {from} and {to}", "amount" to amount, "from" to from, "to" to to)
    }
}

/** A compact amber card: money moved with no message. Offers rows that look like it, adding one by hand, or ignoring it. */
@Composable
fun GapCard(ui: GapUi, accountLabel: String, onAdd: () -> Unit, onIgnore: () -> Unit, onAssign: (List<Long>) -> Unit, modifier: Modifier = Modifier) {
    HCard(modifier, container = Hx.warn.copy(alpha = 0.12f), padding = 12.dp) {
        Text(t("Untracked change · {summary}", "summary" to gapSummary(ui.gap)), style = MaterialTheme.typography.titleSmall)
        ui.candidates.forEach { rows ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Looks like: {rows}", "rows" to rows.joinToString(" + ") { rowLine(it) }), style = MaterialTheme.typography.bodySmall, color = Hx.text2, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAssign(rows.map { it.id }) }) { Text(t("Assign to {account}", "account" to accountLabel)) }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd) { Text(t("Add transaction")) }
            OutlinedButton(onClick = onIgnore) { Text(t("Ignore")) }
        }
    }
}
