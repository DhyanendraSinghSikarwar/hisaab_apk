package com.hisaab.app.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hisaab.email.imap.MailServer

/** How to get an app password from one provider, in a few short steps, with the pages to open. */
private data class Guide(val steps: List<String>, val prerequisite: Pair<String, String>? = null)

private fun guideFor(server: MailServer?): Guide = when (server?.provider) {
    "Gmail" -> Guide(
        listOf(
            "Turn on 2-Step Verification for your Google account (one time).",
            "Open App passwords, type “Hisaab” as the name and tap Create.",
            "Copy the 16-letter password Google shows and paste it below.",
        ),
        "Turn on 2-Step Verification" to "https://myaccount.google.com/signinoptions/twosv",
    )
    "Outlook" -> Guide(
        listOf(
            "Open Advanced security options and turn on Two-step verification first. Until it is on, Microsoft hides App passwords.",
            "Back on the same page, scroll to App passwords and tap Create a new app password.",
            "Copy the password and paste it below. Work or school accounts: your admin may have turned app passwords off.",
        ),
        "Open Advanced security options" to "https://account.live.com/proofs/manage/additional",
    )
    "Yahoo Mail", "AOL Mail" -> Guide(
        listOf(
            "Open Account security and choose Generate app password.",
            "Type “Hisaab” as the app name and tap Generate.",
            "Copy the password and paste it below.",
        ),
    )
    "iCloud Mail" -> Guide(
        listOf(
            "Sign in to your Apple Account and open Sign-In and Security.",
            "Choose App-Specific Passwords, tap +, and name it “Hisaab”.",
            "Copy the password and paste it below.",
        ),
    )
    "Zoho Mail" -> Guide(
        listOf(
            "Open Security, then App passwords, and tap Generate new password.",
            "Name it “Hisaab” and copy the password Zoho shows.",
            "Paste it below.",
        ),
    )
    else -> Guide(
        listOf(
            "Open your email provider's security settings.",
            "Create an app password (sometimes called an app-specific password), or allow IMAP access.",
            "Paste the password below. If your provider has no app passwords, your normal password may work.",
        ),
    )
}

/**
 * A short, numbered guide to getting an app password, with buttons that open the right pages. Shown on the
 * sign-in step so nobody has to search for where the setting lives.
 */
@Composable
fun AppPasswordGuide(server: MailServer?, onOpen: (String) -> Unit) {
    val guide = guideFor(server)
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = c.secondaryContainer.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = c.onSecondaryContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get your app password", style = MaterialTheme.typography.titleSmall, color = c.onSecondaryContainer)
            }
            Text(
                "An app password is a separate password just for Hisaab. Your normal password stays private, and you can revoke this one any time.",
                style = MaterialTheme.typography.bodySmall, color = c.onSecondaryContainer,
            )
            guide.steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, color = c.onSecondaryContainer, modifier = Modifier.weight(1f))
                }
            }
            guide.prerequisite?.let { (label, url) ->
                TextButton(onClick = { onOpen(url) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                    Text("Step 1: $label")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                }
            }
            server?.appPasswordUrl?.let { url ->
                FilledTonalButton(onClick = { onOpen(url) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open ${server.provider} app passwords")
                }
                Text(url, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
    }
}
