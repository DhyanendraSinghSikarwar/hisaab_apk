package com.hisaab.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.theme.Hx
import com.hisaab.app.ui.theme.color
import com.hisaab.app.ui.theme.icon
import com.hisaab.shared.db.MerchantRuleDao
import com.hisaab.shared.db.MerchantRuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(private val rules: MerchantRuleDao) : ViewModel() {
    /** Null until the first read, so the empty state does not flash. */
    val list: StateFlow<List<MerchantRuleEntity>?> = rules.observeAll()
        .map { l -> l.sortedBy { it.merchantKey } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(r: MerchantRuleEntity) = viewModelScope.launch { rules.delete(r.merchantKey) }
    fun restore(r: MerchantRuleEntity) = viewModelScope.launch { rules.upsert(r) }
}

/** "swiggy instamart" → "Swiggy Instamart"; UPI ids stay as they are. */
internal fun ruleTitle(key: String): String =
    if ('@' in key) key else key.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

@Composable
fun RulesRoute(onBack: () -> Unit, vm: RulesViewModel = hiltViewModel()) {
    val rules by vm.list.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    MoreScaffold("Rules", onBack, snackbar = snackbar) { inner ->
        val list = rules ?: return@MoreScaffold
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            item("intro") {
                Text(
                    "Hisaab files these automatically. When you change a transaction's category, the merchant is remembered " +
                        "and its future payments go to the same category.",
                    style = MaterialTheme.typography.bodyMedium, color = Hx.text2, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (list.isEmpty()) {
                item("empty") {
                    HCard {
                        Text("No rules yet", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Open a transaction and pick a category: Hisaab will remember it for that merchant.",
                            fontSize = 13.sp, color = Hx.text2,
                        )
                    }
                }
            } else {
                item("list") {
                    HCard(title = "${list.size} rule${if (list.size == 1) "" else "s"}", padding = 12.dp) {
                        Column {
                            list.forEachIndexed { i, r ->
                                if (i > 0) HorizontalDivider(color = Hx.border)
                                RuleRow(r) {
                                    vm.delete(r)
                                    scope.launch {
                                        val res = snackbar.showSnackbar(
                                            "Rule for ${ruleTitle(r.merchantKey)} removed", actionLabel = "Undo", duration = SnackbarDuration.Short,
                                        )
                                        if (res == SnackbarResult.ActionPerformed) vm.restore(r)
                                    }
                                }
                            }
                        }
                    }
                }
                item("foot") {
                    Text(
                        "Removing a rule leaves past transactions as they are.",
                        fontSize = 12.sp, color = Hx.text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleRow(r: MerchantRuleEntity, onDelete: () -> Unit) {
    HRow(
        title = ruleTitle(r.merchantKey),
        subtitle = null,
        leading = {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(r.category.color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(r.category.icon, null, tint = r.category.color, modifier = Modifier.size(18.dp)) }
        },
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Hx.text2, modifier = Modifier.size(14.dp))
        Text(
            r.category.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = r.category.color,
            modifier = Modifier.padding(start = 6.dp), maxLines = 1,
        )
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Remove rule", tint = Hx.text2, modifier = Modifier.size(18.dp)) }
    }
}
