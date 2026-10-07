package com.hisaab.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.ui.settings.EmailConnectDialog
import com.hisaab.app.ui.theme.BackdropColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val settings: AppSettingsStore,
    mail: com.hisaab.email.sync.GmailSettingsStore,
    private val passwords: com.hisaab.email.statement.StatementPasswordStore,
) : ViewModel() {
    /**
     * Null while loading; true until the user has a name and has signed in with an email or phone. Someone who
     * already connected an inbox before this screen existed counts as signed in.
     */
    val needed = kotlinx.coroutines.flow.combine(settings.settings, mail.settings) { a, m ->
        a.profile.name == null || (a.profile.email == null && a.profile.phone == null && !m.connected)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentName = settings.settings.map { it.profile.name.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun finish(
        name: String, email: String?, phone: String?, dob: java.time.LocalDate? = null, pan: String = "",
        altName: String = "", altPhone: String? = null,
    ) = viewModelScope.launch {
        val p = settings.settings.first().profile
        val mobile = phone ?: p.phone.orEmpty()
        // The statement details first: saving the name ends sign-up and closes this screen.
        // Alternates are kept only in the sealed identity, never in plain settings.
        passwords.setIdentity(com.hisaab.parser.statement.Identity(
            name.trim(), dob, pan.trim().ifEmpty { null }, mobile.ifBlank { null },
            altName = altName.trim().ifEmpty { null }, altPhone = altPhone?.ifBlank { null },
        ))
        settings.saveProfile(name, email ?: p.email.orEmpty(), mobile, p.occupation.orEmpty())
    }
}

/**
 * First run: sign in with email (verified with a code, and the inbox is connected for bank alerts) or a phone
 * number. Either creates the profile. Everything stays on the phone.
 */
@Composable
fun WelcomeScreen(vm: WelcomeViewModel = hiltViewModel()) {
    val known by vm.currentName.collectAsStateWithLifecycle()
    var name by rememberSaveable(known) { mutableStateOf(known) }
    var mode by rememberSaveable { mutableStateOf("choose") }
    var phone by rememberSaveable { mutableStateOf("") }
    var connecting by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf<String?>(null) }
    var dob by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
    var pan by rememberSaveable { mutableStateOf("") }
    var altName by rememberSaveable { mutableStateOf("") }
    var altPhone by rememberSaveable { mutableStateOf("") }
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BackdropColors.emerald.copy(alpha = 0.18f), Color.Transparent, BackdropColors.sapphire.copy(alpha = 0.12f))))) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(72.dp))
                // The DhanKosh mark on a soft tile, as on the launcher icon.
                Box(
                    Modifier.size(104.dp).background(
                        Brush.linearGradient(listOf(Color(0xFFF7FAF8), Color(0xFFE9EEF6))), RoundedCornerShape(30.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(com.hisaab.app.R.drawable.artha_mark), "DhanKosh",
                        modifier = Modifier.size(72.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text("DhanKosh", style = MaterialTheme.typography.displaySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(t("Know your money."), style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.dp))

                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t("Full name (as per bank)")) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Person, null) }, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
                Spacer(Modifier.height(16.dp))
                AnimatedContent(mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "signin") { m ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (m == "details") {
                            Text(t("Unlock your statements"), style = MaterialTheme.typography.titleMedium)
                            if (email != null) {
                                OutlinedTextField(
                                    phone, { phone = it.filter(Char::isDigit).take(10) }, Modifier.fillMaxWidth(), label = { Text(t("Mobile (as per bank)")) },
                                    prefix = { Text("+91 ") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Phone, null) }, shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                )
                            }
                            com.hisaab.app.ui.profile.AlternateFields(altName, { altName = it }, altPhone, { altPhone = it }, phonePrefix = "+91 ")
                            com.hisaab.app.ui.profile.StatementDetailsFields(dob, { dob = it }, pan, { pan = it })
                            Button(
                                onClick = {
                                    vm.finish(name, email, phone.takeIf { it.length == 10 }?.let { "+91 $it" }, dob, pan,
                                        altName, altPhone.takeIf { it.length == 10 }?.let { "+91 $it" })
                                },
                                enabled = com.hisaab.app.ui.profile.validPan(pan), modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) { Text(t("Finish")) }
                            TextButton(onClick = { vm.finish(name, email, phone.takeIf { it.length == 10 }?.let { "+91 $it" }) }, modifier = Modifier.fillMaxWidth()) {
                                Text(t("Skip for now"))
                            }
                        } else if (m == "phone") {
                            OutlinedTextField(
                                phone, { phone = it.filter(Char::isDigit).take(10) }, Modifier.fillMaxWidth(), label = { Text(t("Mobile (as per bank)")) },
                                prefix = { Text("+91 ") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Phone, null) }, shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                            )
                            Button(
                                onClick = { mode = "details" }, enabled = name.isNotBlank() && phone.length == 10,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) { Text(t("Continue")) }
                            TextButton(onClick = { mode = "choose" }, modifier = Modifier.fillMaxWidth()) { Text(t("Use email instead")) }
                        } else {
                            Button(onClick = { connecting = true }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Icon(Icons.Filled.Email, null); Spacer(Modifier.size(10.dp)); Text(t("Continue with email"))
                            }
                            OutlinedButton(onClick = { mode = "phone" }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Icon(Icons.Filled.Phone, null); Spacer(Modifier.size(10.dp)); Text(t("Continue with phone number"))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    t("Email sign-in also reads your bank alerts and statements."),
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = c.primary, modifier = Modifier.size(14.dp))
                    Text(" " + t("Your data never leaves this phone."), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if (connecting) {
        EmailConnectDialog(onDismiss = { connecting = false }, onUseGoogle = null, onConnected = { e -> email = e; connecting = false; mode = "details" })
    }
}
