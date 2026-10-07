package com.hisaab.app.ui.profile

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hisaab.app.i18n.t
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val PAN = Regex("[A-Z]{5}[0-9]{4}[A-Z]")

fun validPan(pan: String) = pan.isBlank() || PAN.matches(pan.trim().uppercase())

/**
 * A second name and mobile a bank or card may have on record (another spelling, a maiden name, an old number).
 * Stored only in the sealed statement identity. [phonePrefix] shows a fixed country code, as at sign-up.
 */
@Composable
fun AlternateFields(
    altName: String, onAltName: (String) -> Unit, altPhone: String, onAltPhone: (String) -> Unit,
    phonePrefix: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            altName, onAltName, Modifier.fillMaxWidth(), label = { Text(t("Alternate name (optional)")) },
            leadingIcon = { Icon(Icons.Filled.PersonOutline, null) }, singleLine = true, shape = MaterialTheme.shapes.medium,
            supportingText = { Text(t("Name on another bank or card, or a spelling variant")) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            altPhone, { v -> onAltPhone(if (phonePrefix != null) v.filter(Char::isDigit).take(10) else v) }, Modifier.fillMaxWidth(),
            label = { Text(t("Alternate mobile (optional)")) }, leadingIcon = { Icon(Icons.Filled.PhoneAndroid, null) },
            prefix = if (phonePrefix != null) ({ Text(phonePrefix) }) else null, singleLine = true, shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        )
    }
}

/** Date of birth and PAN: what banks lock statements with. Used at sign-up and on the profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementDetailsFields(dob: LocalDate?, onDob: (LocalDate?) -> Unit, pan: String, onPan: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    LaunchedEffect(pressed) { if (pressed) picking = true }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            dob?.format(DateTimeFormatter.ofPattern("d MMM yyyy")).orEmpty(), {}, Modifier.fillMaxWidth(), readOnly = true,
            label = { Text(t("Date of birth")) }, leadingIcon = { Icon(Icons.Filled.Cake, null) }, singleLine = true,
            shape = MaterialTheme.shapes.medium, interactionSource = press,
        )
        OutlinedTextField(
            pan, { onPan(it.uppercase().filter(Char::isLetterOrDigit).take(10)) }, Modifier.fillMaxWidth(),
            label = { Text(t("PAN")) }, leadingIcon = { Icon(Icons.Filled.Badge, null) }, singleLine = true,
            isError = !validPan(pan), shape = MaterialTheme.shapes.medium,
            supportingText = if (!validPan(pan)) ({ Text(t("Like ABCDE1234F")) }) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                t("Banks lock statements with these. DhanKosh tries them to open your PDFs. Encrypted on this phone."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (dob ?: LocalDate.of(1990, 1, 1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onDob(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    picking = false
                }) { Text(t("Done")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(t("Cancel")) } },
        ) { DatePicker(state, Modifier.padding(top = 8.dp), showModeToggle = true) }
    }
}
