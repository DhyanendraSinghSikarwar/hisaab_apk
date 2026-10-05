package com.hisaab.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.settings.Profile
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.home.Avatar
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsStore,
    private val transactions: TransactionDao,
) : ViewModel() {
    val profile = settings.settings.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val count = transactions.observeTransactionCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val since = mutableStateOf<Long?>(null)

    init { viewModelScope.launch { since.value = transactions.firstTimestamp() } }

    fun save(name: String, email: String, phone: String, occupation: String, then: () -> Unit = {}) = viewModelScope.launch {
        settings.saveProfile(name, email, phone, occupation)
        then()
    }

    /** Copies the picked photo into app-private storage, scaled down, so it survives the gallery item being deleted. */
    fun setPhoto(uri: Uri) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
                val side = minOf(bitmap.width, bitmap.height)
                val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
                val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
                val file = File(context.filesDir, "profile-${System.currentTimeMillis()}.jpg")
                file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                // Only one photo is kept.
                context.filesDir.listFiles { f -> f.name.startsWith("profile-") && f != file }?.forEach { it.delete() }
                file.path
            }.getOrNull()
        }
        if (path != null) settings.setProfilePhoto(path)
    }

    fun removePhoto() = viewModelScope.launch {
        profile.value?.photoPath?.let { withContext(Dispatchers.IO) { File(it).delete() } }
        settings.setProfilePhoto(null)
    }

    fun dismissPrompt() = viewModelScope.launch { settings.dismissProfilePrompt() }
}

/** The user's photo in a circle, or their initials when there is no photo. */
@Composable
fun ProfileAvatar(name: String, photoPath: String?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? = remember(photoPath) {
        photoPath?.let { p -> runCatching { BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap, name, contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape).border(2.dp, Color(0xFFC9A227).copy(alpha = 0.8f), CircleShape),
        )
    } else {
        Box(modifier) { Avatar(name, size) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(onBack: () -> Unit, onOpenSettings: () -> Unit = {}, vm: ProfileViewModel = hiltViewModel()) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val p = profile ?: return
    var name by rememberSaveable(p.name) { mutableStateOf(p.name.orEmpty()) }
    var email by rememberSaveable(p.email) { mutableStateOf(p.email.orEmpty()) }
    var phone by rememberSaveable(p.phone) { mutableStateOf(p.phone.orEmpty()) }
    var occupation by rememberSaveable(p.occupation) { mutableStateOf(p.occupation.orEmpty()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setPhoto) }
    val changed = name.trim() != p.name.orEmpty() || email.trim() != p.email.orEmpty() || phone.trim() != p.phone.orEmpty() ||
        occupation.trim() != p.occupation.orEmpty()

    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Profile") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") } },
        )
    }) { inner ->
        Column(
            Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                ProfileAvatar(name.ifBlank { "You" }, p.photoPath, 112.dp,
                    Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CameraAlt, "Choose photo", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp)) }
            }
            if (p.photoPath != null) TextButton(onClick = vm::removePhoto) { Text("Remove photo") }
            Text(name.ifBlank { "Your name" }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                listOfNotNull(
                    "$count transactions",
                    vm.since.value?.let { "tracking since ${Periods.month(java.time.YearMonth.from(Periods.localDate(it)))}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Field(name, { name = it }, "Name", Icons.Filled.Person, KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            Field(email, { email = it }, "Email (optional)", Icons.Filled.Email, KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            Field(phone, { phone = it }, "Phone (optional)", Icons.Filled.Phone, KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
            Field(occupation, { occupation = it }, "Occupation (optional)", Icons.Filled.Work,
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done))

            Button(onClick = { vm.save(name, email, phone, occupation) }, enabled = changed && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Save profile")
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Your profile stays on this phone. Hisaab has no server and never uploads it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, keyboard: KeyboardOptions) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        leadingIcon = { Icon(icon, null) }, keyboardOptions = keyboard, shape = MaterialTheme.shapes.medium,
    )
}

/** First run: a short, friendly sheet that asks for a name so Home can greet the user. Can be skipped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupSheet(onDone: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setPhoto) }
    val profile by vm.profile.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = { vm.dismissPrompt(); onDone() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Create your profile", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Add your name so Hisaab feels like yours. It stays on this phone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            ProfileAvatar(name.ifBlank { "You" }, profile?.photoPath, 88.dp,
                Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (profile?.photoPath == null) "Add a photo" else "Change photo")
            }
            Field(name, { name = it }, "Your name", Icons.Filled.Person, KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            Field(email, { email = it }, "Email (optional)", Icons.Filled.Email, KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done))
            Button(onClick = { vm.save(name, email, "", "", then = onDone) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
            TextButton(onClick = { vm.dismissPrompt(); onDone() }) { Text("Later") }
        }
    }
}
