package com.hisaab.app.ui.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.format.Periods
import com.hisaab.shared.db.TransactionSourceEntity

/**
 * The message behind a transaction, shown the way it arrived: an email with its sender, subject and body; an
 * SMS or notification as a bubble; a statement row as the line it was read from. Long bodies start folded.
 */
@Composable
fun MessageView(s: TransactionSourceEntity, modifier: Modifier = Modifier, collapsedLines: Int = 10, footer: (@Composable () -> Unit)? = null) {
    val body = remember(s.rawText) { s.rawText?.let(::tidy) }
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val c = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = c.surfaceContainerHigh) {
        Column(Modifier.padding(14.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(c.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    if (s.source == "EMAIL") {
                        Text(senderName(s.sender).take(1).uppercase(), color = c.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(iconFor(s.source), null, tint = c.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title(s), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(senderAddress(s.sender), Periods.dateTime(s.receivedAt)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (body != null) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(s.rawText.orEmpty())) }) {
                        Icon(Icons.Filled.ContentCopy, t("Copy text"), Modifier.size(18.dp), tint = c.onSurfaceVariant)
                    }
                }
            }
            if (s.source == "EMAIL") {
                s.subject?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.outlineVariant)
            } else {
                Spacer(Modifier.padding(top = 8.dp))
            }
            if (body == null) {
                Text(t("The original text wasn't kept for this one."), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            } else {
                val bubble = s.source != "EMAIL"
                val lines = body.count { it == '\n' } + 1
                val long = lines > collapsedLines || body.length > collapsedLines * 70
                SelectionContainer {
                    Text(
                        body,
                        style = if (s.source == "STATEMENT") MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded || !long) Int.MAX_VALUE else collapsedLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (bubble) Modifier.background(c.surfaceContainerLowest, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).padding(12.dp) else Modifier,
                    )
                }
                if (long) {
                    Text(
                        if (expanded) t("Show less") else if (s.source == "EMAIL") t("Show full email") else t("Show all"),
                        style = MaterialTheme.typography.labelLarge, color = c.primary,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }
            footer?.let { Spacer(Modifier.padding(top = 8.dp)); it() }
        }
    }
}


private fun iconFor(source: String): ImageVector = when (source) {
    "EMAIL" -> Icons.Filled.Email
    "CSV" -> Icons.Filled.UploadFile
    "APP" -> Icons.Filled.Notifications
    "SCREENSHOT" -> Icons.Filled.Image
    "MANUAL" -> Icons.Filled.Edit
    "STATEMENT" -> Icons.Filled.Description
    else -> Icons.Filled.Sms
}

private fun title(s: TransactionSourceEntity): String = when (s.source) {
    "SMS" -> t("SMS · {sender}", "sender" to s.sender)
    "EMAIL" -> senderName(s.sender)
    "APP" -> t("{sender} notification", "sender" to s.sender)
    "SCREENSHOT" -> t("From a screenshot")
    "MANUAL" -> t("Added by you")
    "CSV" -> t("Imported from CSV")
    "STATEMENT" -> t("Statement · {sender}", "sender" to senderName(s.sender))
    else -> "${s.source} · ${s.sender}"
}

/** "HDFC Bank <alerts@hdfcbank.net>" -> "HDFC Bank"; a bare address stays as it is. */
private fun senderName(sender: String): String =
    sender.substringBefore('<').trim().trim('"').ifEmpty { sender.substringAfter('<').substringBefore('>') }.ifEmpty { sender }

private fun senderAddress(sender: String): String? =
    sender.substringAfter('<', "").substringBefore('>').takeIf { it.isNotBlank() && it != senderName(sender) }

/** Readable text: no runs of blank lines, no trailing spaces, no invisible characters left over from HTML. */
private fun tidy(text: String): String = text
    .replace(' ', ' ').replace("​", "").replace("‌", "").replace("﻿", "")
    .lines().joinToString("\n") { it.trimEnd() }
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()
