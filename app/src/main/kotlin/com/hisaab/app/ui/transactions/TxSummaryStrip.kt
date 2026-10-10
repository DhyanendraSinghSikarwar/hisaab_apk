package com.hisaab.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.ui.theme.Hx

/**
 * The period's income, expenses and net as three equal columns split by hairlines, with a slim income-versus-expenses
 * bar beneath. Each pair is (label, formatted value); [netNegative] turns Net red.
 */
@Composable
internal fun TxSummaryStrip(
    income: Pair<String, String>, expenses: Pair<String, String>, net: Pair<String, String>,
    netNegative: Boolean, inMinor: Long, outMinor: Long, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            SummaryCell(income, Hx.pos, Modifier.weight(1f))
            SummaryDivider()
            SummaryCell(expenses, Hx.neg, Modifier.weight(1f))
            SummaryDivider()
            SummaryCell(net, if (netNegative) Hx.neg else Hx.pos, Modifier.weight(1f))
        }
        val total = inMinor + outMinor
        if (total > 0) {
            val share = (inMinor.toFloat() / total).coerceIn(0.02f, 0.98f)
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(CircleShape)) {
                Box(Modifier.weight(share).fillMaxHeight().background(Hx.pos))
                Box(Modifier.weight(1f - share).fillMaxHeight().background(Hx.neg))
            }
        }
    }
}

@Composable
private fun SummaryDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp).background(Hx.border))
}

@Composable
private fun SummaryCell(item: Pair<String, String>, color: Color, modifier: Modifier) {
    val (label, value) = item
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(label, fontSize = 11.sp, color = Hx.text2, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        }
        Text(
            value, fontSize = when { value.length >= 11 -> 13.sp; value.length >= 9 -> 15.sp; value.length >= 8 -> 16.sp; else -> 18.sp },
            fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
