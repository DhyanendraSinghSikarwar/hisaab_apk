package com.hisaab.app.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.security.AppLockGate
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.settings.TextSize
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.components.Kpi
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.settings.SettingsViewModel
import com.hisaab.app.ui.theme.Hx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Saves the in-app text size; the theme picks it up from AppSettings and redraws the whole app. */
@HiltViewModel
class TextSizeViewModel @Inject constructor(private val store: AppSettingsStore) : ViewModel() {
    fun set(size: TextSize) = viewModelScope.launch { store.setTextSize(size) }
}

/** App lock, hiding amounts, text size, and CSV backup. Nothing else. */
@Composable
fun SecurityRoute(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel(), textVm: TextSizeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    val canLock = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    MoreScaffold(t("Security & backup"), onBack, snackbar = snackbar) { inner ->
        val app = s.app ?: return@MoreScaffold
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            item("privacy") {
                HCard {
                    SettingSwitch(
                        t("App lock"), if (canLock) t("Fingerprint, face or screen lock") else t("Set a screen lock on this phone first"),
                        app.appLock, vm::setAppLock, enabled = canLock,
                    )
                    SettingSwitch(t("Hide amounts"), t("Mask balances and totals when the app opens"), app.hideAmounts, vm::setHideAmounts)
                }
            }
            item("text") { TextSizeCard(app.textSize, textVm::set) }
            item("backup") {
                HCard(title = t("Backup")) {
                    HRow(t("Export CSV"), t("Every transaction, to a file you choose")) {
                        OutlinedButton(onClick = { AppLockGate.skipNextLock(); exportLauncher.launch("hisaab-${LocalDate.now()}.csv") }) { Text(t("Export")) }
                    }
                    HRow(t("Import CSV"), t("Rows already present are skipped")) {
                        OutlinedButton(onClick = {
                            AppLockGate.skipNextLock()
                            importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                        }) { Text(t("Import")) }
                    }
                }
            }
            item("note") { PrivacyNote() }
        }
    }
}

/**
 * Text size: four sizes in a segmented control and a sample of Home underneath. The choice is saved at
 * once and the theme redraws the whole app with it, so the sample (and this screen) shows the real size.
 */
@Composable
private fun TextSizeCard(saved: TextSize, onPick: (TextSize) -> Unit) {
    // Follows the tap straight away, before the saved value comes back round from DataStore.
    var picked by remember(saved) { mutableStateOf(saved) }
    val sizes = TextSize.entries
    HCard(title = t("Text size")) {
        Segmented(
            sizes.map { if (it == TextSize.EXTRA_LARGE) t("XL") else t(it.label) },
            sizes.indexOf(picked),
            { i -> picked = sizes[i]; onPick(sizes[i]) },
        )
        HCard(Modifier.padding(top = 12.dp), container = Hx.surface2, padding = 14.dp) {
            Text(t("Preview"), fontSize = 11.sp, color = Hx.text2, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                t("{amount} spent this month", "amount" to "₹12,450"), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                t("{left} left of your {budget} budget", "left" to "₹3,120", "budget" to "₹15,570"), fontSize = 13.sp, color = Hx.text2,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Kpi(t("Income"), "₹85,000", Modifier.weight(1f), Hx.pos)
                Kpi(t("Spent"), "₹12,450", Modifier.weight(1f), Hx.neg)
                Kpi(t("Saved"), "₹72,550", Modifier.weight(1f))
            }
        }
        HelpText(
            t("{size} text. Applied on top of your phone's font size, up to a limit so screens still fit.", "size" to t(picked.label)),
            Modifier.padding(top = 10.dp),
        )
    }
}

/** "Your data never leaves this phone." with a lock. */
@Composable
internal fun PrivacyNote(modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Lock, null, tint = Hx.accent, modifier = Modifier.size(16.dp))
        Text(
            t("Your data never leaves this phone. No servers, no tracking."),
            fontSize = 12.sp, color = Hx.text2, modifier = Modifier.padding(start = 8.dp),
        )
    }
}
