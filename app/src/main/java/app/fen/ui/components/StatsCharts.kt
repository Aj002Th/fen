package app.fen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fen.data.BucketUnit
import app.fen.data.StatsBucket
import app.fen.data.SummaryRow
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney
import app.fen.util.formatSignedMoney
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/* --------------------------------- 容器 --------------------------------- */

@Composable
fun StatsCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Fen.colors.surface,
        modifier = modifier.fillMaxWidth().padding(top = 14.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Fen.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = trailing,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = Fen.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sub != null) {
            Spacer(Modifier.height(1.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChartEmpty(text: String = "暂无记录") {
    Box(
        Modifier.fillMaxWidth().height(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
    }
}

/* -------------------------------- 双色柱状图 ------------------------------- */

@Composable
fun DualBarChart(
    buckets: List<StatsBucket>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    if (buckets.isEmpty() || buckets.all { it.expense == 0L && it.income == 0L }) {
        ChartEmpty()
        return
    }
    val maxValue = buckets.maxOf { maxOf(it.expense, it.income) }.coerceAtLeast(1L)
    val dividerColor = Fen.colors.divider
    val selectedColor = Fen.colors.surfaceMuted.copy(alpha = 0.7f)
    val expenseColor = Fen.colors.expense
    val incomeColor = Fen.colors.income
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(142.dp)
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / buckets.size
                        val index = (offset.x / slot).toInt().coerceIn(buckets.indices)
                        onSelect(if (selectedIndex == index) null else index)
                    }
                },
        ) {
            val baseline = size.height - 2.dp.toPx()
            val chartHeight = size.height - 10.dp.toPx()
            val slot = size.width / buckets.size
            val barWidth = (slot * 0.28f).coerceIn(2.dp.toPx(), 11.dp.toPx())
            val gap = 1.5.dp.toPx()

            drawLine(
                color = dividerColor,
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1.dp.toPx(),
            )

            buckets.forEachIndexed { index, bucket ->
                if (selectedIndex == index) {
                    drawRoundRect(
                        color = selectedColor,
                        topLeft = Offset(slot * index + 1.dp.toPx(), 0f),
                        size = Size((slot - 2.dp.toPx()).coerceAtLeast(1f), size.height),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
                val center = slot * index + slot / 2f
                val expenseHeight = (bucket.expense.toFloat() / maxValue) * chartHeight
                val incomeHeight = (bucket.income.toFloat() / maxValue) * chartHeight
                if (expenseHeight > 0f) {
                    drawRoundRect(
                        color = expenseColor,
                        topLeft = Offset(center - barWidth - gap / 2f, baseline - expenseHeight),
                        size = Size(barWidth, expenseHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
                if (incomeHeight > 0f) {
                    drawRoundRect(
                        color = incomeColor,
                        topLeft = Offset(center + gap / 2f, baseline - incomeHeight),
                        size = Size(barWidth, incomeHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labelIndices(buckets.size).forEach { index ->
                Text(
                    text = buckets[index].label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkSoft,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun labelIndices(count: Int): List<Int> = when {
    count <= 7 -> (0 until count).toList()
    count <= 12 -> listOf(0, count / 3, count * 2 / 3, count - 1).distinct()
    else -> listOf(0, count / 4, count / 2, count * 3 / 4, count - 1).distinct()
}

private fun sharePercent(share: Double): String =
    if (share > 0 && share < 0.01) "<1%" else "${(share * 100).roundToInt()}%"

/* ------------------------------- 累计结余折线 ------------------------------ */

@Composable
fun CumulativeChart(
    buckets: List<StatsBucket>,
    values: List<Long>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    if (buckets.isEmpty() || values.size < 2 || values.all { it == 0L }) {
        ChartEmpty("暂无净结余变化")
        return
    }
    val minValue = minOf(0L, values.min())
    val maxValue = maxOf(0L, values.max())
    val range = (maxValue - minValue).coerceAtLeast(1L)
    val dividerColor = Fen.colors.divider
    val areaColor = Fen.colors.ink.copy(alpha = 0.06f)
    val lineColor = Fen.colors.ink
    val cursorColor = Fen.colors.inkFaint
    val incomeColor = Fen.colors.income
    val expenseColor = Fen.colors.expense

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(124.dp)
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        val step = size.width.toFloat() / (values.size - 1).coerceAtLeast(1)
                        val index = (offset.x / step).roundToInt().coerceIn(values.indices)
                        onSelect(if (selectedIndex == index) null else index)
                    }
                },
        ) {
            val top = 6.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val chartHeight = (bottom - top).coerceAtLeast(1f)
            fun yOf(value: Long): Float =
                bottom - ((value - minValue).toFloat() / range) * chartHeight

            val zeroY = yOf(0L)
            drawLine(
                color = dividerColor,
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            )

            fun xOf(index: Int): Float =
                size.width * index / (values.size - 1).coerceAtLeast(1)

            val area = Path().apply {
                moveTo(0f, zeroY)
                values.forEachIndexed { index, value ->
                    lineTo(xOf(index), yOf(value))
                }
                lineTo(size.width, zeroY)
                close()
            }
            drawPath(area, color = areaColor)

            val line = Path().apply {
                values.forEachIndexed { index, value ->
                    val x = xOf(index)
                    val y = yOf(value)
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            val selected = selectedIndex?.coerceIn(values.indices)
            if (selected != null) {
                val x = xOf(selected)
                val y = yOf(values[selected])
                drawLine(
                    color = cursorColor,
                    start = Offset(x, top),
                    end = Offset(x, bottom),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(
                    color = if (values[selected] >= 0) incomeColor else expenseColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buckets.first().label,
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
            )
            Text(
                text = buckets.last().label,
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
            )
        }
    }
}

/* -------------------------------- 环形图 -------------------------------- */

data class DonutEntry(
    val key: String,
    val label: String,
    val amount: Long,
    val color: Color,
)

@Composable
fun CategoryDonut(
    entries: List<DonutEntry>,
    total: Long,
    categoryCount: Int,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    centerLabel: String,
) {
    if (entries.isEmpty() || total <= 0) {
        ChartEmpty()
        return
    }
    val selected = entries.firstOrNull { it.key == selectedKey }
    Box(
        modifier = Modifier
            .size(168.dp)
            .pointerInput(entries, total) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val distance = hypot(dx, dy)
                    val outer = size.width / 2f
                    val inner = outer - 26.dp.toPx()
                    if (distance !in inner..outer) return@detectTapGestures
                    var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                    if (angle < 0f) angle += 360f
                    var start = 0f
                    entries.forEach { entry ->
                        val sweep = 360f * entry.amount / total
                        if (angle >= start && angle < start + sweep) {
                            onSelect(if (selectedKey == entry.key) null else entry.key)
                            return@detectTapGestures
                        }
                        start += sweep
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val strokeWidth = 18.dp.toPx()
            val selectedStroke = 22.dp.toPx()
            val inset = selectedStroke / 2f
            val arcSize = Size(size.width - selectedStroke, size.height - selectedStroke)
            var startAngle = -90f
            entries.forEach { entry ->
                val sweep = if (total > 0) 360f * entry.amount / total else 0f
                val isSelected = entry.key == selectedKey
                val width = if (isSelected) selectedStroke else strokeWidth
                val gap = if (entries.size > 1) 2f else 0f
                if (sweep > 0.2f) {
                    if (sweep >= 359.8f) {
                        drawCircle(
                            color = entry.color,
                            radius = (size.minDimension - selectedStroke) / 2f,
                            style = Stroke(width = width),
                        )
                    } else {
                        drawArc(
                            color = entry.color,
                            startAngle = startAngle + gap / 2f,
                            sweepAngle = (sweep - gap).coerceAtLeast(0.6f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = width, cap = StrokeCap.Round),
                        )
                    }
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = selected?.label ?: centerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 30.dp),
            )
            Text(
                text = "¥${formatMoney(selected?.amount ?: total)}",
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = Fen.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = if (selected != null) {
                    sharePercent(selected.amount.toDouble() / total)
                } else {
                    "$categoryCount 类"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkSoft,
            )
        }
    }
}

@Composable
fun DonutLegend(
    entries: List<DonutEntry>,
    total: Long,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        entries.forEach { entry ->
            val isSelected = entry.key == selectedKey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Fen.colors.surfaceMuted else Color.Transparent)
                    .clickable { onSelect(if (isSelected) null else entry.key) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(entry.color),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fen.colors.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sharePercent(entry.amount.toDouble() / total),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = Fen.colors.inkSoft,
                )
            }
        }
    }
}

/* -------------------------------- 星期分布 ------------------------------- */

@Composable
fun WeekdayBars(values: List<Long>, counts: List<Int>) {
    val maxValue = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val maxIndex = values.indices.maxByOrNull { values[it] } ?: -1
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(7) { index ->
            val value = values.getOrElse(index) { 0L }
            val isMax = index == maxIndex && value > 0
            val fraction = (value.toFloat() / maxValue).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .height(84.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Fen.colors.surfaceMuted.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (value > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((84 * fraction.coerceAtLeast(0.04f)).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMax) Fen.colors.expense else Fen.colors.expense.copy(alpha = 0.45f)),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = labels[index],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isMax) Fen.colors.ink else Fen.colors.inkSoft,
                    fontWeight = if (isMax) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/* -------------------------------- 汇总表 -------------------------------- */

@Composable
fun SummaryTable(rows: List<SummaryRow>, unit: BucketUnit) {
    if (rows.isEmpty()) {
        ChartEmpty()
        return
    }
    var expanded by remember(rows) { mutableStateOf(false) }
    val body = rows.dropLast(2)
    val footer = rows.takeLast(2)
    // 超过 12 行才折叠；月视图常见 16+ 行，也会正常折叠。
    val collapsedLimit = 12
    val shownBody = if (expanded || body.size <= collapsedLimit) body else body.take(collapsedLimit)

    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            TableHeader("日期", 1.25f, TextAlign.Start)
            TableHeader("支出", 1f, TextAlign.End)
            TableHeader("收入", 1f, TextAlign.End)
            TableHeader("结余", 1f, TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Fen.colors.divider))
        shownBody.forEach { row ->
            SummaryRowView(row)
        }
        if (!expanded && body.size > collapsedLimit) {
            Text(
                text = "展开全部（${body.size} 行）",
                style = MaterialTheme.typography.labelLarge,
                color = Fen.colors.link,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Fen.colors.divider))
        footer.forEach { row -> SummaryRowView(row) }
        Spacer(Modifier.height(2.dp))
        Text(
            text = when (unit) {
                BucketUnit.DAY -> "均值按区间天数计算"
                BucketUnit.MONTH -> "均值按区间月数计算"
                BucketUnit.YEAR -> "均值按区间年数计算"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkFaint,
        )
    }
}

@Composable
private fun RowScope.TableHeader(text: String, weight: Float, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Fen.colors.inkSoft,
        textAlign = align,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun SummaryRowView(row: SummaryRow) {
    val valueColor = when {
        row.isTotal -> Fen.colors.ink
        row.isAverage -> Fen.colors.inkSoft
        else -> Fen.colors.ink
    }
    val netColor = when {
        row.isAverage -> Fen.colors.inkSoft
        row.net > 0 -> Fen.colors.income
        row.net < 0 -> Fen.colors.expense
        else -> Fen.colors.inkSoft
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (row.isTotal) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1.25f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TableCell(formatMoney(row.expense), 1f, valueColor, row.isTotal)
        TableCell(formatMoney(row.income), 1f, valueColor, row.isTotal)
        TableCell(formatSignedMoney(row.net), 1f, netColor, row.isTotal)
    }
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, color: Color, bold: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        color = color,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(weight),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
