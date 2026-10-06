package com.hisaab.app.ui.more

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisaab.app.ui.components.CardTitle
import com.hisaab.app.ui.components.Tag
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.theme.Hx
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------------------------
// Values the user typed in to replace what Hisaab worked out. All amounts are paise; null means
// "use the calculated value".
// ---------------------------------------------------------------------------------------------

data class TaxOverrides(
    /** Annual gross salary. */
    val salary: Long? = null,
    val otherIncome: Long? = null,
    val c80: Long? = null,
    val d80: Long? = null,
    /** 80CCD(1B), your own NPS contribution beyond 80C. Old regime only. */
    val nps: Long? = null,
    /** 24(b), interest on a home loan for a self-occupied house. Old regime only. */
    val homeLoan: Long? = null,
    /** HRA exemption and any other deductions. Old regime only. */
    val hraOther: Long? = null,
    /** 80CCD(2), the employer's NPS contribution. Both regimes. */
    val employerNps: Long? = null,
) {
    val any: Boolean get() = listOf(salary, otherIncome, c80, d80, nps, homeLoan, hraOther, employerNps).any { it != null }

    fun toJson(): String = JSONObject().apply {
        FIELDS.forEach { (k, get) -> get(this@TaxOverrides)?.let { put(k, it) } }
    }.toString()

    companion object {
        private val FIELDS: List<Pair<String, (TaxOverrides) -> Long?>> = listOf(
            "salary" to { it.salary }, "other" to { it.otherIncome }, "c80" to { it.c80 }, "d80" to { it.d80 },
            "nps" to { it.nps }, "homeLoan" to { it.homeLoan }, "hra" to { it.hraOther }, "employerNps" to { it.employerNps },
        )

        fun fromJson(json: String?): TaxOverrides {
            val o = runCatching { JSONObject(json ?: return TaxOverrides()) }.getOrNull() ?: return TaxOverrides()
            fun l(k: String): Long? = if (o.has(k)) runCatching { o.getLong(k) }.getOrNull() else null
            return TaxOverrides(l("salary"), l("other"), l("c80"), l("d80"), l("nps"), l("homeLoan"), l("hra"), l("employerNps"))
        }
    }
}

private val Context.taxOverridesStore: DataStore<Preferences> by preferencesDataStore(name = "tax_overrides")

/** The tax centre's overrides, on the phone only, one JSON entry per financial year ("2026-27"). */
@Singleton
class TaxOverridesStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.taxOverridesStore

    fun observe(fy: String): Flow<TaxOverrides> =
        store.data.map { TaxOverrides.fromJson(it[key(fy)]) }.distinctUntilChanged()

    suspend fun save(fy: String, overrides: TaxOverrides) {
        store.edit { p -> if (overrides.any) p[key(fy)] = overrides.toJson() else p.remove(key(fy)) }
    }

    suspend fun reset(fy: String) {
        store.edit { it.remove(key(fy)) }
    }

    private fun key(fy: String) = stringPreferencesKey("fy_$fy")
}

// ---------------------------------------------------------------------------------------------
// The edit sheet.
// ---------------------------------------------------------------------------------------------

/** One editable figure: what Hisaab calculated, and a note on how it is used. */
private class Field(val label: String, val calculated: Long, val note: String?, initial: Long?) {
    var text by mutableStateOf(initial?.let(::toInput) ?: "")
    /** Blank means "use the calculated value". */
    val value: Long? get() = if (text.isBlank()) null else Money.parseInput(text)
    val invalid: Boolean get() = text.isNotBlank() && (value ?: -1L) < 0
}

private fun toInput(minor: Long): String = BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaxEditSheet(
    s: TaxState,
    onDismiss: () -> Unit,
    onSave: (TaxOverrides) -> Unit,
    onReset: () -> Unit,
) {
    val o = s.overrides
    val fields = remember {
        listOf(
            Field("Annual gross salary", s.projectedSalary, "Before TDS and PF, for the whole year", o.salary),
            Field("Other income", s.otherIncome, "Interest, rent, freelance and the like", o.otherIncome),
            Field("80C investments", s.c80Calc, "PPF, ELSS, EPF, life cover… capped at ₹1,50,000 · old regime", o.c80),
            Field("80D health insurance", s.d80Calc, "Capped at ₹25,000 · old regime", o.d80),
            Field("NPS 80CCD(1B)", 0, "Your own NPS, capped at ₹50,000 · old regime only", o.nps),
            Field("Home-loan interest 24(b)", 0, "Self-occupied house, capped at ₹2,00,000 · old regime only", o.homeLoan),
            Field("HRA exemption / other deductions", 0, "Old regime only", o.hraOther),
            Field("Employer NPS 80CCD(2)", 0, "Your employer's NPS share · both regimes", o.employerNps),
        )
    }
    val invalid = fields.any { it.invalid }
    val draft = if (invalid) null else TaxOverrides(
        salary = fields[0].value, otherIncome = fields[1].value, c80 = fields[2].value, d80 = fields[3].value,
        nps = fields[4].value, homeLoan = fields[5].value, hraOther = fields[6].value, employerNps = fields[7].value,
    )
    // Recompute both regimes live from what is typed so far.
    val preview = draft?.let { s.withOverrides(it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Edit tax values", style = MaterialTheme.typography.titleLarge)
            Text(
                "Leave a field blank to use what Hisaab calculated. Your values stay on this phone for ${s.fyLabel} until you reset them.",
                fontSize = 13.sp, color = Hx.text2,
            )
            CardTitle("Income", Modifier.padding(top = 6.dp))
            fields.take(2).forEach { FieldInput(it) }
            CardTitle("Deductions", Modifier.padding(top = 6.dp))
            fields.drop(2).forEach { FieldInput(it) }

            Surface(shape = RoundedCornerShape(12.dp), color = Hx.surface2, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (preview == null) {
                        Text("Fix the highlighted values to see the estimate.", fontSize = 13.sp, color = Hx.neg)
                    } else {
                        PreviewFigure("New regime", preview.newRegime.total, Modifier.weight(1f))
                        PreviewFigure("Old regime", preview.oldRegime.total, Modifier.weight(1f))
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onReset(); onDismiss() },
                    enabled = o.any || fields.any { it.text.isNotBlank() },
                ) { Text("Reset to calculated") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { draft?.let(onSave); onDismiss() }, enabled = draft != null) { Text("Save") }
            }
        }
    }
}

@Composable
private fun PreviewFigure(label: String, tax: Long, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Hx.text2)
        Text(Money.format(tax, showPaise = false), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FieldInput(f: Field) {
    val calc = Money.format(f.calculated, showPaise = false)
    OutlinedTextField(
        f.text, { f.text = it }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(f.label) },
        prefix = { Text("₹") },
        placeholder = { Text(toInput(f.calculated), color = Hx.text2) },
        isError = f.invalid,
        trailingIcon = if (f.text.isNotEmpty()) ({ IconButton(onClick = { f.text = "" }) { Icon(Icons.Filled.Close, "Use calculated") } }) else null,
        supportingText = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (f.invalid) "Enter an amount like 150000 or 1234.50" else "Calculated: $calc", modifier = Modifier.weight(1f, fill = false))
                    if (!f.invalid && f.value != null) { Spacer(Modifier.width(6.dp)); Tag("Edited", Hx.accent) }
                }
                if (f.note != null && !f.invalid) Text(f.note, color = Hx.text2)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
