package com.hisaab.app.ui.more

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hisaab.app.i18n.t
import com.hisaab.app.log.ActivityEntry
import com.hisaab.app.log.ActivityLog
import com.hisaab.app.log.ActivitySource
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.Kpi
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.Hx
import com.hisaab.email.sync.GmailScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Totals over a period. */
data class ActivityTotals(val sms: Int = 0, val emails: Int = 0, val added: Int = 0, val statements: Int = 0)

data class ActivityState(
    val entries: List<ActivityEntry> = emptyList(),
    val today: ActivityTotals = ActivityTotals(),
    val week: ActivityTotals = ActivityTotals(),
    val lastSms: Long? = null,
    val lastEmail: Long? = null,
    /** The next nightly email or SMS sync WorkManager has scheduled, if any. */
    val nextSync: Long? = null,
)

@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val log: ActivityLog,
) : ViewModel() {
    private val work = WorkManager.getInstance(context)

    val state = combine(
        log.entries,
        work.getWorkInfosForUniqueWorkFlow(GmailScheduler.PERIODIC),
        work.getWorkInfosForUniqueWorkFlow(NIGHTLY_SMS),
    ) { entries, mail, sms ->
        val now = System.currentTimeMillis()
        val dayStart = Periods.startOfDay(now)
        val weekStart = Periods.localDate(now).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(Periods.zone).toInstant().toEpochMilli()
        val next = (mail + sms).filter { it.state == WorkInfo.State.ENQUEUED }.map { it.nextScheduleTimeMillis }
            .filter { it in (now + 1) until Long.MAX_VALUE }.minOrNull()
        ActivityState(
            entries = entries,
            today = totals(entries.filter { it.at >= dayStart }),
            week = totals(entries.filter { it.at >= weekStart }),
            lastSms = entries.firstOrNull { it.source == ActivitySource.SMS_SCAN && it.error == null }?.at,
            lastEmail = entries.firstOrNull { it.source.isEmail && it.error == null }?.at,
            nextSync = next,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityState())

    fun clear() = viewModelScope.launch { log.clear() }

    private fun totals(list: List<ActivityEntry>) = ActivityTotals(
        sms = list.filter { it.source.isSms }.sumOf { it.read },
        emails = list.sumOf { it.emails },
        added = list.sumOf { it.added },
        statements = list.sumOf { it.statements },
    )

    private companion object {
        /** The nightly SMS catch-up's unique work name (see NightlySync). */
        const val NIGHTLY_SMS = "nightly-sync"
    }
}

private val ActivitySource.isSms get() = this == ActivitySource.SMS_SCAN || this == ActivitySource.SMS_LIVE
private val ActivitySource.isEmail get() = this == ActivitySource.GMAIL || this == ActivitySource.IMAP

private val ActivitySource.icon: ImageVector
    get() = when (this) {
        ActivitySource.SMS_SCAN, ActivitySource.SMS_LIVE -> Icons.Filled.Sms
        ActivitySource.GMAIL, ActivitySource.IMAP -> Icons.Filled.Email
        ActivitySource.STATEMENTS -> Icons.Filled.Description
        ActivitySource.NOTIFICATIONS -> Icons.Filled.Notifications
    }

/** Matches the source filter; the Statements chip also shows email runs that read statements. */
private fun ActivityEntry.matches(filter: ActivitySource?) = when (filter) {
    null -> true
    ActivitySource.STATEMENTS -> source == ActivitySource.STATEMENTS || statements > 0
    else -> source == filter
}

/** Each sync run on this phone: when, from where, and how many messages and transactions. Counts only. */
@Composable
fun ActivityLogRoute(onBack: () -> Unit, vm: ActivityLogViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<ActivitySource?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    MoreScaffold(t("Activity log"), onBack, actions = {
        if (s.entries.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, t("Clear log")) }
    }) { inner ->
        val shown = s.entries.filter { it.matches(filter) }
        val byDay = shown.groupBy { Periods.localDate(it.at) }
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            item("today") { HCard(title = t("Today")) { Totals(s.today) } }
            item("week") { HCard(title = t("This week")) { Totals(s.week) } }
            item("sync") { SyncTimes(s) }
            item("filters") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(t("All"), on = filter == null) { filter = null }
                    ActivitySource.entries.forEach { src ->
                        Pill(t(src.label), on = filter == src, leading = src.icon) { filter = if (filter == src) null else src }
                    }
                }
            }
            if (shown.isEmpty()) {
                item("empty") { EmptyState(Icons.Filled.History, t("No activity yet"), t("Each sync is recorded here, on this phone only.")) }
            }
            byDay.forEach { (day, list) ->
                item("d:$day") {
                    HCard(title = t(Periods.dayHeader(day))) {
                        list.forEach { e -> EntryRow(e) }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("Clear log?")) },
            text = { Text(t("Removes every entry. Transactions are not affected.")) },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text(t("Clear"), color = Hx.neg) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t("Cancel")) } },
        )
    }
}

