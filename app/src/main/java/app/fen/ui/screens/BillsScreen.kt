package app.fen.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fen.data.Snapshot
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.data.groupByDay
import app.fen.data.inMonth
import app.fen.data.totals
import app.fen.ui.components.AmountText
import app.fen.ui.components.CircleAction
import app.fen.ui.components.EmptyState
import app.fen.ui.components.IconBadge
import app.fen.ui.components.MonthBar
import app.fen.ui.components.PageHeader
import app.fen.ui.components.RowDivider
import app.fen.ui.components.SearchField
import app.fen.ui.components.SectionLabel
import app.fen.ui.icons.categoryIcon
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney
import app.fen.util.label
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillsScreen(
    snapshot: Snapshot,
    month: YearMonth,
    onShiftMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onDelete: (Tx) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Tx?>(null) }

    val categories = remember(snapshot.categories) { snapshot.categories.associateBy { it.id } }
    val accounts = remember(snapshot.accounts) { snapshot.accounts.associateBy { it.id } }

    val monthTxs = remember(snapshot.transactions, month) { snapshot.transactions.inMonth(month) }
    val visible = remember(monthTxs, query, categories) {
        if (query.isBlank()) {
            monthTxs
        } else {
            val q = query.trim().lowercase()
            monthTxs.filter { tx ->
                val category = tx.categoryId?.let { categories[it] }?.name.orEmpty()
                val account = accounts[tx.accountId]?.name.orEmpty()
                tx.note.lowercase().contains(q) ||
                    category.lowercase().contains(q) ||
                    account.lowercase().contains(q) ||
                    formatMoney(tx.amount).contains(q)
            }
        }
    }
    val totals = remember(visible) { visible.totals() }
    val groups = remember(visible) { visible.groupByDay() }
    val budget = snapshot.budgets[month.toString()]

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "明细",
            modifier = Modifier.padding(horizontal = 20.dp),
            actions = {
                CircleAction(
                    icon = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings,
                    tint = Fen.colors.ink,
                )
            },
        )

        SearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
        ) {
            item {
                MonthBar(
                    label = month.label(),
                    onPrev = { onShiftMonth(-1) },
                    onNext = { onShiftMonth(1) },
                    trailing = {
                        if (month != YearMonth.now()) {
                            Surface(
                                onClick = onToday,
                                shape = RoundedCornerShape(12.dp),
                                color = Fen.colors.surface,
                            ) {
                                Text(
                                    "今天",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Fen.colors.inkSoft,
                                )
                            }
                        }
                    },
                )
                Spacer(Modifier.height(14.dp))
                SummaryPager(totals.expense, totals.income, budget)
                Spacer(Modifier.height(22.dp))
                SectionLabel(
                    text = if (query.isBlank()) "明细" else "搜索结果",
                    trailing = {
                        Text(
                            text = "${visible.size} 笔",
                            style = MaterialTheme.typography.labelMedium,
                            color = Fen.colors.inkSoft,
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            if (groups.isEmpty()) {
                item {
                    EmptyState(
                        title = if (query.isBlank()) "这个月还没有记录" else "没有找到相关记录",
                        subtitle = if (query.isBlank()) "点右下角「+」记下第一笔" else "换个关键词试试",
                    )
                }
            }

            groups.forEach { group ->
                item(key = "day-${group.date}") {
                    DayHeader(group.date, group.totals.expense, group.totals.income)
                }
                item(key = "card-${group.date}") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Fen.colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            group.items.forEachIndexed { index, tx ->
                                val category = tx.categoryId?.let { categories[it] }
                                TransactionRow(
                                    tx = tx,
                                    categoryName = category?.name ?: "未分类",
                                    icon = categoryIcon(category?.icon),
                                    color = Color(category?.color ?: 0xFF7C8794),
                                    accountName = accounts[tx.accountId]?.name.orEmpty(),
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { pendingDelete = tx },
                                    ),
                                )
                                if (index != group.items.lastIndex) {
                                    RowDivider(startInset = 70.dp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    pendingDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔记录？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(tx)
                    pendingDelete = null
                }) { Text("删除", color = Fen.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SummaryPager(expense: Long, income: Long, budget: Long?) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            SummaryCard(page = page, expense = expense, income = income, budget = budget)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(if (pagerState.currentPage == index) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) Fen.colors.ink else Fen.colors.inkFaint
                        )
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(page: Int, expense: Long, income: Long, budget: Long?) {
    val net = income - expense
    val (label, value) = when (page) {
        0 -> "本月支出" to expense
        1 -> "本月收入" to income
        else -> "本月结余" to net
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Fen.colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Fen.colors.inkSoft,
            )
            Spacer(Modifier.height(2.dp))
            AmountText(
                amount = value,
                style = MaterialTheme.typography.displayLarge,
                color = Fen.colors.ink,
                prefix = "¥",
            )
            Spacer(Modifier.height(10.dp))
            when (page) {
                0 -> MetricRow(listOf("收入" to income, "结余" to net))
                1 -> MetricRow(listOf("支出" to expense, "结余" to net))
                else -> MetricRow(listOf("支出" to expense, "收入" to income))
            }
        }
    }
}

@Composable
private fun MetricRow(items: List<Pair<String, Long>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        items.forEach { (label, value) ->
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkSoft,
                )
                Text(
                    text = formatMoney(value),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = Fen.colors.ink,
                )
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, expense: Long, income: Long) {
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
private fun TransactionRow(
    tx: Tx,
    categoryName: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    accountName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, color = color, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = listOfNotNull(
                    tx.note.takeIf { it.isNotBlank() },
                    accountName.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
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
                TxType.TRANSFER -> formatMoney(tx.amount)
            },
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = when (tx.type) {
                TxType.EXPENSE -> Fen.colors.expense
                TxType.INCOME -> Fen.colors.income
                TxType.TRANSFER -> Fen.colors.ink
            },
        )
    }
}
