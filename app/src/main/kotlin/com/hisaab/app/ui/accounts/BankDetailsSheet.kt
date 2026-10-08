package com.hisaab.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.components.HRow
import com.hisaab.app.ui.theme.Hx
import com.hisaab.email.statement.AccountDetails
import com.hisaab.email.statement.StatementPasswordStore
import com.hisaab.email.statement.StatementProcessor
import com.hisaab.parser.statement.Identity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The profile's name, mobile and email, which an account falls back to when its own field is empty. */
data class ProfileValues(val name: String = "", val mobile: String = "", val email: String = "")

@HiltViewModel
class BankDetailsViewModel @Inject constructor(
    private val passwords: StatementPasswordStore,
    private val statements: StatementProcessor,
    private val settings: AppSettingsStore,
) : ViewModel() {
    val profile: Flow<ProfileValues> = combine(passwords.identity, settings.settings) { id, s ->
        ProfileValues(
            name = id.name ?: s.profile.name.orEmpty(), mobile = id.phone ?: s.profile.phone.orEmpty(),
            email = id.email ?: s.profile.email.orEmpty(),
        )
    }

    fun details(accountId: Long): Flow<AccountDetails> = passwords.accountDetails(accountId)

    /**
     * Saves one account's details. Each of [toProfile] ("name", "mobile", "email") that is blank in the profile is
     * also saved there. Waiting statements are retried afterwards.
     */
    fun save(accountId: Long, d: AccountDetails, toProfile: Set<String>, done: () -> Unit) = viewModelScope.launch {
        passwords.setAccountDetails(accountId, d)
        if (toProfile.isNotEmpty()) {
            val id = passwords.identity.first()
            val p = settings.settings.first().profile
            val name = if ("name" in toProfile) d.name.trim() else id.name ?: p.name.orEmpty()
            val mobile = if ("mobile" in toProfile) d.mobile.trim() else id.phone ?: p.phone.orEmpty()
            val email = if ("email" in toProfile) d.email.trim() else id.email ?: p.email.orEmpty()
            settings.saveProfile(name, email, mobile, p.occupation.orEmpty())
            passwords.setIdentity(
                id.copy(name = name.ifBlank { null }, phone = mobile.ifBlank { null }, email = email.ifBlank { null }),
            )
        }
        done()
        statements.retryLocked()
    }
}

/**
 * "Statement unlock details" for one account, inside its edit sheet: customer ID, account number, IFSC, and the
 * name, mobile and email the bank holds. Name, mobile and email follow the profile until set here.
 */
@Composable
fun BankDetailsSection(accountId: Long, bankName: String, vm: BankDetailsViewModel = hiltViewModel()) {
    val saved by remember(accountId) { vm.details(accountId) }.collectAsState(AccountDetails())
    val profile by vm.profile.collectAsState(ProfileValues())
    var open by remember { mutableStateOf(false) }
    var loaded by remember(accountId) { mutableStateOf(false) }
    var customerId by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var alsoProfile by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    // Fill the fields once, from what is saved; later edits stay local until Save.
    LaunchedEffect(saved) {
        if (!loaded) {
            customerId = saved.customerId; number = saved.accountNumber; ifsc = saved.ifsc
            name = saved.name; mobile = saved.mobile; email = saved.email
            loaded = true
        }
    }
    val filled = listOf(saved.customerId, saved.accountNumber, saved.ifsc, saved.name, saved.mobile, saved.email).count { it.isNotBlank() }
    val fillable = buildSet {
        if (profile.name.isBlank() && name.isNotBlank()) add("name")
        if (profile.mobile.isBlank() && mobile.isNotBlank()) add("mobile")
        if (profile.email.isBlank() && email.isNotBlank()) add("email")
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HRow(
            title = t("Statement unlock details"),
            subtitle = if (filled > 0) t("{n} saved", "n" to filled) else t("Customer ID, account number and more"),
            onClick = { open = !open },
        ) { Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Hx.text2) }
        if (open) {
            Field(customerId, { customerId = it.filter(Char::isLetterOrDigit).take(20) }, t("Customer ID"), KeyboardType.Number)
            Field(number, { number = it.filter(Char::isLetterOrDigit).take(24) }, t("Full account number"), KeyboardType.Number)
            Field(ifsc, { ifsc = it.uppercase().filter(Char::isLetterOrDigit).take(11) }, t("IFSC"), KeyboardType.Text, KeyboardCapitalization.Characters)
            Field(name, { name = it }, t("Name on the account"), KeyboardType.Text, KeyboardCapitalization.Words, inherited = profile.name)
            Field(mobile, { mobile = it }, t("Mobile registered with the bank"), KeyboardType.Phone, inherited = profile.mobile)
            Field(email, { email = it }, t("Email registered with the bank"), KeyboardType.Email, inherited = profile.email, last = true)
            if (fillable.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(alsoProfile, { alsoProfile = it })
                    Text(t("Also save to profile"), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    t("Encrypted on this phone. Used only to open your statements."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    vm.save(accountId, AccountDetails(customerId, number, ifsc, name, mobile, email), if (alsoProfile) fillable else emptySet()) { done = true }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (done) t("Saved") else t("Save details")) }
        }
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String, type: KeyboardType,
    caps: KeyboardCapitalization = KeyboardCapitalization.None, inherited: String = "", last: Boolean = false,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        placeholder = if (inherited.isNotBlank()) ({ Text(inherited) }) else null,
        supportingText = if (inherited.isNotBlank() && value.isBlank()) ({ Text(t("Same as profile · {value}", "value" to inherited)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = type, capitalization = caps, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        shape = MaterialTheme.shapes.medium,
    )
}
