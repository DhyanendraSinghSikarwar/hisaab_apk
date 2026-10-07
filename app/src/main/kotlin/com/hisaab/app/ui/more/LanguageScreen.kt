package com.hisaab.app.ui.more

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.I18n
import com.hisaab.app.i18n.Language
import com.hisaab.app.i18n.LanguageStore
import com.hisaab.app.i18n.PackInfo
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.theme.Hx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LanguageUi(val packs: List<PackInfo> = emptyList(), val busy: Language? = null)

@HiltViewModel
class LanguageViewModel @Inject constructor(private val store: LanguageStore) : ViewModel() {
    private val _ui = MutableStateFlow(LanguageUi(store.packs()))
    val ui = _ui.asStateFlow()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 2)

    private fun refresh() = _ui.update { it.copy(packs = store.packs()) }

    /** Uses [l]; downloads its pack first when needed. */
    fun choose(l: Language) = viewModelScope.launch {
        if (_ui.value.busy != null) return@launch
        if (!store.isDownloaded(l)) {
            _ui.update { it.copy(busy = l) }
            val ok = runCatching { store.download(l) }.isSuccess
            _ui.update { it.copy(busy = null) }
            refresh()
            if (!ok) { messages.tryEmit(t("Couldn't download {lang}. Check your connection.", "lang" to l.english)); return@launch }
        }
        runCatching { store.use(l) }.onFailure { messages.tryEmit(t("That language pack is damaged. Download it again.")) }
    }

    fun delete(l: Language) = viewModelScope.launch { store.delete(l); refresh() }
}

/** English is built in; other languages download when chosen and can be removed again. */
@Composable
fun LanguageRoute(onBack: () -> Unit, vm: LanguageViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    MoreScaffold(t("Language"), onBack, snackbar = snackbar) { inner ->
        LazyColumn(contentPadding = listPadding(inner), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                HelpText(t("English is built in. Other languages download when you pick them (about 100 KB each) and work offline after that."))
            }
            items(ui.packs, key = { it.language.code }) { p ->
                LanguageRow(p, selected = I18n.language == p.language, busy = ui.busy == p.language, onChoose = { vm.choose(p.language) }, onDelete = { vm.delete(p.language) })
            }
        }
    }
}

@Composable
private fun LanguageRow(p: PackInfo, selected: Boolean, busy: Boolean, onChoose: () -> Unit, onDelete: () -> Unit) {
    HCard(onClick = onChoose, padding = 14.dp, container = if (selected) Hx.accentSoft else Hx.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Hx.accent else Hx.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    p.language.native.take(1), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) androidx.compose.ui.graphics.Color.White else Hx.text2,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.language.native, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        p.language == Language.ENGLISH -> t("Built in")
                        p.downloaded -> "${p.language.english} · ${t("Downloaded")}"
                        else -> "${p.language.english} · ${t("Tap to download")}"
                    },
                    fontSize = 12.sp, color = Hx.text2,
                )
            }
            AnimatedContent(
                when { busy -> 0; selected -> 1; !p.downloaded -> 2; else -> 3 },
                transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "lang-state",
            ) { state ->
                when (state) {
                    0 -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    1 -> Icon(Icons.Filled.CheckCircle, t("In use"), tint = Hx.accent)
                    2 -> Icon(Icons.Outlined.CloudDownload, t("Download"), tint = Hx.text2)
                    else -> IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, t("Remove download"), tint = Hx.text2) }
                }
            }
        }
    }
}
