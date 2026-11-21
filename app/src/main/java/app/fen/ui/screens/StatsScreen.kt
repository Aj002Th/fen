package app.fen.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.fen.data.CategoryTotal
import app.fen.data.Snapshot
import app.fen.data.dailyExpense
import app.fen.data.expenseByCategory
import app.fen.data.inMonth
import app.fen.data.totals
import app.fen.ui.components.AmountText
import app.fen.ui.components.CircleAction
import app.fen.ui.components.MonthBar
import app.fen.ui.components.PageHeader
import app.fen.ui.components.SectionLabel
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney
import app.fen.util.label
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatsScreen(
    snapshot: Snapshot,
    month: YearMonth,
    onShiftMonth: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val monthTxs = remember(snapshot.transactions, month) { snapshot.transactions.inMonth(month) }
    val totals = remember(monthTxs) { monthTxs.totals() }
    val byCategory = remember(monthTxs, snapshot.categories) {
        monthTxs.expenseByCategory(snapshot.categories)
    }
    val days = remember(month) {
        val end = if (month == YearMonth.now()) LocalDate.now() else month.atEndOfMonth()
        (6 downTo 0).map { end.minusDays(it.toLong()) }
    }
    val trend = remember(monthTxs, days) { monthTxs.dailyExpense(days) }

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
                MonthBar(
                    label = month.label(),
                    onPrev = { onShiftMonth(-1) },
                    onNext = { onShiftMonth(1) },
                )
                Spacer(Modifier.height(16.dp))
            }

        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "本月支出",
                                style = MaterialTheme.typography.labelLarge,
                                color = Fen.colors.inkSoft,
                            )
                            AmountText(
                                amount = totals.expense,
                                style = MaterialTheme.typography.displaySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "收入",
                                style = MaterialTheme.typography.labelMedium,
                                color = Fen.colors.inkSoft,
                            )
                            Text(
                                formatMoney(totals.income),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                color = Fen.colors.income,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    if (byCategory.isEmpty()) {
                        Text(
                            "本月还没有支出记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fen.colors.inkSoft,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Donut(total = totals.expense, items = byCategory)
                            Spacer(Modifier.size(20.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                byCategory.take(5).forEach { item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                                .background(Color(item.category.color))
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Text(
                                            item.category.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "${(item.amount * 100.0 / totals.expense).toInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Fen.colors.inkSoft,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionLabel("分类排行", modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
        }

        items(count = byCategory.size, key = { byCategory[it].category.id }) { index ->
            CategoryBar(item = byCategory[index], total = totals.expense)
        }

        item {
            SectionLabel("最近 7 天", modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
        }

        item { TrendBars(days = days, values = trend) }
    }
    }
}

@Composable
private fun Donut(total: Long, items: List<CategoryTotal>) {
    val strokeWidth = 20.dp
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(140.dp)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            var startAngle = -90f
            items.forEach { item ->
                val sweep = if (total > 0) 360f * item.amount / total else 0f
                if (sweep > 0.5f) {
                    drawArc(
                        color = Color(item.category.color),
                        startAngle = startAngle + 1.2f,
                        sweepAngle = (sweep - 2.4f).coerceAtLeast(0.8f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("共", style = MaterialTheme.typography.labelMedium, color = Fen.colors.inkSoft)
            Text("${items.size} 类", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun CategoryBar(item: CategoryTotal, total: Long) {
    val fraction = if (total > 0) (item.amount.toFloat() / total) else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color(item.category.color))
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = item.category.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatMoney(item.amount),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(Fen.colors.surfaceMuted)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(item.category.color))
            )
        }
    }
}

@Composable
private fun TrendBars(days: List<LocalDate>, values: List<Long>) {
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            val value = values.getOrElse(index) { 0L }
            val fraction = value.toFloat() / max
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(90.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Fen.colors.surfaceMuted.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((90 * fraction.coerceAtLeast(0.05f)).dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (fraction > 0f) Fen.colors.ink else Color.Transparent)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${day.dayOfMonth}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkSoft,
                )
            }
        }
    }
}
