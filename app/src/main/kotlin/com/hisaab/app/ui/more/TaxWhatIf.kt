package com.hisaab.app.ui.more

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
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.CardTitle
import com.hisaab.app.ui.components.Tag
import com.hisaab.app.ui.format.Money
import com.hisaab.app.ui.theme.Hx
import java.math.BigDecimal

// ---------------------------------------------------------------------------------------------
// What-if figures: amounts typed into the calculator to replace what DhanKosh worked out, held only in
// TaxViewModel for the current visit and never saved. All amounts are paise; null means "use the
// calculated value".
// ---------------------------------------------------------------------------------------------

data class TaxWhatIf(
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
}

// ---------------------------------------------------------------------------------------------
// The what-if calculator sheet.
// ---------------------------------------------------------------------------------------------

/** One editable figure: what DhanKosh calculated, and a note on how it is used. */
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
    onCalculate: (TaxWhatIf) -> Unit,
    onReset: () -> Unit,
) {
    val o = s.whatIf
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
    val draft = if (invalid) null else TaxWhatIf(
        salary = fields[0].value, otherIncome = fields[1].value, c80 = fields[2].value, d80 = fields[3].value,
        nps = fields[4].value, homeLoan = fields[5].value, hraOther = fields[6].value, employerNps = fields[7].value,
    )
    // Recompute both regimes live from what is typed so far.
    val preview = draft?.let { s.withWhatIf(it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t("What-if calculator"), style = MaterialTheme.typography.titleLarge)
            Text(
                t("Try your own figures for {fy}. Leave a field blank to use what DhanKosh calculated. Nothing here is saved: the calculated estimate comes back when you leave the Tax centre.", "fy" to s.fyLabel),
                fontSize = 13.sp, color = Hx.text2,
            )
            CardTitle(t("Income"), Modifier.padding(top = 6.dp))
            fields.take(2).forEach { FieldInput(it) }
            CardTitle(t("Deductions"), Modifier.padding(top = 6.dp))
            fields.drop(2).forEach { FieldInput(it) }

            Surface(shape = RoundedCornerShape(12.dp), color = Hx.surface2, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (preview == null) {
                        Text(t("Fix the highlighted values to see the estimate."), fontSize = 13.sp, color = Hx.neg)
                    } else {
                        PreviewFigure(t("New regime"), preview.newRegime.total, Modifier.weight(1f))
                        PreviewFigure(t("Old regime"), preview.oldRegime.total, Modifier.weight(1f))
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onReset(); onDismiss() },
                    enabled = o.any || fields.any { it.text.isNotBlank() },
                ) { Text(t("Back to calculated")) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(t("Cancel")) }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { draft?.let(onCalculate); onDismiss() }, enabled = draft != null) { Text(t("Calculate")) }
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
        label = { Text(t(f.label)) },
        prefix = { Text("₹") },
        placeholder = { Text(toInput(f.calculated), color = Hx.text2) },
        isError = f.invalid,
        trailingIcon = if (f.text.isNotEmpty()) ({ IconButton(onClick = { f.text = "" }) { Icon(Icons.Filled.Close, t("Use calculated")) } }) else null,
        supportingText = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (f.invalid) t("Enter an amount like 150000 or 1234.50") else t("Calculated: {amount}", "amount" to calc), modifier = Modifier.weight(1f, fill = false))
                    if (!f.invalid && f.value != null) { Spacer(Modifier.width(6.dp)); Tag(t("What-if"), Hx.accent) }
                }
                if (f.note != null && !f.invalid) Text(t(f.note), color = Hx.text2)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
