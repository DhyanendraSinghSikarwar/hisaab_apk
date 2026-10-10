package com.hisaab.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.notify.CardDueReminderChecker
import com.hisaab.app.notify.CardDueReminderStore
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.theme.Hx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Which card bills the user marked paid. Marking records nothing in the ledger: it only silences reminders. */
@HiltViewModel
class CardDueViewModel @Inject constructor(
    private val store: CardDueReminderStore,
    private val accounts: com.hisaab.shared.db.AccountDao,
    private val overrides: com.hisaab.shared.repo.CardOverrideStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    val paid = store.paidKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun markPaid(cardId: Long, due: LocalDate) = viewModelScope.launch {
        store.markPaid(cardId, due)
        // Paid: any manual billed/unbilled figures are done with.
        accounts.getById(cardId)?.let { a -> overrides.clear(a.bankName, a.last4) }
        androidx.core.app.NotificationManagerCompat.from(context).cancel(CardDueReminderChecker.notificationId(cardId))
    }

    fun undo(cardId: Long, due: LocalDate) = viewModelScope.launch { store.undoPaid(cardId, due) }
}

/** True once the bill of [cardId] due on [due] is marked paid. */
@Composable
internal fun cardDuePaid(cardId: Long, due: LocalDate?, vm: CardDueViewModel = hiltViewModel()): Boolean {
    val paid by vm.paid.collectAsStateWithLifecycle()
    return due != null && CardDueReminderStore.paidKey(cardId, due) in paid
}

/** A compact check button that marks the bill of [cardId] due on [due] as paid. */
@Composable
internal fun MarkPaidButton(cardId: Long, due: LocalDate, vm: CardDueViewModel = hiltViewModel()) {
    androidx.compose.material3.IconButton(onClick = { vm.markPaid(cardId, due) }, modifier = Modifier.size(32.dp)) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Check, t("Mark paid"), tint = Hx.accent, modifier = Modifier.size(20.dp))
    }
}

/**
 * "Due in 3 days · Mark paid", or "Marked paid · Undo" once marked. Shows nothing without a billed amount and a due date.
 * Marking paid only silences reminders; the payment itself still arrives from SMS or email.
 */
@Composable
internal fun CardDueActions(cardId: Long, due: LocalDate?, billedMinor: Long?, modifier: Modifier = Modifier, vm: CardDueViewModel = hiltViewModel()) {
    if (due == null || billedMinor == null || billedMinor <= 0) return
    val paid by vm.paid.collectAsStateWithLifecycle()
    val done = CardDueReminderStore.paidKey(cardId, due) in paid
    val days = ChronoUnit.DAYS.between(LocalDate.now(Periods.zone), due)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (done) {
            Text(t("Marked paid"), fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(end = 4.dp))
            TextButton(onClick = { vm.undo(cardId, due) }, modifier = Modifier.defaultMinSize(minHeight = 1.dp, minWidth = 1.dp)) {
                Text(t("Undo"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Hx.accent)
            }
        } else {
            val label = when {
                days < 0 -> t("Overdue")
                days == 0L -> t("Due today")
                days == 1L -> t("Due tomorrow")
                else -> t("Due in {days} days", "days" to days)
            }
            Text(label, fontSize = 12.sp, color = if (days < 0) Hx.neg else if (days <= 5) Hx.warn else Hx.text2, modifier = Modifier.padding(end = 4.dp))
            Text("·", fontSize = 12.sp, color = Hx.text2)
            TextButton(onClick = { vm.markPaid(cardId, due) }, modifier = Modifier.defaultMinSize(minHeight = 1.dp, minWidth = 1.dp)) {
                Text(t("Mark paid"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Hx.accent)
            }
        }
    }
}
