package com.hisaab.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hisaab.app.i18n.t
import com.hisaab.app.ui.category.CategoriesViewModel
import com.hisaab.app.ui.category.CategoryLook
import com.hisaab.app.ui.category.IconBadge
import com.hisaab.app.ui.category.NewItemDialog
import com.hisaab.app.ui.category.subsFor
import com.hisaab.app.ui.components.CardGap
import com.hisaab.app.ui.components.CategorySheet
import com.hisaab.app.ui.components.HCard
import com.hisaab.app.ui.components.IconLibrary
import com.hisaab.app.ui.components.Pill
import com.hisaab.app.ui.components.Segmented
import com.hisaab.app.ui.components.Tag
import com.hisaab.app.ui.theme.Hx
import com.hisaab.parser.model.Category
import com.hisaab.shared.db.CustomCategoryEntity
import com.hisaab.shared.db.MerchantRuleDao
import com.hisaab.shared.db.MerchantRuleEntity
import com.hisaab.shared.db.RuleMatch
import com.hisaab.shared.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val rules: MerchantRuleDao,
    private val repo: TransactionRepository,
) : ViewModel() {
    /** The user's own rules first, then learned ones; null until the first read so the empty state does not flash. */
    val list: StateFlow<List<MerchantRuleEntity>?> = rules.observeAll()
        .map { l -> l.sortedWith(compareByDescending<MerchantRuleEntity> { it.manual }.thenBy { it.merchantKey }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(r: MerchantRuleEntity) = viewModelScope.launch { rules.delete(r.merchantKey) }
    fun restore(r: MerchantRuleEntity) = viewModelScope.launch { rules.upsert(r) }

    /** How many past transactions a rule with this text would re-file, live as the user types. */
    fun matchCount(key: String, match: RuleMatch): Flow<Int> =
        if (key.length < MIN_KEY) flowOf(0)
        else rules.observeMatchCount(key, MerchantRuleEntity.likeOf(key), match == RuleMatch.CONTAINS)

    /** Saves the rule; [onDone] gets how many past transactions were re-filed. */
    fun save(rule: MerchantRuleEntity, previousKey: String?, applyToPast: Boolean, onDone: (Int) -> Unit) = viewModelScope.launch {
        onDone(repo.saveRule(rule, previousKey, applyToPast))
    }

    companion object {
        const val MIN_KEY = 2
    }
}

/** "swiggy instamart" → "Swiggy Instamart"; UPI ids stay as they are. */
internal fun ruleTitle(key: String): String =
    if ('@' in key) key else key.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

/** How a rule's category shows: the user's own category when it has one, else the built-in one. */
private fun lookOf(category: Category, customId: Long?, custom: List<CustomCategoryEntity>): CategoryLook =
    customId?.let { id -> custom.firstOrNull { it.id == id } }?.let { CategoryLook.of(it) } ?: CategoryLook.of(category)

/** The rule being written or edited. [original] is null for a new rule. */
private data class RuleDraft(val original: MerchantRuleEntity?)

@Composable
fun RulesRoute(onBack: () -> Unit, vm: RulesViewModel = hiltViewModel(), cats: CategoriesViewModel = hiltViewModel()) {
    val rules by vm.list.collectAsStateWithLifecycle()
    val custom by cats.custom.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<RuleDraft?>(null) }

    fun remove(r: MerchantRuleEntity) {
        vm.delete(r)
        scope.launch {
            val res = snackbar.showSnackbar(t("Rule for {name} removed", "name" to ruleTitle(r.merchantKey)), actionLabel = t("Undo"), duration = SnackbarDuration.Short)
            if (res == SnackbarResult.ActionPerformed) vm.restore(r)
        }
    }

    MoreScaffold(t("Rules"), onBack, snackbar = snackbar) { inner ->
        val list = rules ?: return@MoreScaffold
        val mine = list.filter { it.manual }
        val learned = list.filterNot { it.manual }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = listPadding(inner, bottomExtra = 96.dp), verticalArrangement = Arrangement.spacedBy(CardGap)) {
                item("intro") {
                    Text(
                        t("Rules file a merchant's payments for you. Yours come first and override automatic categories; DhanKosh also learns one whenever you change a transaction's category."),
                        style = MaterialTheme.typography.bodyMedium, color = Hx.text2, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                if (list.isEmpty()) {
                    item("empty") {
                        HCard {
                            Text(t("No rules yet"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                t("Tap Add rule to file a merchant, or anything containing a word, under a category of your choice."),
                                fontSize = 13.sp, color = Hx.text2,
                            )
                        }
                    }
                }
                if (mine.isNotEmpty()) {
                    item("mine") {
                        RuleCard(t("Your rules · {n}", "n" to mine.size), mine, custom, onOpen = { editing = RuleDraft(it) }, onDelete = ::remove)
                    }
                }
                if (learned.isNotEmpty()) {
                    item("learned") {
                        RuleCard(t("Learned · {n}", "n" to learned.size), learned, custom, onOpen = { editing = RuleDraft(it) }, onDelete = ::remove)
                    }
                }
                if (list.isNotEmpty()) {
                    item("foot") {
                        Text(
                            t("Tap a rule to edit it. Removing a rule leaves past transactions as they are."),
                            fontSize = 12.sp, color = Hx.text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { editing = RuleDraft(null) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text(t("Add rule")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = inner.calculateBottomPadding() + 16.dp),
            )
        }
    }

    editing?.let { draft ->
        RuleEditor(
            original = draft.original,
            existingKeys = rules.orEmpty().map { it.merchantKey }.toSet(),
            vm = vm, cats = cats,
            onDismiss = { editing = null },
            onSaved = { key, changed ->
                editing = null
                scope.launch {
                    val tail = if (changed > 0) " · " + (if (changed == 1) t("{n} transaction updated", "n" to changed) else t("{n} transactions updated", "n" to changed)) else ""
                    snackbar.showSnackbar(t("Rule for {name} saved", "name" to ruleTitle(key)) + tail, duration = SnackbarDuration.Short)
                }
            },
            onDelete = { r -> editing = null; remove(r) },
        )
    }
}

@Composable
private fun RuleCard(
    title: String, rules: List<MerchantRuleEntity>, custom: List<CustomCategoryEntity>,
    onOpen: (MerchantRuleEntity) -> Unit, onDelete: (MerchantRuleEntity) -> Unit,
) {
    HCard(title = title, padding = 12.dp) {
        Column {
            rules.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = Hx.border)
                RuleRow(r, custom, onClick = { onOpen(r) }, onDelete = { onDelete(r) })
            }
        }
    }
}

@Composable
private fun RuleRow(r: MerchantRuleEntity, custom: List<CustomCategoryEntity>, onClick: () -> Unit, onDelete: () -> Unit) {
    val look = lookOf(r.category, r.customCategoryId, custom)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(look.icon, look.color, 36)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (r.matchType == RuleMatch.CONTAINS) "“${r.merchantKey}”" else ruleTitle(r.merchantKey),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                if (r.manual) Tag(t("Yours"), Hx.accent) else Tag(t("Learned"), Hx.text2)
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${t(r.matchType.label)} → ", fontSize = 12.sp, color = Hx.text2, maxLines = 1)
                Text(
                    t(look.name) + (r.subcategory?.let { " › ${t(it)}" } ?: ""),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = look.color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, t("Remove rule"), tint = Hx.text2, modifier = Modifier.size(18.dp)) }
    }
}

/** Writes or edits a rule: what to match, the category and sub-category, and whether to re-file the past. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(
    original: MerchantRuleEntity?,
    existingKeys: Set<String>,
    vm: RulesViewModel,
    cats: CategoriesViewModel,
    onDismiss: () -> Unit,
    onSaved: (key: String, changed: Int) -> Unit,
    onDelete: (MerchantRuleEntity) -> Unit,
) {
    val custom by cats.custom.collectAsStateWithLifecycle()
    val subs by cats.subs.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(original?.merchantKey.orEmpty()) }
    var match by remember { mutableStateOf(original?.matchType ?: RuleMatch.EXACT) }
    var category by remember { mutableStateOf(original?.category ?: Category.FOOD) }
    var customId by remember { mutableStateOf(original?.customCategoryId) }
    var sub by remember { mutableStateOf(original?.subcategory) }
    var applyPast by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf(false) }
    var newSub by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val key = text.trim().lowercase()
    val valid = key.length >= RulesViewModel.MIN_KEY
    val count by remember(key, match) { vm.matchCount(key, match) }.collectAsStateWithLifecycle(0)
    val own = customId?.let { id -> custom.firstOrNull { it.id == id } }
    val look = lookOf(category, own?.id, custom)
    val builtIn = if (own == null) category else null
    val choices = subsFor(look.key, builtIn, subs)
    val replaces = valid && key != original?.merchantKey && key in existingKeys

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (original == null) t("New rule") else t("Edit rule"), style = MaterialTheme.typography.titleLarge)

            SectionLabel(t("When merchant or UPI id"))
            Segmented(RuleMatch.entries.map { t(it.label) }, RuleMatch.entries.indexOf(match), onSelect = { match = RuleMatch.entries[it] })
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(if (match == RuleMatch.CONTAINS) t("e.g. swiggy") else t("e.g. Swiggy or swiggy@icici")) },
                supportingText = when {
                    replaces -> ({ Text(t("Replaces the existing rule for {name}", "name" to ruleTitle(key))) })
                    match == RuleMatch.CONTAINS -> ({ Text(t("Matches any merchant or UPI id containing this text")) })
                    else -> null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(14.dp),
            )

            SectionLabel(t("Set category"))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { picking = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(look.icon, look.color, 40)
                Text(t(look.name), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Text(t("Change"), style = MaterialTheme.typography.labelLarge, color = Hx.accent)
            }

            SectionLabel(t("Sub-category (optional)"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(t("Automatic"), on = sub == null) { sub = null }
                choices.forEach { s -> Pill(t(s.name), on = s.name == sub, leading = IconLibrary.get(s.icon)) { sub = s.name } }
                Pill(t("New"), leading = Icons.Filled.Add) { newSub = true }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = valid) { applyPast = !applyPast },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = applyPast && valid, onCheckedChange = { applyPast = it }, enabled = valid)
                Column(Modifier.weight(1f)) {
                    Text(t("Also apply to past transactions ({n})", "n" to count), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (count == 0) t("No past transactions match yet") else if (count == 1) t("This will update {n} transaction", "n" to count) else t("This will update {n} transactions", "n" to count),
                        fontSize = 12.sp, color = Hx.text2,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (original != null) TextButton(onClick = { onDelete(original) }) { Text(t("Delete"), color = Hx.neg) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(t("Cancel")) }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = valid && !saving,
                    onClick = {
                        saving = true
                        val rule = MerchantRuleEntity(
                            merchantKey = key, category = if (own != null) Category.OTHER else category, updatedAt = System.currentTimeMillis(),
                            matchType = match, subcategory = sub, customCategoryId = own?.id, manual = true,
                        )
                        vm.save(rule, original?.merchantKey, applyPast && count > 0) { changed -> onSaved(key, changed) }
                    },
                ) { Text(t("Save")) }
            }
        }
    }

    if (picking) {
        CategorySheet(
            current = if (own == null) category else null,
            onPick = { c -> if (c != category || own != null) sub = null; category = c; customId = null },
            onDismiss = { picking = false },
            title = t("File under"),
            custom = custom, currentCustomId = own?.id,
            onPickCustom = { c -> if (c.id != own?.id) sub = null; customId = c.id; category = Category.OTHER },
            onCreateCustom = { name, icon, color -> cats.createCategory(name, icon, color) { id -> customId = id; category = Category.OTHER; sub = null } },
        )
    }
    if (newSub) {
        NewItemDialog(t("New sub-category in {name}", "name" to t(look.name)), withColor = false, onDismiss = { newSub = false }) { name, icon, _ ->
            cats.createSub(look.key, name, icon)
            sub = name.trim()
            newSub = false
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Hx.text2)
}
