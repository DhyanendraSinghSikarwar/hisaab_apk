package com.hisaab.app.ui.bench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.format.Periods
import com.hisaab.parser.model.ParsedTransaction
import com.hisaab.parser.model.Source
import com.hisaab.parser.registry.ParserRegistry
import com.hisaab.parser.rules.RejectionRules
import com.hisaab.parser.text.TextNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed interface BenchResult {
    data class Parsed(val tx: ParsedTransaction, val micros: Long) : BenchResult
    data class Rejected(val why: String) : BenchResult
}

@HiltViewModel
class ParserBenchViewModel @Inject constructor(private val registry: ParserRegistry) : ViewModel() {
    private val _result = MutableStateFlow<BenchResult?>(null)
    val result = _result.asStateFlow()

    fun run(sender: String, body: String, source: Source) {
        val start = System.nanoTime()
        val tx = registry.parse(body, sender, System.currentTimeMillis(), source)
        val micros = (System.nanoTime() - start) / 1000
        _result.value = if (tx != null) BenchResult.Parsed(tx, micros) else {
            val reason = RejectionRules.reasonFor(TextNormalizer.normalize(body))
            BenchResult.Rejected(
                when {
                    registry.resolve(sender) == null -> "Sender \"$sender\" is not a known bank. Try an SMS header like VM-HDFCBK or an email like alerts@hdfcbank.net."
                    reason != null -> "Rejected as $reason: not a completed transaction."
                    else -> "No transaction found: needs an amount, debit or credit wording, and an account number or reference."
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserBenchRoute(onBack: () -> Unit, vm: ParserBenchViewModel = hiltViewModel()) {
    val result by vm.result.collectAsStateWithLifecycle()
    var sender by remember { mutableStateOf("VM-HDFCBK") }
    var body by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(Source.SMS) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Test the parser") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Source.SMS, Source.EMAIL).forEach { s -> FilterChip(selected = source == s, onClick = { source = s }, label = { Text(s.name) }) }
            }
            OutlinedTextField(sender, { sender = it }, Modifier.fillMaxWidth(), label = { Text("Sender") }, singleLine = true)
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("bench-body"), label = { Text("Message text") })
            Button(onClick = { vm.run(sender, body, source) }, enabled = body.isNotBlank(), modifier = Modifier.testTag("bench-parse")) { Text("Parse") }
            when (val r = result) {
                is BenchResult.Parsed -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val t = r.tx
                        Text("${t.type} ${Money.format(t.amountMinor, t.currency)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("bench-result"))
                        Line("Bank", t.bankName); Line("Account", "${t.accountLast4 ?: "—"} (${t.accountKind})")
                        Line("Merchant", t.merchant ?: "—"); Line("Category", t.category.label); Line("Channel", t.channel.name)
                        Line("Reference", t.referenceNumber ?: "—"); Line("UPI id", t.upiId ?: "—")
                        Line("Balance", t.balanceMinor?.let { Money.format(it) } ?: "—"); Line("Limit", t.availableLimitMinor?.let { Money.format(it) } ?: "—")
                        Line("When", Periods.dateTime(t.transactionTime) + if (t.hasExplicitTime) "" else " (message time)")
                        Line("Confidence", "${(t.confidence * 100).toInt()}%"); Line("Hash", t.transactionHash.take(16) + "…")
                        Line("Parse time", "${r.micros} µs")
                    }
                }
                is BenchResult.Rejected -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(r.why, Modifier.padding(16.dp).testTag("bench-result"), color = MaterialTheme.colorScheme.onErrorContainer)
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
}
