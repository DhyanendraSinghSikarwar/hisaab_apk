package com.hisaab.app.ui.more

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.i18n.t
import com.hisaab.app.security.AppLockGate
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.settings.SettingsViewModel

private val BUDGET_LEVELS = listOf(80, 90, 95)

/** What DhanKosh tells you about, and when. */
@Composable
fun AlertsRoute(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleResumeEffect(Unit) { allowed = NotificationManagerCompat.from(context).areNotificationsEnabled(); onPauseOrDispose { } }

    MoreScaffold(t("Notifications & alerts"), onBack) { inner ->
        val app = s.app ?: return@MoreScaffold
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            if (!allowed) item("blocked") {
                HCard {
                    HRow(t("Notifications are off"), t("Android is blocking alerts from DhanKosh")) {
                        Button(onClick = {
                            AppLockGate.skipNextLock()
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }) { Text(t("Allow")) }
                    }
                }
            }
            item("tx") {
                HCard(title = t("Transactions")) {
                    SettingSwitch(t("Transaction alerts"), t("Each new spend, with quick category buttons"), app.transactionNotifications, vm::setTransactionNotifications)
                }
            }
            item("budget") {
                HCard(title = t("Budgets")) {
                    Text(t("Alert me when a budget reaches"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Segmented(
                        BUDGET_LEVELS.map { "$it%" }, BUDGET_LEVELS.indexOf(app.budgetAlertPercent),
                        onSelect = { vm.setBudgetAlertPercent(BUDGET_LEVELS[it]) },
                    )
                    HelpText(t("You are also told when a budget is used up."), Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
