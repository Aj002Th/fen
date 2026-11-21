package app.fen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.fen.data.Category
import app.fen.data.Snapshot
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.ui.components.CircleAction
import app.fen.ui.components.EmptyState
import app.fen.ui.components.IconBadge
import app.fen.ui.components.RowDivider
import app.fen.ui.components.SearchField
import app.fen.ui.components.SectionLabel
import app.fen.ui.icons.categoryIcon
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney
import app.fen.util.label
import app.fen.util.timeLabel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private enum class TypeFilter(val label: String) {
    ALL("全部"), EXPENSE("支出"), INCOME("收入")
}

private enum class TimeFilter(val label: String) {
    ALL("全部时间"), THIS_MONTH("本月"), LAST_30("近 30 天")
}

private enum class AmountFilter(val label: String) {
    ANY("不限"), LT100("100 以下"), M100_500("100–500"), GT500("500 以上")
}

private enum class SortFilter(val label: String) {
    TIME("时间"), AMOUNT("金额")
}

private enum class FilterKind { TIME, AMOUNT, SORT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    snapshot: Snapshot,
    history: List<String>,
    onSaveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    var timeFilter by remember { mutableStateOf(TimeFilter.ALL) }
    var amountFilter by remember { mutableStateOf(AmountFilter.ANY) }
    var sortFilter by remember { mutableStateOf(SortFilter.TIME) }
    var openFilter by remember { mutableStateOf<FilterKind?>(null) }

    val categories = remember(snapshot.categories) { snapshot.categories.associateBy { it.id } }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val latestQuery by rememberUpdatedState(query)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // 关键字清空时回到初始状态（类型/时间/金额/排序全部复位）
    LaunchedEffect(query) {
        if (query.isBlank()) {
            typeFilter = TypeFilter.ALL
            timeFilter = TimeFilter.ALL
            amountFilter = AmountFilter.ANY
            sortFilter = SortFilter.TIME
        }
    }

    // 离开搜索页时把最后一次输入记入历史
    DisposableEffect(Unit) {
        onDispose {
            if (latestQuery.isNotBlank()) onSaveHistory(latestQuery)
        }
    }

    val results = remember(snapshot.transactions, query, typeFilter, timeFilter, amountFilter, sortFilter) {
        val now = System.currentTimeMillis()
        val startOfMonth = YearMonth.now().atDay(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val q = query.trim().lowercase()

        snapshot.transactions
            .asSequence()
            .filter { tx ->
                when (typeFilter) {
                    TypeFilter.ALL -> true
                    TypeFilter.EXPENSE -> tx.type == TxType.EXPENSE
                    TypeFilter.INCOME -> tx.type == TxType.INCOME
                }
            }
            .filter { tx ->
                when (timeFilter) {
                    TimeFilter.ALL -> true
                    TimeFilter.THIS_MONTH -> tx.occurredAt >= startOfMonth
                    TimeFilter.LAST_30 -> tx.occurredAt >= now - 30L * 24 * 60 * 60 * 1000
                }
            }
            .filter { tx ->
                when (amountFilter) {
                    AmountFilter.ANY -> true
                    AmountFilter.LT100 -> tx.amount < 10_000
                    AmountFilter.M100_500 -> tx.amount in 10_000..50_000
                    AmountFilter.GT500 -> tx.amount > 50_000
                }
            }
            .filter { tx ->
                if (q.isEmpty()) {
                    true
                } else {
                    val categoryName = tx.categoryId?.let { categories[it] }?.name.orEmpty()
                    tx.note.lowercase().contains(q) ||
                        categoryName.lowercase().contains(q) ||
                        formatMoney(tx.amount).contains(q)
                }
            }
            .toList()
            .let { list ->
                if (sortFilter == SortFilter.AMOUNT) list.sortedByDescending { it.amount }
                else list.sortedByDescending { it.occurredAt }
            }
    }

    val expense = results.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val income = results.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val showSuggestions = query.isBlank()

    fun applyKeyword(text: String) {
        query = text
        onSaveHistory(text)
        focusManager.clearFocus()
        keyboard?.hide()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                tint = Fen.colors.ink,
            )
            Spacer(Modifier.width(10.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                focusRequester = focusRequester,
                imeAction = ImeAction.Search,
                onSearch = {
                    if (query.isNotBlank()) onSaveHistory(query.trim())
                    focusManager.clearFocus()
                    keyboard?.hide()
                },
                modifier = Modifier.weight(1f),
                placeholder = "输入关键词",
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TypeFilter.entries.forEach { filter ->
                FilterPill(
                    label = filter.label,
                    selected = typeFilter == filter,
                    onClick = { typeFilter = filter },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                icon = Icons.Outlined.CalendarToday,
                label = "时间",
                value = timeFilter.label,
                active = timeFilter != TimeFilter.ALL,
                expanded = openFilter == FilterKind.TIME,
                onClick = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    openFilter = if (openFilter == FilterKind.TIME) null else FilterKind.TIME
                },
            )
            FilterChip(
                icon = Icons.Outlined.Payments,
                label = "金额",
                value = amountFilter.label,
                active = amountFilter != AmountFilter.ANY,
                expanded = openFilter == FilterKind.AMOUNT,
                onClick = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    openFilter = if (openFilter == FilterKind.AMOUNT) null else FilterKind.AMOUNT
                },
            )
            FilterChip(
                icon = Icons.Outlined.Sort,
                label = "排序",
                value = sortFilter.label,
                active = sortFilter != SortFilter.TIME,
                expanded = openFilter == FilterKind.SORT,
                onClick = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    openFilter = if (openFilter == FilterKind.SORT) null else FilterKind.SORT
                },
            )
        }

