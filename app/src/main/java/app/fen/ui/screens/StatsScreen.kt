package app.fen.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.fen.data.CategoryStat
import app.fen.data.Snapshot
import app.fen.data.StatsBoardConfig
import app.fen.data.StatsBundle
import app.fen.data.StatsMode
import app.fen.data.StatsSection
import app.fen.data.TxType
import app.fen.data.buildStats
import app.fen.data.statsPeriod
import app.fen.data.toDate
import app.fen.ui.components.AmountText
import app.fen.ui.components.CategoryDonut
import app.fen.ui.components.CircleAction
import app.fen.ui.components.CumulativeChart
import app.fen.ui.components.DonutEntry
import app.fen.ui.components.DonutLegend
import app.fen.ui.components.DualBarChart
import app.fen.ui.components.MetricTile
import app.fen.ui.components.PageHeader
import app.fen.ui.components.Segmented
import app.fen.ui.components.StatsCard
import app.fen.ui.components.StatsRangePickerSheet
import app.fen.ui.components.SummaryTable
import app.fen.ui.components.WeekdayBars
import app.fen.ui.theme.Fen
import app.fen.ui.theme.FenMotion
import app.fen.util.formatMoney
import app.fen.util.formatSignedMoney
import app.fen.util.shortDate
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    snapshot: Snapshot,
    statsMode: StatsMode,
    statsAnchor: LocalDate,
    customRange: Pair<LocalDate, LocalDate>,
    categoryType: TxType,
    boardConfig: StatsBoardConfig,
    onSetMode: (StatsMode) -> Unit,
    onShift: (Long) -> Unit,
    onSetCustomRange: (LocalDate, LocalDate) -> Unit,
    onSetCategoryType: (TxType) -> Unit,
    shouldPromptForRange: () -> Boolean,
    onTouchRange: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showRangePicker by remember { mutableStateOf(false) }
    var selectedTrendIndex by remember(statsMode, statsAnchor, customRange) { mutableStateOf<Int?>(null) }
    var selectedBalanceIndex by remember(statsMode, statsAnchor, customRange) { mutableStateOf<Int?>(null) }
    var selectedCategoryKey by remember(statsMode, statsAnchor, customRange, categoryType) {
        mutableStateOf<String?>(null)
    }
    var showAllRanking by remember(statsMode, statsAnchor, customRange, categoryType) {
        mutableStateOf(false)
    }
    var expandedRankingKeys by remember(statsMode, statsAnchor, customRange, categoryType) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var periodDirection by remember { mutableStateOf(1) }

    val today = remember { LocalDate.now() }
    val earliest = remember(snapshot.transactions) {
        snapshot.transactions.minOfOrNull { it.occurredAt.toDate() }
    }
    val period = remember(statsMode, statsAnchor, customRange, earliest, today) {
        statsPeriod(
            mode = statsMode,
            anchor = statsAnchor,
            customStart = customRange.first,
            customEnd = customRange.second,
            today = today,
            earliest = earliest,
        )
    }
    val stats = remember(period, snapshot, boardConfig.summaryShowEmpty) {
        buildStats(
            txs = snapshot.transactions,
            categories = snapshot.categories,
            period = period,
            summaryShowEmpty = boardConfig.summaryShowEmpty,
        )
    }

    val categoryStats = if (categoryType == TxType.EXPENSE) stats.expenseCategories else stats.incomeCategories
    val typeTotal = if (categoryType == TxType.EXPENSE) stats.totals.expense else stats.totals.income
    val typeLabel = if (categoryType == TxType.EXPENSE) "支出" else "收入"

    val previousTotals = stats.previousTotals
    val sections = boardConfig.visibleSections.filterNot { section ->
        section == StatsSection.COMPARISON && (
            previousTotals == null ||
                (stats.totals.count == 0 && previousTotals.count == 0)
            )
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "统计",
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            item {
                Segmented(
                    options = listOf("周", "月", "年", "全部", "范围"),
                    selected = statsMode.ordinal,
                    onSelect = { index ->
                        val mode = StatsMode.entries[index]
                        periodDirection = if (index >= statsMode.ordinal) 1 else -1
                        if (mode == StatsMode.RANGE && statsMode != StatsMode.RANGE) {
                            if (shouldPromptForRange()) {
                                showRangePicker = true
                            } else {
                                onTouchRange()
                            }
                        }
                        onSetMode(mode)
                    },
                )
                Spacer(Modifier.height(10.dp))
                PeriodBar(
                    label = periodLabel(period.start, period.end, statsMode),
                    showArrows = statsMode == StatsMode.WEEK ||
                        statsMode == StatsMode.MONTH ||
                        statsMode == StatsMode.YEAR,
                    canNext = period.end < today,
                    direction = periodDirection,
                    onPrev = {
                        periodDirection = -1
                        onShift(-1)
                    },
                    onNext = {
                        periodDirection = 1
                        onShift(1)
                    },
                    onClick = if (statsMode == StatsMode.RANGE) {
                        { showRangePicker = true }
                    } else {
                        null
                    },
                )
            }

            if (sections.isEmpty()) {
                item {
                    StatsCard("统计看板") {
                        Text(
                            text = "看板已全部隐藏",
                            style = MaterialTheme.typography.titleMedium,
                            color = Fen.colors.ink,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "到 设置 → 统计看板 里打开需要的卡片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fen.colors.inkSoft,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "打开设置",
                            style = MaterialTheme.typography.labelLarge,
                            color = Fen.colors.link,
                            modifier = Modifier.clickable(onClick = onOpenSettings).padding(vertical = 4.dp),
                        )
                    }
                }
            }

            sections.forEach { section ->
                item(key = section.key) {
                    when (section) {
                        StatsSection.OVERVIEW -> OverviewCard(stats)
                        StatsSection.CATEGORY_MIX -> CategoryMixCard(
                            stats = stats,
                            categoryType = categoryType,
                            typeLabel = typeLabel,
                            typeTotal = typeTotal,
                            categoryStats = categoryStats,
                            selectedKey = selectedCategoryKey,
                            onSelect = { selectedCategoryKey = it },
                            onSetCategoryType = onSetCategoryType,
                        )
                        StatsSection.CATEGORY_RANKING -> CategoryRankingCard(
                            categoryType = categoryType,
                            typeLabel = typeLabel,
                            typeTotal = typeTotal,
                            categoryStats = categoryStats,
                            rankingLimit = boardConfig.rankingLimit,
                            showAll = showAllRanking,
                            onShowAll = { showAllRanking = true },
                            expandedKeys = expandedRankingKeys,
                            onToggleExpand = { key ->
                                expandedRankingKeys = if (key in expandedRankingKeys) {
                                    expandedRankingKeys - key
                                } else {
                                    expandedRankingKeys + key
                                }
                            },
                            onSetCategoryType = onSetCategoryType,
                        )
                        StatsSection.TREND -> TrendCard(
                            stats = stats,
                            selectedIndex = selectedTrendIndex,
                            onSelect = { selectedTrendIndex = it },
                        )
                        StatsSection.BALANCE -> BalanceCard(
                            stats = stats,
                            selectedIndex = selectedBalanceIndex,
                            onSelect = { selectedBalanceIndex = it },
                        )
                        StatsSection.COMPARISON -> ComparisonCard(stats)
                        StatsSection.WEEKDAY -> WeekdayCard(stats)
                        StatsSection.SUMMARY -> SummaryCard(stats, boardConfig.summaryShowEmpty)
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showRangePicker) {
        StatsRangePickerSheet(
            initialStart = customRange.first,
            initialEnd = customRange.second,
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                onSetCustomRange(start, end)
                showRangePicker = false
            },
        )
    }
}

private fun periodLabel(start: LocalDate, end: LocalDate, mode: StatsMode): String = when (mode) {
    StatsMode.MONTH -> "${start.year}年${start.monthValue}月"
    StatsMode.YEAR -> "${start.year}年"
    StatsMode.WEEK, StatsMode.ALL, StatsMode.RANGE -> rangeLabel(start, end)
}

private fun rangeLabel(start: LocalDate, end: LocalDate): String {
    val sameYear = start.year == end.year
    val startText = if (sameYear) {
        "${start.monthValue}月${start.dayOfMonth}日"
    } else {
        "${start.year}年${start.monthValue}月${start.dayOfMonth}日"
    }
    val endText = if (sameYear) {
        "${end.monthValue}月${end.dayOfMonth}日"
    } else {
        "${end.year}年${end.monthValue}月${end.dayOfMonth}日"
    }
    return "$startText – $endText"
}

@Composable
private fun PeriodBar(
    label: String,
    showArrows: Boolean,
    canNext: Boolean,
    direction: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArrows) {
            CircleAction(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "上一段",
                onClick = onPrev,
                tint = Fen.colors.ink,
            )
        } else {
            Spacer(Modifier.width(40.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    val spec = tween<IntOffset>(FenMotion.durationMillis)
                    if (direction > 0) {
                        slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it }
                    } else {
                        slideInHorizontally(spec) { -it } togetherWith slideOutHorizontally(spec) { it }
                    }
                },
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
                label = "period",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    color = Fen.colors.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) {
                Spacer(Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = "选择日期范围",
                    tint = Fen.colors.inkSoft,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (showArrows) {
            CircleAction(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "下一段",
                onClick = { if (canNext) onNext() },
                tint = if (canNext) Fen.colors.ink else Fen.colors.inkFaint,
            )
        } else {
            Spacer(Modifier.width(40.dp))
        }
    }
}

/* --------------------------------- 总览 --------------------------------- */

@Composable
private fun OverviewCard(stats: StatsBundle) {
    StatsCard("总览") {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkSoft,
                )
                AmountText(
                    amount = stats.totals.expense,
                    style = MaterialTheme.typography.displaySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "收入",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkSoft,
                )
                Text(
                    text = formatMoney(stats.totals.income),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = Fen.colors.income,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "结余",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkSoft,
                )
                Text(
                    text = formatSignedMoney(stats.totals.net),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = netColor(stats.totals.net),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Fen.colors.divider))
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = stats.averageLabel,
                value = "¥${formatMoney(stats.averageExpense)}",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "记账笔数",
                value = "${stats.totals.count} 笔",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "平均每笔支出",
                value = if (stats.totals.expenseCount > 0) {
                    "¥${formatMoney(stats.totals.expense / stats.totals.expenseCount)}"
                } else {
                    "—"
                },
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "最高单日支出",
                value = if (stats.maxExpenseDayDate != null) {
                    "¥${formatMoney(stats.maxExpenseDayAmount)}"
                } else {
                    "—"
                },
                sub = stats.maxExpenseDayDate?.let { day ->
                    if (stats.period.start.year != stats.period.end.year) {
                        "${day.year}年${day.monthValue}月${day.dayOfMonth}日"
                    } else {
                        day.shortDate()
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/* -------------------------------- 分类占比 ------------------------------- */

@Composable
private fun CategoryMixCard(
    stats: StatsBundle,
    categoryType: TxType,
    typeLabel: String,
    typeTotal: Long,
    categoryStats: List<CategoryStat>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onSetCategoryType: (TxType) -> Unit,
) {
    StatsCard(
        title = "分类占比",
        trailing = { TypeToggle(categoryType, onSetCategoryType) },
    ) {
        if (categoryStats.isEmpty() || typeTotal <= 0) {
            Text(
                text = "这个区间还没有${typeLabel}记录",
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
            )
        } else {
            val entries = donutEntries(categoryStats)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDonut(
                    entries = entries,
                    total = typeTotal,
                    categoryCount = categoryStats.size,
                    selectedKey = selectedKey,
                    onSelect = onSelect,
                    centerLabel = "总$typeLabel",
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    DonutLegend(
                        entries = entries,
                        total = typeTotal,
                        selectedKey = selectedKey,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

private fun donutEntries(categoryStats: List<CategoryStat>): List<DonutEntry> {
    val top = categoryStats.take(6).map { stat ->
        DonutEntry(
            key = stat.category.id,
            label = stat.category.name,
            amount = stat.amount,
            color = Color(stat.category.color),
        )
    }
    val rest = categoryStats.drop(6)
    if (rest.isEmpty()) return top
    return top + DonutEntry(
        key = "__other__",
        label = "其他 ${rest.size} 类",
        amount = rest.sumOf { it.amount },
        color = Color(0xFFB7BCC5),
    )
}

/* -------------------------------- 分类排行 ------------------------------- */

@Composable
private fun CategoryRankingCard(
    categoryType: TxType,
    typeLabel: String,
    typeTotal: Long,
    categoryStats: List<CategoryStat>,
    rankingLimit: Int,
    showAll: Boolean,
    onShowAll: () -> Unit,
    expandedKeys: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSetCategoryType: (TxType) -> Unit,
) {
    StatsCard(
        title = "分类排行",
        trailing = { TypeToggle(categoryType, onSetCategoryType) },
    ) {
        if (categoryStats.isEmpty() || typeTotal <= 0) {
            Text(
                text = "这个区间还没有${typeLabel}记录",
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
            )
        } else {
            val limit = if (rankingLimit <= 0) categoryStats.size else rankingLimit
            val visible = if (showAll) categoryStats else categoryStats.take(limit)
            visible.forEach { stat ->
                RankingRow(
                    stat = stat,
                    total = typeTotal,
                    expanded = stat.category.id in expandedKeys,
                    onToggleExpand = { onToggleExpand(stat.category.id) },
                )
            }
            if (!showAll && limit < categoryStats.size) {
                Text(
                    text = "查看全部 ${categoryStats.size} 类",
                    style = MaterialTheme.typography.labelLarge,
                    color = Fen.colors.link,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShowAll)
                        .padding(top = 10.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun RankingRow(
    stat: CategoryStat,
    total: Long,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val color = Color(stat.category.color)
    val share = if (total > 0) stat.amount.toFloat() / total else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stat.category.name,
                style = MaterialTheme.typography.titleMedium,
                color = Fen.colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMoney(stat.amount),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = Fen.colors.ink,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Fen.colors.surfaceMuted),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(share.coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (share > 0f && share < 0.01f) {
                    "<1%"
                } else {
                    "${(share * 100).roundToInt()}%"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = Fen.colors.inkSoft,
                maxLines = 1,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append("${stat.count} 笔 · 平均 ${formatMoney(stat.average)}")
                    val ratio = stat.deltaRatio
                    when {
                        stat.previousAmount == 0L -> append(" · 上期无记录")
                        ratio != null -> append(
                            " · 较上期 ${formatSignedMoney(stat.delta)} " +
                                String.format(Locale.CHINA, "(%+.1f%%)", ratio * 100)
                        )
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
            )
        }
    }
}

/* -------------------------------- 收支趋势 ------------------------------- */

@Composable
private fun TrendCard(stats: StatsBundle, selectedIndex: Int?, onSelect: (Int?) -> Unit) {
    StatsCard(
        title = "收支趋势",
        trailing = {
            LegendDot(Fen.colors.expense, "支出")
            LegendDot(Fen.colors.income, "收入")
        },
    ) {
        val selected = selectedIndex?.let { stats.trendBuckets.getOrNull(it) }
        Text(
            text = if (selected != null) {
                "${selected.tableLabel} · 支出 ¥${formatMoney(selected.expense)} · 收入 ¥${formatMoney(selected.income)}"
            } else {
                "点按柱子查看明细"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (selected != null) Fen.colors.ink else Fen.colors.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        DualBarChart(stats.trendBuckets, selectedIndex, onSelect)
    }
}

/* -------------------------------- 累计结余 ------------------------------- */

@Composable
private fun BalanceCard(stats: StatsBundle, selectedIndex: Int?, onSelect: (Int?) -> Unit) {
    StatsCard(
        title = "累计结余",
        trailing = {
            Text(
                text = "期末 ${formatSignedMoney(stats.cumulative.lastOrNull() ?: 0L)}",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = netColor(stats.cumulative.lastOrNull() ?: 0L),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        val detail = if (selectedIndex != null && selectedIndex > 0) {
            val bucket = stats.trendBuckets.getOrNull(selectedIndex - 1)
            val value = stats.cumulative.getOrNull(selectedIndex) ?: 0L
            "${bucket?.tableLabel ?: "区间起点"} · 累计 ${formatSignedMoney(value)}"
        } else {
            "点按曲线查看累计值 · 起点为 0"
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        CumulativeChart(stats.trendBuckets, stats.cumulative, selectedIndex, onSelect)
    }
}

/* -------------------------------- 收支对比 ------------------------------- */

@Composable
private fun ComparisonCard(stats: StatsBundle) {
    val previous = stats.previousTotals ?: return
    StatsCard(
        title = "收支对比",
        trailing = {
            Text(
                text = "较上期",
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
            )
        },
    ) {
        ComparisonRow(
            label = "支出",
            current = stats.totals.expense,
            previous = previous.expense,
            isExpense = true,
        )
        Spacer(Modifier.height(10.dp))
        ComparisonRow(
            label = "收入",
            current = stats.totals.income,
            previous = previous.income,
            isExpense = false,
        )
        Spacer(Modifier.height(10.dp))
        ComparisonRow(
            label = "结余",
            current = stats.totals.net,
            previous = previous.net,
            isExpense = false,
            signed = true,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "上期按同长度区间对比",
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkFaint,
        )
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    current: Long,
    previous: Long,
    isExpense: Boolean,
    signed: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Fen.colors.ink,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (signed) formatSignedMoney(current) else "¥${formatMoney(current)}",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = if (signed) netColor(current) else Fen.colors.ink,
                maxLines = 1,
            )
            Text(
                text = "上期 ${if (signed) formatSignedMoney(previous) else "¥${formatMoney(previous)}"}",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = Fen.colors.inkSoft,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        DeltaBadge(current = current, previous = previous, isExpense = isExpense)
    }
}

@Composable
private fun DeltaBadge(current: Long, previous: Long, isExpense: Boolean) {
    val text: String
    val color: Color
    when {
        previous == 0L && current == 0L -> {
            text = "—"
            color = Fen.colors.inkSoft
        }
        previous == 0L -> {
            text = "新增"
            color = when {
                isExpense -> Fen.colors.expense
                current >= 0 -> Fen.colors.income
                else -> Fen.colors.expense
            }
        }
        else -> {
            val delta = current - previous
            val percent = delta.toDouble() / previous * 100
            if (kotlin.math.abs(percent) < 0.05) {
                text = "持平"
                color = Fen.colors.inkSoft
            } else {
                text = if (!isExpense && previous < 0) {
                    formatSignedMoney(delta)
                } else {
                    String.format(Locale.CHINA, "%+.1f%%", percent)
                }
                color = when {
                    isExpense && delta > 0 -> Fen.colors.expense
                    isExpense && delta < 0 -> Fen.colors.income
                    !isExpense && delta > 0 -> Fen.colors.income
                    else -> Fen.colors.expense
                }
            }
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

/* -------------------------------- 星期分布 ------------------------------- */

@Composable
private fun WeekdayCard(stats: StatsBundle) {
    val maxIndex = stats.weekdayExpense.indices.maxByOrNull { stats.weekdayExpense[it] } ?: -1
    val maxValue = stats.weekdayExpense.getOrNull(maxIndex) ?: 0L
    val maxLabel = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrNull(maxIndex) ?: "—"
    StatsCard(
        title = "星期分布",
        trailing = {
            if (maxValue > 0) {
                Text(
                    text = "最高 $maxLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkSoft,
                )
            }
        },
    ) {
        WeekdayBars(stats.weekdayExpense, stats.weekdayCount)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (maxValue > 0) {
                "$maxLabel 支出 ¥${formatMoney(maxValue)}，占 ${(maxValue * 100.0 / stats.totals.expense.coerceAtLeast(1L)).roundToInt()}%"
            } else {
                "这个区间还没有支出记录"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkSoft,
        )
    }
}

/* -------------------------------- 账单汇总 ------------------------------- */

@Composable
private fun SummaryCard(stats: StatsBundle, showEmpty: Boolean) {
    StatsCard(
        title = "账单汇总",
        trailing = {
            Text(
                text = "${stats.totals.count} 笔",
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
            )
        },
    ) {
        SummaryTable(stats.summaryRows, stats.unit)
        val hidden = stats.buckets.count { it.count == 0 }
        val keepsEmpty = stats.unit == app.fen.data.BucketUnit.DAY && stats.period.days <= 7
        if (!showEmpty && !keepsEmpty && hidden > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "已隐藏 $hidden 个无记录${unitLabel(stats)}",
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkFaint,
            )
        }
    }
}

private fun unitLabel(stats: StatsBundle): String = when (stats.unit) {
    app.fen.data.BucketUnit.DAY -> "日"
    app.fen.data.BucketUnit.MONTH -> "月"
    app.fen.data.BucketUnit.YEAR -> "年"
}

/* --------------------------------- 小组件 -------------------------------- */

@Composable
private fun TypeToggle(type: TxType, onChange: (TxType) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Fen.colors.surfaceMuted)
            .padding(3.dp),
    ) {
        listOf("支出" to TxType.EXPENSE, "收入" to TxType.INCOME).forEach { (label, item) ->
            val active = type == item
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Fen.colors.surface else Color.Transparent)
                    .clickable { onChange(item) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) Fen.colors.ink else Fen.colors.inkSoft,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkSoft,
        )
    }
}

@Composable
private fun netColor(net: Long): Color = when {
    net > 0 -> Fen.colors.income
    net < 0 -> Fen.colors.expense
    else -> Fen.colors.inkSoft
}
