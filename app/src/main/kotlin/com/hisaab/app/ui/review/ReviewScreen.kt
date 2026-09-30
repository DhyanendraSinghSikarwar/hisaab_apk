package com.hisaab.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.ui.components.EmptyState
import com.hisaab.app.ui.components.TransactionRow
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import com.hisaab.shared.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewPair(val flagged: TransactionEntity, val candidate: TransactionEntity?)

@HiltViewModel
class ReviewViewModel @Inject constructor(private val dao: TransactionDao, private val repository: TransactionRepository) : ViewModel() {
    val pairs = dao.observeNeedsReview().map { list -> list.map { ReviewPair(it, it.duplicateOfId?.let { id -> dao.getById(id) }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun merge(id: Long) = viewModelScope.launch { repository.mergeFlagged(id) }
    fun keep(id: Long) = viewModelScope.launch { repository.keepSeparate(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, vm: ReviewViewModel = hiltViewModel()) {
    val pairs by vm.pairs.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Possible duplicates") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { inner ->
        if (pairs.isEmpty()) EmptyState(Icons.Filled.DoneAll, "All clear", "Nothing needs a decision.", Modifier.padding(inner))
        LazyColumn(contentPadding = PaddingValues(top = inner.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(pairs, key = { it.flagged.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(p.flagged.reviewReason ?: "These look like the same transaction", style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        TransactionRow(p.flagged, onClick = { onOpen(p.flagged.id) })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        p.candidate?.let { TransactionRow(it, onClick = { onOpen(it.id) }) }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                            OutlinedButton(onClick = { vm.keep(p.flagged.id) }) { Text("Keep both") }
                            Button(onClick = { vm.merge(p.flagged.id) }) { Text("Merge") }
                        }
                    }
                }
            }
        }
    }
}