        AnimatedVisibility(visible = openFilter != null) {
            val kind = openFilter
            if (kind != null) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (kind) {
                        FilterKind.TIME -> TimeFilter.entries.forEach { option ->
                            OptionPill(
                                label = option.label,
                                selected = timeFilter == option,
                                onClick = {
                                    timeFilter = option
                                    openFilter = null
                                },
                            )
                        }
                        FilterKind.AMOUNT -> AmountFilter.entries.forEach { option ->
                            OptionPill(
                                label = option.label,
                                selected = amountFilter == option,
                                onClick = {
                                    amountFilter = option
                                    openFilter = null
                                },
                            )
                        }
                        FilterKind.SORT -> SortFilter.entries.forEach { option ->
                            OptionPill(
                                label = option.label,
                                selected = sortFilter == option,
                                onClick = {
                                    sortFilter = option
                                    openFilter = null
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (!showSuggestions) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "共 ${results.size} 笔",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkSoft,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "支出 ${formatMoney(expense)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.expense,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "收入 ${formatMoney(income)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.income,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (showSuggestions) {
            SuggestionSection(
                snapshot = snapshot,
                history = history,
                onPick = { applyKeyword(it) },
                onClearHistory = onClearHistory,
            )
        } else if (results.isEmpty()) {
            EmptyState(
                title = "没有找到相关记录",
                subtitle = "换个关键词或放宽筛选条件试试",
            )
        } else if (sortFilter == SortFilter.AMOUNT) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            ) {
                item { SectionLabel("按金额排序", modifier = Modifier.padding(bottom = 6.dp)) }
                items(count = results.size, key = { results[it].id }) { index ->
                    val tx = results[index]
                    SearchRow(
                        tx = tx,
                        category = tx.categoryId?.let { categories[it] },
                        subtitle = "${tx.occurredAt.toDate().label()} ${tx.occurredAt.timeLabel()}",
                    )
                }
            }
        } else {
            val groups = results.groupBy { it.occurredAt.toDate() }
                .toSortedMap(compareByDescending { it })
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            ) {
                groups.forEach { (date, items) ->
                    item(key = "day-$date") {
                        DayHeader(date = date, items = items)
                    }
                    item(key = "card-$date") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Fen.colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                items.forEachIndexed { index, tx ->
                                    SearchRow(
                                        tx = tx,
                                        category = tx.categoryId?.let { categories[it] },
                                        subtitle = tx.note.ifBlank { tx.occurredAt.timeLabel() },
                                    )
                                    if (index != items.lastIndex) RowDivider(startInset = 70.dp)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionSection(
    snapshot: Snapshot,
    history: List<String>,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val top = remember(snapshot.transactions, snapshot.categories) {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        snapshot.transactions
            .filter { it.categoryId != null && it.occurredAt >= cutoff }
            .groupBy { it.categoryId!! }
            .entries
            .sortedByDescending { it.value.size }
            .take(8)
            .mapNotNull { entry -> snapshot.categories.firstOrNull { it.id == entry.key } }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (history.isNotEmpty()) {
            SectionLabel(
                text = "搜索历史",
                modifier = Modifier.padding(bottom = 10.dp),
                trailing = {
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelMedium,
                        color = Fen.colors.inkSoft,
                        modifier = Modifier.clickable(onClick = onClearHistory),
                    )
                },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { keyword ->
                    Surface(
                        onClick = { onPick(keyword) },
                        shape = RoundedCornerShape(12.dp),
                        color = Fen.colors.surface,
                    ) {
                        Text(
                            text = keyword,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Fen.colors.inkSoft,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        SectionLabel("搜索建议", modifier = Modifier.padding(bottom = 10.dp))
        if (top.isEmpty()) {
            Text(
                "记几笔账之后，这里会出现常用分类",
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                top.forEach { category ->
                    Surface(
                        onClick = { onPick(category.name) },
                        shape = RoundedCornerShape(12.dp),
                        color = Fen.colors.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = categoryIcon(category.icon),
                                contentDescription = null,
                                tint = Color(category.color),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(category.name, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "支持搜索备注、分类名称和金额",
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkFaint,
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, items: List<Tx>) {
    val expense = items.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val income = items.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date.label(),
            style = MaterialTheme.typography.labelLarge,
            color = Fen.colors.inkSoft,
        )
        Spacer(Modifier.weight(1f))
        if (expense > 0) {
            Text(
                text = "支出 ${formatMoney(expense)}",
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
            )
        }
        if (expense > 0 && income > 0) {
            Text(
                text = "  ·  ",
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkFaint,
            )
        }
        if (income > 0) {
            Text(
                text = "收入 ${formatMoney(income)}",
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
            )
        }
    }
}

@Composable
private fun SearchRow(
    tx: Tx,
    category: Category?,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = categoryIcon(category?.icon),
            color = Color(category?.color ?: 0xFF7C8794),
            size = 40.dp,
            iconSize = 20.dp,
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = category?.name ?: "未分类",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
                maxLines = 1,
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = when (tx.type) {
                TxType.EXPENSE -> "-${formatMoney(tx.amount)}"
                TxType.INCOME -> "+${formatMoney(tx.amount)}"
            },
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = when (tx.type) {
                TxType.EXPENSE -> Fen.colors.expense
                TxType.INCOME -> Fen.colors.income
            },
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Fen.colors.ink else Fen.colors.surface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                if (Fen.colors.isDark) Color(0xFF15171B) else Color.White
            } else {
                Fen.colors.inkSoft
            },
        )
    }
}

@Composable
private fun FilterChip(
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (expanded || active) Fen.colors.surfaceMuted else Fen.colors.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active || expanded) Fen.colors.inkSoft else Fen.colors.inkFaint,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkFaint,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active || expanded) Fen.colors.ink else Fen.colors.inkSoft,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Fen.colors.inkFaint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun OptionPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Fen.colors.ink else Fen.colors.surface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                if (Fen.colors.isDark) Color(0xFF15171B) else Color.White
            } else {
                Fen.colors.inkSoft
            },
        )
    }
}

private fun Long.toDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

