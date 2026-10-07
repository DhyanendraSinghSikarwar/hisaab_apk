package com.hisaab.app.ui.accounts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.AccountAvatar
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.MoneyColors
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.MonthTotal
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The range the account chart covers: the last six months, one calendar year, or everything. */
sealed interface AccountRange {
    data object SixMonths : AccountRange
    data class Year(val year: Int) : AccountRange
    data object AllTime : AccountRange
}

@HiltViewModel
class AccountDetailViewModel @Inject constructor(handle: SavedStateHandle, accounts: AccountDao, private val dao: TransactionDao) : ViewModel() {
    val id: Long = checkNotNull(handle.get<Long>("id"))
    val account = accounts.observeWithActivity(Periods.startOfMonth(System.currentTimeMillis())).map { all -> all.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val range = MutableStateFlow<AccountRange>(AccountRange.SixMonths)

    @OptIn(ExperimentalCoroutinesApi::class)
    val months = range.flatMapLatest { r ->
        val now = YearMonth.now(Periods.zone)
        val (first, last) = when (r) {
            AccountRange.SixMonths -> now.minusMonths(5) to now
            is AccountRange.Year -> YearMonth.of(r.year, 1) to minOf(YearMonth.of(r.year, 12), now)
            AccountRange.AllTime -> (dao.firstTimestampFor(id)?.let { YearMonth.from(Periods.localDate(it)) } ?: now) to now
        }
        val from = Periods.range(first).first
        dao.monthlyForAccount(id, from, Periods.range(last).last, Periods.offsetMillis(from)).map { rows ->
            // Every month in the range, zero where nothing moved, so the lines have no gaps.
            val byMonth = rows.associateBy { it.month }
            generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }
                .map { m -> byMonth[m.toString()] ?: MonthTotal(m.toString(), 0, 0) }.toList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** One account: its balance, and spending and income month by month as two lines. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailRoute(onBack: () -> Unit, onOpenTransactions: (Long) -> Unit, vm: AccountDetailViewModel = hiltViewModel()) {
    val a by vm.account.collectAsStateWithLifecycle()
    val months by vm.months.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val acc = a
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            colors = com.hisaab.app.ui.theme.clearTopBar(),
            title = { Text(acc?.let { (it.nickname ?: it.bankName) + " ••" + it.last4 } ?: t("Account")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back")) } },
        )
    }) { inner ->
        if (acc == null) return@Scaffold
        Column(
            Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(acc.bankName, acc.kind, acc.accountType, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t(acc.accountType?.label ?: if (acc.kind == com.hisaab.parser.model.AccountKind.CARD) "Card" else "Bank account"),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(acc.currentBalanceMinor?.let { Money.format(it) } ?: "—", style = MaterialTheme.typography.headlineMedium)
                }
            }

            val now = LocalDate.now(Periods.zone).year
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(t("6 months"), t("Year"), t("All time"))
                options.forEachIndexed { i, label ->
                    val selected = when (i) { 0 -> range is AccountRange.SixMonths; 1 -> range is AccountRange.Year; else -> range is AccountRange.AllTime }
                    SegmentedButton(selected, {
                        vm.range.value = when (i) { 0 -> AccountRange.SixMonths; 1 -> AccountRange.Year(now); else -> AccountRange.AllTime }
                    }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
                }
            }
            (range as? AccountRange.Year)?.let { y ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { vm.range.value = AccountRange.Year(y.year - 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Previous year")) }
                    Text("${y.year}", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { vm.range.value = AccountRange.Year(y.year + 1) }, enabled = y.year < now) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Next year"))
                    }
                }
            }

            val spent = months.sumOf { it.spent }
            val income = months.sumOf { it.income }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure(t("Spent"), spent, MoneyColors.debit, Modifier.weight(1f))
                Figure(t("Income"), income, MoneyColors.credit, Modifier.weight(1f))
            }
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(t("Month by month"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    if (months.all { it.spent == 0L && it.income == 0L }) {
                        Text(t("Nothing moved in this period."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TwoLineChart(months, MoneyColors.debit, MoneyColors.credit)
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Legend(t("Spent"), MoneyColors.debit); Legend(t("Income"), MoneyColors.credit)
                        }
                    }
                }
            }
            OutlinedButton(onClick = { onOpenTransactions(acc.id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null); Spacer(Modifier.width(8.dp)); Text(t("See transactions"))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Figure(label: String, minor: Long, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(minor, showPaise = false), style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text("  $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Spending and income as two smooth lines over the same months; tap to read a month. */
@Composable
private fun TwoLineChart(months: List<MonthTotal>, spentColor: Color, incomeColor: Color) {
    val max = (months.maxOfOrNull { maxOf(it.spent, it.income) } ?: 1L).coerceAtLeast(1L).toFloat()
    var picked by remember(months) { mutableStateOf<Int?>(null) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(200.dp).pointerInput(months) {
                detectTapGestures { o -> if (months.size > 1) picked = ((o.x / size.width) * (months.size - 1) + 0.5f).toInt().coerceIn(0, months.size - 1) }
            },
        ) {
            val n = months.size
            fun xAt(i: Int) = if (n <= 1) size.width / 2 else size.width * i / (n - 1)
            fun yAt(v: Long) = size.height - (v / max) * size.height * 0.92f
            for (k in 1..3) drawLine(grid, Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), strokeWidth = 1f)
            listOf(months.map { it.income } to incomeColor, months.map { it.spent } to spentColor).forEach { (values, color) ->
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = xAt(i); val y = yAt(v)
                    if (i == 0) path.moveTo(x, y) else {
                        val px = xAt(i - 1); val py = yAt(values[i - 1])
                        path.cubicTo((px + x) / 2, py, (px + x) / 2, y, x, y)
                    }
                }
                val fill = Path().apply { addPath(path); lineTo(xAt(n - 1), size.height); lineTo(xAt(0), size.height); close() }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent)))
                drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                values.forEachIndexed { i, v -> if (i == picked) drawCircle(color, 5.dp.toPx(), Offset(xAt(i), yAt(v))) }
            }
            picked?.let { i -> drawLine(grid, Offset(xAt(i), 0f), Offset(xAt(i), size.height), strokeWidth = 2f) }
        }
        Row(Modifier.fillMaxWidth()) {
            val step = ((months.size + 5) / 6).coerceAtLeast(1)
            months.forEachIndexed { i, m ->
                Text(if (i % step == 0) Periods.monthShort(YearMonth.parse(m.month)) else "", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        picked?.let { i ->
            val m = months[i]
            Text(
                t("{month}: spent {spent} · income {income}", "month" to Periods.month(YearMonth.parse(m.month)), "spent" to Money.format(m.spent, showPaise = false), "income" to Money.format(m.income, showPaise = false)),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
