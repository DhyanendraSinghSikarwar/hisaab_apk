package com.hisaab.app.ui.more

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hisaab.app.ui.format.AmountPrivacy
import com.hisaab.app.ui.theme.Hx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Share the estimate on screen (calculated or what-if) as an HD image or a PDF. When amounts are hidden, asks first,
 * because the file always carries the real figures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaxShareFlow(s: TaxState, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmed by remember { mutableStateOf(!AmountPrivacy.hidden) }
    var busy by remember { mutableStateOf<TaxShareFormat?>(null) }
    var failed by remember { mutableStateOf(false) }

    if (!confirmed) {
        AlertDialog(
            onDismissRequest = onDone,
            icon = { Icon(Icons.Outlined.Visibility, null) },
            title = { Text("Share with amounts?") },
            text = { Text("Amounts are hidden on screen. The shared estimate will show your real income, deductions and tax figures.") },
            confirmButton = { TextButton(onClick = { confirmed = true }) { Text("Share with amounts") } },
            dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )
        return
    }

    fun start(format: TaxShareFormat) {
        if (busy != null) return
        busy = format
        failed = false
        scope.launch {
            val file = runCatching { withContext(Dispatchers.Default) { TaxReport.export(context.applicationContext, s, format) } }.getOrNull()
            busy = null
            if (file == null) { failed = true; return@launch }
            if (shareFile(context, file, format, s)) onDone() else failed = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (busy == null) onDone() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Share estimate", style = MaterialTheme.typography.titleLarge)
            Text(
                "A clean summary of ${s.fyLabel}${if (s.whatIf.any) " with your what-if figures" else ""}: income, deductions, " +
                    "both regimes with the slab breakdown, and the recommended regime.",
                fontSize = 13.sp, color = Hx.text2,
            )
            Spacer(Modifier.size(2.dp))
            FormatOption(
                Icons.Outlined.Image, "Image (HD)", "PNG, 1440 px wide · best for WhatsApp and chats",
                busy == TaxShareFormat.IMAGE, enabled = busy == null,
            ) { start(TaxShareFormat.IMAGE) }
            FormatOption(
                Icons.Outlined.PictureAsPdf, "PDF", "A4 document with selectable text · for email or your CA",
                busy == TaxShareFormat.PDF, enabled = busy == null,
            ) { start(TaxShareFormat.PDF) }
            if (failed) Text("Couldn't create the file. Please try again.", fontSize = 13.sp, color = Hx.neg)
        }
    }
}

@Composable
private fun FormatOption(icon: ImageVector, title: String, sub: String, working: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Hx.surface2).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Hx.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Hx.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 12.sp, color = Hx.text2)
        }
        if (working) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

/** Hands [file] to Android's share sheet through the app's FileProvider, so WhatsApp, Gmail, Drive and others appear. */
private fun shareFile(context: Context, file: File, format: TaxShareFormat, s: TaxState): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = format.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Tax estimate · ${s.fyLong}")
        putExtra(Intent.EXTRA_TEXT, "Tax estimate · ${s.fyLong}, made with Hisaab")
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Share tax estimate").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}.isSuccess
