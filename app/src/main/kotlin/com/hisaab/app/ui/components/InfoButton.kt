package com.hisaab.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A small ⓘ that explains a setting in plain words: what it does, what it reads, and what stays on the phone. */
@Composable
fun InfoButton(title: String, vararg paragraphs: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(36.dp)) {
        Icon(Icons.Outlined.Info, "About $title", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            icon = { Icon(Icons.Outlined.Info, null) },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

/** The explanations, in one place so the same question gets the same answer everywhere. */
object Info {
    val SMS = arrayOf(
        "Hisaab reads SMS only from banks, card issuers, EPFO and NPS. Messages from people and other apps are skipped without being read.",
        "Bank SMS become transactions. OTPs, offers, reminders and failed payments are ignored.",
        "Everything stays on this phone.",
    )
    val EMAIL = arrayOf(
        "Connecting your email lets Hisaab read bank alert emails and the statement PDFs attached to them (card, bank, CAS, INDmoney and broker statements).",
        "It reads only mail from the bank and investment senders in the filter, and only to your phone. Nothing is uploaded.",
        "To prove the address is yours, Hisaab mails you a 6-digit code from your own account.",
    )
    val APP_PASSWORD = arrayOf(
        "An app password is a separate 16-character password your email provider makes for one app. Your normal password won't work, and Hisaab never sees it.",
        "Gmail: turn on 2-Step Verification, then create one at myaccount.google.com/apppasswords. You can delete it there at any time.",
        "It's stored encrypted with a key that never leaves this phone.",
    )
    val FETCH_HISTORY = arrayOf(
        "How far back Hisaab reads your SMS inbox and your email. A longer period finds more history but takes longer the first time.",
        "Changing it rescans for the new period. Transactions already found stay.",
    )
    val PAYMENT_APPS = arrayOf(
        "Many banks don't send an SMS for small UPI payments, but GPay, PhonePe, Paytm and others always show a notification.",
        "With this on, Hisaab reads notifications from known payment and bank apps only, and keeps only completed payments. Every other app's notifications are ignored.",
        "If the bank's SMS or email for the same payment comes later, the two are merged, not counted twice.",
    )
    val STATEMENT_PASSWORDS = arrayOf(
        "Most statements are password protected. Save a password once and Hisaab tries it on every new statement.",
        "Card statements often use the first 4 letters of your name + DDMM of birth (e.g. RAHU0105). CAS uses your PAN in capitals. Bank statements often use your customer ID or date of birth.",
        "Passwords are encrypted on this phone and never sent anywhere.",
    )
    val STATEMENTS = arrayOf(
        "Statement PDFs attached to emails from your banks, card issuers, CAMS, KFintech, NSDL, CDSL and brokers are read automatically once email is connected. You can also import one from the phone.",
        "Card and bank statement rows become transactions. Rows already recorded from an SMS or email are merged, not doubled. A CAS or demat statement becomes investment holdings.",
    )
    val INVESTMENTS = arrayOf(
        "EPF: the balance from EPFO's passbook SMS. NPS: the holding value from NPS SMS.",
        "Mutual funds, shares and ETFs: units and market value from your CAS (CAMS, KFintech, NSDL or CDSL) and broker statements such as INDmoney.",
        "Anything else (PPF, gold, FDs, US stocks): add it yourself. A newer statement or SMS updates the holdings it covers.",
    )
    val CARD_LINK = arrayOf(
        "A debit card spends your bank account's money. Linking it makes its spends and ATM withdrawals count against that account, and the balance in its SMS updates the account.",
        "Hisaab suggests a link when the balance in the card's SMS matches one of your accounts.",
    )
    val DUPLICATES = arrayOf(
        "Hisaab merges an SMS, an email and a statement row for the same payment automatically when it's sure.",
        "When it isn't sure (same amount and account, no reference number), it asks you here. Compare shows both side by side with their original messages.",
    )
    val MANUAL_BALANCE = arrayOf(
        "Optional. If your bank's SMS don't include the balance, type it here once.",
        "Hisaab moves it on with every transaction after that, and a newer balance from a bank message replaces it.",
    )
    val APP_LOCK = arrayOf(
        "Asks for your fingerprint, face or screen lock each time Hisaab opens, so someone holding your unlocked phone can't see your finances.",
    )
}
