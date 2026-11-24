package app.fen.ui.screens

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fen.data.Category
import app.fen.data.Snapshot
import app.fen.data.TxType
import app.fen.data.exchange.ExportFilter
import app.fen.data.exchange.ExportScope
import app.fen.data.exchange.filterForExport
import app.fen.ui.components.CircleAction
import app.fen.ui.components.IconBadge
import app.fen.ui.components.SectionLabel
import app.fen.ui.components.StatsRangePickerSheet
import app.fen.ui.icons.categoryIcon
import app.fen.ui.theme.Fen
import app.fen.util.shortDate
import java.time.LocalDate
import java.time.YearMonth

private enum class TimePreset(val label: String) {
    ALL("全部"),
    THIS_MONTH("本月"),
    LAST_MONTH("上月"),
    LAST_30("近 30 天"),
    THIS_YEAR("今年"),
    CUSTOM("自定义"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    snapshot: Snapshot,
    onBack: () -> Unit,
    onShare: (Context, ExportFilter) -> Boolean,
) {
    val context = LocalContext.current
    var preset by remember { mutableStateOf(TimePreset.ALL) }
    var customFrom by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var customTo by remember { mutableStateOf(LocalDate.now()) }
    var showRange by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(ExportScope.ALL) }
    var allCategories by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf(setOf<String>()) }

    val (from, to) = remember(preset, customFrom, customTo) { preset.range(customFrom, customTo) }
    val visibleCategories = remember(snapshot.categories, scope) {
        snapshot.categories
            .filter {
                when (scope) {
                    ExportScope.ALL -> true
                    ExportScope.EXPENSE -> it.type == TxType.EXPENSE
                    ExportScope.INCOME -> it.type == TxType.INCOME
                }
            }
            .sortedWith(compareBy({ it.type }, { it.sortOrder }))
    }
    val categoryIds = if (allCategories) null else picked.intersect(visibleCategories.map { it.id }.toSet())
    val filter = ExportFilter(from = from, to = to, scope = scope, categoryIds = categoryIds)
    val count = remember(snapshot.transactions, filter) {
        snapshot.transactions.filterForExport(filter).size
    }

    fun toggleCategory(id: String) {
        if (allCategories) {
            allCategories = false
            picked = setOf(id)
        } else if (id in picked) {
            val next = picked - id
            if (next.isEmpty()) allCategories = true else picked = next
        } else {
            picked = picked + id
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleAction(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                        tint = Fen.colors.ink,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("导出", style = MaterialTheme.typography.headlineLarge)
                }
            }
            item { SectionLabel("时间", modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePreset.entries.forEach { option ->
                        val custom = option == TimePreset.CUSTOM && preset == TimePreset.CUSTOM
                        SelectChip(
                            label = if (custom) "自定义 · ${rangeLabel(customFrom, customTo)}" else option.label,
                            selected = preset == option,
                            onClick = {
                                if (option == TimePreset.CUSTOM) showRange = true else preset = option
                            },
                        )
                    }
                }
            }

            item { SectionLabel("收支", modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ExportScope.ALL to "全部",
                        ExportScope.EXPENSE to "支出",
                        ExportScope.INCOME to "收入",
                    ).forEach { (value, label) ->
                        SelectChip(
                            label = label,
                            selected = scope == value,
                            accent = when (value) {
                                ExportScope.EXPENSE -> Fen.colors.expense
                                ExportScope.INCOME -> Fen.colors.income
                                ExportScope.ALL -> Fen.colors.ink
                            },
                            onClick = { scope = value },
                        )
                    }
                }
            }

            item { SectionLabel("分类", modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)) }
            item {
                if (visibleCategories.isEmpty()) {
                    Text("还没有分类", style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
                } else {
                    val expense = visibleCategories.filter { it.type == TxType.EXPENSE }
                    val income = visibleCategories.filter { it.type == TxType.INCOME }
                    val split = expense.isNotEmpty() && income.isNotEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SelectChip(
                            label = "全部",
                            selected = allCategories,
                            onClick = { allCategories = true },
                        )
                        if (expense.isNotEmpty()) {
                            CategoryGroup(
                                title = if (split) "支出" else null,
                                tint = Fen.colors.expense,
                                categories = expense,
                                picked = picked,
                                allCategories = allCategories,
                                onToggle = ::toggleCategory,
                            )
                        }
                        if (income.isNotEmpty()) {
                            CategoryGroup(
                                title = if (split) "收入" else null,
                                tint = Fen.colors.income,
                                categories = income,
                                picked = picked,
                                allCategories = allCategories,
                                onToggle = ::toggleCategory,
                            )
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Fen.colors.bg)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        ) {
            val enabled = count > 0
            Surface(
                onClick = { if (enabled) onShare(context, filter) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (enabled) Fen.colors.ink else Fen.colors.surfaceMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (enabled) "分享 CSV · $count 笔" else "没有符合条件的记录",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) {
                            if (Fen.colors.isDark) Color(0xFF15171B) else Color.White
                        } else {
                            Fen.colors.inkFaint
                        },
                    )
                }
            }
        }
    }

    if (showRange) {
        StatsRangePickerSheet(
            initialStart = customFrom,
            initialEnd = customTo,
            onDismiss = { showRange = false },
            onConfirm = { start, end ->
                customFrom = start
                customTo = end
                preset = TimePreset.CUSTOM
                showRange = false
            },
        )
    }
}

private fun rangeLabel(start: LocalDate, end: LocalDate): String {
    val sameYear = start.year == end.year
    val from = if (sameYear) start.shortDate() else "${start.year}年${start.shortDate()}"
    val until = if (sameYear) end.shortDate() else "${end.year}年${end.shortDate()}"
    return "$from – $until"
}

private fun TimePreset.range(customFrom: LocalDate, customTo: LocalDate): Pair<LocalDate?, LocalDate?> {
    val today = LocalDate.now()
    return when (this) {
        TimePreset.ALL -> null to null
        TimePreset.THIS_MONTH -> today.withDayOfMonth(1) to today
        TimePreset.LAST_MONTH -> {
            val last = YearMonth.from(today).minusMonths(1)
            last.atDay(1) to last.atEndOfMonth()
        }
        TimePreset.LAST_30 -> today.minusDays(29) to today
        TimePreset.THIS_YEAR -> LocalDate.of(today.year, 1, 1) to today
        TimePreset.CUSTOM -> customFrom to customTo
    }
}

@Composable
private fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color = Fen.colors.ink,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent else Fen.colors.surface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                if (Fen.colors.isDark) Color(0xFF15171B) else Color.White
            } else {
                Fen.colors.inkSoft
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGroup(
    title: String?,
    tint: Color,
    categories: List<Category>,
    picked: Set<String>,
    allCategories: Boolean,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = !allCategories && category.id in picked,
                    onClick = { onToggle(category.id) },
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    val tint = Color(category.color.toInt())
    val onColor = if (tint.luminance() > 0.62f) Color(0xFF15171B) else Color.White
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) tint else Fen.colors.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(onColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = categoryIcon(category.icon),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                IconBadge(icon = categoryIcon(category.icon), color = tint, size = 26.dp, iconSize = 14.dp)
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) onColor else Fen.colors.inkSoft,
            )
        }
    }
}

private fun Color.luminance(): Float = red * 0.299f + green * 0.587f + blue * 0.114f