@Composable
private fun SyncTimes(s: ActivityState) {
    HCard(title = t("Sync")) {
        HRow(t("Last SMS scan"), s.lastSms?.let(Periods::dateTime) ?: t("Not yet"), leading = { Badge(Icons.Filled.Sms) })
        HRow(t("Last email sync"), s.lastEmail?.let(Periods::dateTime) ?: t("Not yet"), leading = { Badge(Icons.Filled.Email) })
        HRow(t("Next scheduled sync"), s.nextSync?.let(Periods::dateTime) ?: t("Not scheduled"), leading = { Badge(Icons.Filled.Sync) })
    }
}

@Composable
private fun Totals(x: ActivityTotals) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Kpi(t("SMS read"), "${x.sms}", Modifier.weight(1f))
        Kpi(t("Emails read"), "${x.emails}", Modifier.weight(1f))
        Kpi(t("Added"), "${x.added}", Modifier.weight(1f))
        Kpi(t("Statements"), "${x.statements}", Modifier.weight(1f))
    }
}

@Composable
private fun Badge(icon: ImageVector, error: Boolean = false) {
    Box(
        Modifier.size(36.dp).background(if (error) Hx.neg.copy(alpha = 0.12f) else Hx.accentSoft, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = if (error) Hx.neg else Hx.accent) }
}

@Composable
private fun EntryRow(e: ActivityEntry) {
    val parts = buildList {
        if (e.read > 0) add(
            if (e.source.isSms && e.relevant != e.read) t("{n} SMS, {b} from banks", "n" to e.read, "b" to e.relevant)
            else if (e.source == ActivitySource.NOTIFICATIONS) t("{n} notifications", "n" to e.read)
            else t("{n} SMS", "n" to e.read),
        )
        if (e.emails > 0) add(t("{n} emails", "n" to e.emails))
        add(t("{n} added", "n" to e.added))
        if (e.merged > 0) add(t("{n} merged", "n" to e.merged))
        if (e.review > 0) add(t("{n} to review", "n" to e.review))
        if (e.skipped > 0) add(t("{n} duplicates", "n" to e.skipped))
        if (e.statements > 0) add(t("{n} statements", "n" to e.statements))
        if (e.locked > 0) add(t("{n} locked", "n" to e.locked))
    }
    val title = t(e.source.label) + if (e.runs > 1) " · ×${e.runs}" else ""
    HRow(
        title, if (e.error != null) t("Failed: {error}", "error" to e.error) else parts.joinToString(" · "),
        leading = { Badge(e.source.icon, error = e.error != null) },
    ) {
        Text(
            Periods.time(e.at) + if (e.millis >= 1000) "\n" + "%.1fs".format(e.millis / 1000.0) else "",
            fontSize = 12.sp, color = Hx.text2, textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
