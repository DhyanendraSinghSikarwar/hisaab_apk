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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.settings.AppSettingsStore
import com.hisaab.app.settings.Profile
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.format.Periods
import com.hisaab.app.ui.home.Avatar
import com.hisaab.app.ui.theme.Hx
import com.hisaab.shared.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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
    private val passwords: com.hisaab.email.statement.StatementPasswordStore,
    private val statements: com.hisaab.email.statement.StatementProcessor,
) : ViewModel() {
    val profile = settings.settings.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val identity = passwords.identity.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val count = transactions.observeTransactionCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val since = mutableStateOf<Long?>(null)

    init { viewModelScope.launch { since.value = transactions.firstTimestamp() } }

    fun save(name: String, email: String, phone: String, occupation: String, dob: java.time.LocalDate? = identity.value?.dob,
             pan: String = identity.value?.pan.orEmpty(), altName: String = identity.value?.altName.orEmpty(),
             altPhone: String = identity.value?.altPhone.orEmpty(), then: () -> Unit = {}) = viewModelScope.launch {
        settings.saveProfile(name, email, phone, occupation)
        // Alternates live only in the sealed identity, never in plain settings.
        passwords.setIdentity(com.hisaab.parser.statement.Identity(
            name.trim().ifEmpty { null }, dob, pan.trim().ifEmpty { null }, phone.trim().ifEmpty { null },
            altName = altName.trim().ifEmpty { null }, altPhone = altPhone.trim().ifEmpty { null },
        ))
        then()
        // New details may open statements that were waiting for a password.
        statements.retryLocked()
    }

    private val _cropSource = MutableStateFlow<Bitmap?>(null)
    /** The picked photo, upright and memory-safe, while the user frames it; null when no crop is open. */
    val cropSource = _cropSource.asStateFlow()
    private val _savingPhoto = MutableStateFlow(false)
    val savingPhoto = _savingPhoto.asStateFlow()

    /** Loads the picked photo for the crop dialog. Nothing is saved until the user confirms. */
    fun beginCrop(uri: Uri) = viewModelScope.launch {
        _cropSource.value = withContext(Dispatchers.IO) { runCatching { decodeForCrop(context, uri) }.getOrNull() }
    }

    fun cancelCrop() { _cropSource.value = null }

    /**
     * Renders the framed circle to a 512x512 JPEG in app-private storage, so it survives the gallery item being
     * deleted, and keeps only that one photo.
     */
    fun savePhoto(spec: CropSpec) = viewModelScope.launch {
        val src = _cropSource.value ?: return@launch
        _savingPhoto.value = true
        val path = withContext(Dispatchers.IO) {
            runCatching {
                val square = renderCrop(src, spec, 512)
                val file = File(context.filesDir, "profile-${System.currentTimeMillis()}.jpg")
                file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                square.recycle()
                // Only one photo is kept.
                context.filesDir.listFiles { f -> f.name.startsWith("profile-") && f != file }?.forEach { it.delete() }
                file.path
            }.getOrNull()
        }
        if (path != null) settings.setProfilePhoto(path)
        _savingPhoto.value = false
        _cropSource.value = null
    }

    fun removePhoto() = viewModelScope.launch {
        profile.value?.photoPath?.let { withContext(Dispatchers.IO) { File(it).delete() } }
        settings.setProfilePhoto(null)
    }

    fun dismissPrompt() = viewModelScope.launch { settings.dismissProfilePrompt() }
}

