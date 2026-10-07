package com.hisaab.app.ui.more

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.theme.Hx

/**
 * The one place to configure donations. Fill [UPI_ID] and/or drop a QR image at
 * `app/src/main/assets/support_qr.webp`; until one exists the Support button stays hidden.
 */
object SupportConfig {
    const val UPI_ID = ""
    const val PAYEE_NAME = "DhanKosh"
    const val QR_ASSET = "support_qr.webp"

    private fun assetExists(context: Context) = runCatching { context.assets.open(QR_ASSET).close() }.isSuccess

    /** True when there is something to show: a UPI id or a bundled QR image. */
    fun available(context: Context): Boolean = UPI_ID.isNotBlank() || assetExists(context)

    internal fun loadAsset(context: Context) =
        runCatching { context.assets.open(QR_ASSET).use { BitmapFactory.decodeStream(it) } }.getOrNull()

    /** `upi://pay` link; [amount] in rupees is added when positive. */
    fun upiUri(amount: Int? = null): String = buildString {
        append("upi://pay?pa=").append(Uri.encode(UPI_ID, "@."))
        append("&pn=").append(Uri.encode(PAYEE_NAME))
        append("&cu=INR")
        if (amount != null && amount > 0) append("&am=").append(amount)
    }
}

/** "Support DhanKosh": QR, UPI id and a one-tap UPI app launch. */
@Composable
fun SupportRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val asset = remember { SupportConfig.loadAsset(context) }
    var chip by rememberSaveable { mutableStateOf(-1) } // -1 none, 0..2 preset, 3 custom
    var custom by rememberSaveable { mutableStateOf("") }
    val presets = listOf(20, 50, 100)
    val amount: Int? = when (chip) { in 0..2 -> presets[chip]; 3 -> custom.toIntOrNull(); else -> null }
    val hasId = SupportConfig.UPI_ID.isNotBlank()

    MoreScaffold(t("Support DhanKosh"), onBack) { inner ->
        Column(
            Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Hx.accentSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Favorite, null, tint = Hx.accent, modifier = Modifier.size(26.dp))
                    }
                    Text(t("Support DhanKosh"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        t("DhanKosh is free, private and built by one developer. If it helps you, you can buy the developer a coffee."),
                        fontSize = 14.sp, color = Hx.text2, textAlign = TextAlign.Center,
                    )
                }
            }

            if (hasId) {
                HCard(title = t("Amount")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEachIndexed { i, v -> Pill("₹$v", on = chip == i) { chip = if (chip == i) -1 else i } }
                        Pill(t("Custom"), on = chip == 3) { chip = if (chip == 3) -1 else 3 }
                    }
                    if (chip == 3) {
                        OutlinedTextField(
                            custom, { custom = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth().padding(top = 10.dp),
                            label = { Text(t("Amount (₹)")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium,
                        )
                    }
                }
            }

            if (asset != null || hasId) {
                HCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp), contentAlignment = Alignment.Center) {
                            if (asset != null) {
                                Image(asset.asImageBitmap(), t("UPI QR code"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                            } else {
                                val qr = remember(amount) { runCatching { QrCode.encode(SupportConfig.upiUri(amount)) }.getOrNull() }
                                if (qr != null) QrCanvas(qr)
                            }
                        }
                        Text(t("Scan with any UPI app"), fontSize = 12.sp, color = Hx.text2)
                        if (hasId) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Hx.surface2).padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(t("UPI ID"), fontSize = 11.sp, color = Hx.text2)
                                    Text(SupportConfig.UPI_ID, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                }
                                androidx.compose.material3.TextButton(onClick = { copy(context, SupportConfig.UPI_ID) }) {
                                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(t("Copy"))
                                }
                            }
                        }
                    }
                }
            }

            if (hasId) {
                Button(
                    onClick = { pay(context, amount) }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(t("Pay with a UPI app"), style = MaterialTheme.typography.titleSmall) }
            }
            Text(
                t("Thank you. DhanKosh stays free either way."), fontSize = 12.sp, color = Hx.text2, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun QrCanvas(qr: QrCode) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val quiet = 2
        val cell = size.width / (qr.size + quiet * 2)
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (qr.dark(x, y)) drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("UPI ID", text))
    Toast.makeText(context, t("Copied"), Toast.LENGTH_SHORT).show()
}

private fun pay(context: Context, amount: Int?) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SupportConfig.upiUri(amount)))
    try {
        context.startActivity(Intent.createChooser(intent, t("Pay with")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, t("No UPI app found"), Toast.LENGTH_SHORT).show()
    }
}
