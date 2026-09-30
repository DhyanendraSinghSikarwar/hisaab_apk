package com.hisaab.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisaab.parser.model.Category
import com.hisaab.parser.model.TransactionType
import com.hisaab.shared.db.AccountDao
import com.hisaab.shared.db.AccountEntity
import com.hisaab.shared.db.TransactionDao
import com.hisaab.shared.db.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class SourceFilter(val label: String, val column: String?) { ALL("All", null), SMS("SMS", "SMS"), EMAIL("Email", "EMAIL"), CSV("Imported", "CSV") }

data class TransactionFilter(
    val search: String = "",
    val source: SourceFilter = SourceFilter.ALL,
    val type: TransactionType? = null,
    val category: Category? = null,
    val accountId: Long? = null,
    val limit: Int = PAGE,
) {
    companion object { const val PAGE = 100 }
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val dao: TransactionDao,
    accounts: AccountDao,
    handle: SavedStateHandle,
) : ViewModel() {
    private val _filter = MutableStateFlow(
        TransactionFilter(
            accountId = handle.get<Long>("accountId")?.takeIf { it > 0 },
            category = handle.get<String>("category")?.let { runCatching { Category.valueOf(it) }.getOrNull() },
        ),
    )
    val filter: StateFlow<TransactionFilter> = _filter

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val transactions: StateFlow<List<TransactionEntity>> = _filter.debounce(150).flatMapLatest { f ->
        dao.observe(
            search = f.search.trim().takeIf { it.isNotEmpty() }, accountId = f.accountId, category = f.category?.name,
            type = f.type?.name, source = f.source.column, from = 0, to = Long.MAX_VALUE, limit = f.limit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = accounts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(transform: (TransactionFilter) -> TransactionFilter) = _filter.update { transform(it).copy(limit = TransactionFilter.PAGE) }

    /** Called when the list nears its end: the next page is the same query with a larger LIMIT. */
    fun loadMore() {
        if (transactions.value.size >= _filter.value.limit) _filter.update { it.copy(limit = it.limit + TransactionFilter.PAGE) }
    }
}