/** The user's photo in a circle, or their initials when there is no photo. */
@Composable
fun ProfileAvatar(name: String, photoPath: String?, size: Dp, modifier: Modifier = Modifier, ring: Boolean = true) {
    val bitmap: ImageBitmap? = remember(photoPath) {
        photoPath?.let { p -> runCatching { BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap, name, contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
                .let { if (ring) it.border(2.dp, Color(0xFFC9A227).copy(alpha = 0.8f), CircleShape) else it },
        )
    } else if (!ring) {
        // Large initials in the theme's hero colours, for the profile header.
        val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "H" }
        Box(
            modifier.size(size).clip(CircleShape).background(com.hisaab.app.ui.theme.HeroBrush)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.36f).sp)
        }
    } else {
        Box(modifier) { Avatar(name, size) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenSettings: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenDataSources: () -> Unit = {},
    vm: ProfileViewModel = hiltViewModel(),
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val identity by vm.identity.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val cropSource by vm.cropSource.collectAsStateWithLifecycle()
    val saving by vm.savingPhoto.collectAsStateWithLifecycle()
    val p = profile ?: return
    val id = identity ?: return
    var dob by rememberSaveable(id.dob) { mutableStateOf(id.dob) }
    var pan by rememberSaveable(id.pan) { mutableStateOf(id.pan.orEmpty()) }
    var altName by rememberSaveable(id.altName) { mutableStateOf(id.altName.orEmpty()) }
    var altPhone by rememberSaveable(id.altPhone) { mutableStateOf(id.altPhone.orEmpty()) }
    var name by rememberSaveable(p.name) { mutableStateOf(p.name.orEmpty()) }
    var email by rememberSaveable(p.email) { mutableStateOf(p.email.orEmpty()) }
    var phone by rememberSaveable(p.phone) { mutableStateOf(p.phone.orEmpty()) }
    var occupation by rememberSaveable(p.occupation) { mutableStateOf(p.occupation.orEmpty()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::beginCrop) }
    val launchPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val changed = name.trim() != p.name.orEmpty() || email.trim() != p.email.orEmpty() || phone.trim() != p.phone.orEmpty() ||
        occupation.trim() != p.occupation.orEmpty() || dob != id.dob || pan.trim() != id.pan.orEmpty() ||
        altName.trim() != id.altName.orEmpty() || altPhone.trim() != id.altPhone.orEmpty()
    val canSave = changed && name.isNotBlank() && validPan(pan)

    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            colors = com.hisaab.app.ui.theme.clearTopBar(), title = { Text("Profile") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { inner ->
        Column(
            Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProfileHeader(
                name = name, photoPath = p.photoPath,
                subtitle = listOfNotNull(
                    "$count transactions",
                    vm.since.value?.let { "tracking since ${Periods.month(java.time.YearMonth.from(Periods.localDate(it)))}" },
                ).joinToString(" · "),
                onPickPhoto = launchPick, onRemovePhoto = vm::removePhoto,
            )

            HCard(title = "Personal") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(name, { name = it }, "Full name (as per bank)", Icons.Filled.Person,
                        KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        error = if (name.isBlank() && changed) "Your name is needed to save" else null)
                    Field(email, { email = it }, "Email (optional)", Icons.Filled.Email, KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    Field(phone, { phone = it }, "Mobile (as per bank)", Icons.Filled.Phone, KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                    Field(occupation, { occupation = it }, "Occupation (optional)", Icons.Filled.Work,
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                }
            }

            HCard(title = "For locked statements") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AlternateFields(altName, { altName = it }, altPhone, { altPhone = it })
                    StatementDetailsFields(dob, { dob = it }, pan, { pan = it })
                }
            }

            val lift by animateFloatAsState(if (canSave) 1f else 0f, tween(260), label = "save")
            Button(
                onClick = { vm.save(name, email, phone, occupation, dob, pan, altName, altPhone) }, enabled = canSave,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX = 0.98f + 0.02f * lift; scaleY = scaleX },
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (changed) "Save profile" else "Saved", style = MaterialTheme.typography.titleSmall)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, null, tint = Hx.text2, modifier = Modifier.size(13.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    "Stays on this phone. Artha has no server and never uploads it.",
                    style = MaterialTheme.typography.labelSmall, color = Hx.text2, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    cropSource?.let { src -> PhotoCropDialog(src, saving, onCancel = vm::cancelCrop, onConfirm = { vm.savePhoto(it) }) }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboard: KeyboardOptions, error: String? = null,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        leadingIcon = { Icon(icon, null) }, keyboardOptions = keyboard, shape = MaterialTheme.shapes.medium,
        isError = error != null, supportingText = if (error != null) ({ Text(error) }) else null,
    )
}

/** First run: a short, friendly sheet that asks for a name so Home can greet the user. Can be skipped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupSheet(onDone: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::beginCrop) }
    val profile by vm.profile.collectAsStateWithLifecycle()
    val cropSource by vm.cropSource.collectAsStateWithLifecycle()
    val saving by vm.savingPhoto.collectAsStateWithLifecycle()
    cropSource?.let { src -> PhotoCropDialog(src, saving, onCancel = vm::cancelCrop, onConfirm = { vm.savePhoto(it) }) }
    ModalBottomSheet(onDismissRequest = { vm.dismissPrompt(); onDone() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Create your profile", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Add your name so Artha feels like yours. It stays on this phone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            ProfileAvatar(name.ifBlank { "You" }, profile?.photoPath, 88.dp,
                Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (profile?.photoPath == null) "Add a photo" else "Change photo")
            }
            Field(name, { name = it }, "Full name (as per bank)", Icons.Filled.Person, KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            Field(email, { email = it }, "Email (optional)", Icons.Filled.Email, KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done))
            // Keeps any mobile and occupation already saved; the full profile edits those.
            Button(onClick = { vm.save(name, email, profile?.phone.orEmpty(), profile?.occupation.orEmpty(), then = onDone) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
            TextButton(onClick = { vm.dismissPrompt(); onDone() }) { Text("Later") }
        }
    }
}
