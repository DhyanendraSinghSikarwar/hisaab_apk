package com.hisaab.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.security.AppLockGate
import com.hisaab.email.imap.MailAuthException
import com.hisaab.email.imap.MailConnector
import com.hisaab.email.imap.MailServer
import com.hisaab.email.imap.MailServers
import com.hisaab.email.imap.VerificationCode
import com.hisaab.email.sync.GmailScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

enum class ConnectStep { ADDRESS, SIGN_IN, CODE, DONE }

data class EmailConnectState(
    val step: ConnectStep = ConnectStep.ADDRESS,
    val email: String = "",
    val server: MailServer? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

/** Drives the connect-by-email steps: address, sign in with an app password, then the mailed 6-digit code. */
@HiltViewModel
class EmailConnectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connector: MailConnector,
) : ViewModel() {
    private val _state = MutableStateFlow(EmailConnectState())
    val state = _state.asStateFlow()

    fun submitAddress(email: String) {
        val e = email.trim()
        if (!MailServers.isValidAddress(e)) {
            _state.update { it.copy(error = "Enter a full email address, like name@gmail.com") }
            return
        }
        _state.update { it.copy(step = ConnectStep.SIGN_IN, email = e.lowercase(), server = MailServers.forAddress(e), error = null) }
    }

    fun signIn(password: String) = work {
        if (password.isBlank()) throw IOException("Enter the app password")
        connector.signInAndSendCode(_state.value.email, password)
        _state.update { it.copy(step = ConnectStep.CODE, info = "We sent a 6-digit code to ${it.email}. Check your inbox (and spam).") }
    }

    fun resend() = work {
        connector.resendCode()
        _state.update { it.copy(info = "A new code is on its way to ${it.email}.") }
    }

    fun verify(code: String) = work {
        when (connector.verify(code)) {
            VerificationCode.Result.OK -> {
                GmailScheduler.schedulePeriodic(context)
                GmailScheduler.syncNow(context)
                _state.update { it.copy(step = ConnectStep.DONE) }
            }
            VerificationCode.Result.WRONG -> throw IOException("That code doesn't match. Check the latest email from Hisaab.")
            VerificationCode.Result.EXPIRED -> throw IOException("The code expired. Tap Resend code.")
            VerificationCode.Result.TOO_MANY_ATTEMPTS -> throw IOException("Too many wrong tries. Tap Resend code for a new one.")
            VerificationCode.Result.NONE -> throw IOException("Sign in again to get a code.")
        }
    }

    fun back() {
        connector.cancel()
        _state.update { s ->
            when (s.step) {
                ConnectStep.CODE -> s.copy(step = ConnectStep.SIGN_IN, error = null, info = null)
                else -> s.copy(step = ConnectStep.ADDRESS, error = null, info = null)
            }
        }
    }

    fun reset() {
        connector.cancel()
        _state.value = EmailConnectState()
    }

    private fun work(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: MailAuthException) {
                _state.update { it.copy(error = signInHelp(it.server, e.message)) }
            } catch (e: IOException) {
                _state.update { it.copy(error = e.message ?: "Could not reach the mail server. Check your internet connection.") }
            } catch (e: IllegalStateException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun signInHelp(server: MailServer?, detail: String?): String {
        val base = "Sign-in was refused" + (detail?.let { " ($it)" } ?: "") + "."
        return if (server?.appPasswordUrl != null) "$base ${server.provider} needs an app password here, not your normal password." else base
    }
}

@Composable
fun EmailConnectDialog(onDismiss: () -> Unit, onUseGoogle: (() -> Unit)?, vm: EmailConnectViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val close = { vm.reset(); onDismiss() }

    AlertDialog(
        onDismissRequest = { if (!s.busy) close() },
        title = {
            Text(
                when (s.step) {
                    ConnectStep.ADDRESS -> "Connect your email"
                    ConnectStep.SIGN_IN -> "Sign in to ${s.server?.provider ?: "email"}"
                    ConnectStep.CODE -> "Verify your email"
                    ConnectStep.DONE -> "Email connected"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (s.step) {
                    ConnectStep.ADDRESS -> {
                        Text("Hisaab reads bank alert emails from your inbox, on this phone only.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        )
                    }
                    ConnectStep.SIGN_IN -> {
                        Text(s.email, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Sign in with an app password: a 16-character password your email provider makes just for this app. " +
                                "Your normal password won't work, and you can revoke the app password at any time.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        s.server?.appPasswordUrl?.let { url ->
                            TextButton(onClick = { openUrl(context, url) }) { Text("Create an app password for ${s.server?.provider}") }
                            if (s.server?.provider == "Gmail") {
                                Text("Gmail needs 2-Step Verification turned on before it offers app passwords.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedTextField(
                            password, { password = it }, Modifier.fillMaxWidth(), label = { Text("App password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        )
                    }
                    ConnectStep.CODE -> {
                        s.info?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        OutlinedTextField(
                            code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("6-digit code") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        )
                        TextButton(onClick = vm::resend, enabled = !s.busy) { Text("Resend code") }
                    }
                    ConnectStep.DONE -> Text(
                        "${s.email} is verified. Hisaab is fetching bank emails from the look-back period now, then checks every hour.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (s.step == ConnectStep.ADDRESS && onUseGoogle != null) {
                    TextButton(onClick = { vm.reset(); onUseGoogle() }) { Text("Advanced: Google sign-in (needs a Google Cloud OAuth client)") }
                }
            }
        },
        confirmButton = {
            when (s.step) {
                ConnectStep.ADDRESS -> TextButton(onClick = { vm.submitAddress(email) }) { Text("Continue") }
                ConnectStep.SIGN_IN -> TextButton(onClick = { vm.signIn(password) }, enabled = !s.busy) { Text("Sign in & send code") }
                ConnectStep.CODE -> TextButton(onClick = { vm.verify(code) }, enabled = !s.busy && code.length == 6) { Text("Verify") }
                ConnectStep.DONE -> TextButton(onClick = close) { Text("Done") }
            }
        },
        dismissButton = {
            when (s.step) {
                ConnectStep.ADDRESS, ConnectStep.DONE -> if (s.step == ConnectStep.ADDRESS) TextButton(onClick = close) { Text("Cancel") }
                else -> TextButton(onClick = vm::back, enabled = !s.busy) { Text("Back") }
            }
        },
    )
}

private fun openUrl(context: Context, url: String) {
    AppLockGate.skipNextLock()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
