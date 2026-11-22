package app.fen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import app.fen.ui.theme.Fen
import app.fen.util.label
import app.fen.util.shortDate
import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计范围选择：快捷项 + 区间日历。
 * 第一次点选起点，第二次点选终点；再点早于起点的日期会重新开始。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsRangePickerSheet(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cursorMonth by remember { mutableStateOf(YearMonth.from(initialEnd)) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var selectingStart by remember { mutableStateOf(true) }

    fun setRange(from: LocalDate, until: LocalDate) {
        val s = if (from <= until) from else until
        val e = if (from <= until) until else from
        start = s
        end = e
        cursorMonth = YearMonth.from(s)
        selectingStart = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Fen.colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Fen.colors.inkFaint) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "选择统计范围",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = rangeText(start, end),
                    style = MaterialTheme.typography.titleMedium,
                    color = Fen.colors.inkSoft,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val today = LocalDate.now()
                QuickRange("本月") {
                    setRange(today.withDayOfMonth(1), today)
                }
                QuickRange("上月") {
                    val last = YearMonth.from(today).minusMonths(1)
                    setRange(last.atDay(1), last.atEndOfMonth())
                }
                QuickRange("近 30 天") {
                    setRange(today.minusDays(29), today)
                }
                QuickRange("今年") {
                    setRange(LocalDate.of(today.year, 1, 1), today)
                }
                QuickRange("去年") {
                    val year = today.year - 1
                    setRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleAction(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "上个月",
                    onClick = { cursorMonth = cursorMonth.minusMonths(1) },
                )
                Text(
                    text = cursorMonth.label(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                CircleAction(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "下个月",
                    onClick = { cursorMonth = cursorMonth.plusMonths(1) },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (selectingStart) "选择起点" else "选择终点",
                    style = MaterialTheme.typography.labelLarge,
                    color = Fen.colors.inkSoft,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelMedium,
                            color = Fen.colors.inkSoft,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            RangeCalendarGrid(
                month = cursorMonth,
                start = start,
                end = end,
                onSelect = { date ->
                    if (selectingStart) {
                        start = date
                        end = date
                        selectingStart = false
                    } else if (date < start) {
                        start = date
                        end = date
                        selectingStart = false
                    } else {
                        end = date
                        selectingStart = true
                    }
                },
            )

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Fen.colors.surfaceMuted,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "取消",
                            style = MaterialTheme.typography.titleMedium,
                            color = Fen.colors.inkSoft,
                        )
                    }
                }
                Surface(
                    onClick = { onConfirm(start, end) },
                    modifier = Modifier.weight(1.4f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Fen.colors.ink,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "确定",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (Fen.colors.isDark) Color(0xFF15171B) else Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun rangeText(start: LocalDate, end: LocalDate): String {
    return if (start.year == end.year) {
        "${start.shortDate()} – ${end.shortDate()}"
    } else {
        "${start.year}/${start.monthValue}/${start.dayOfMonth} – " +
            "${end.year}/${end.monthValue}/${end.dayOfMonth}"
    }
}

@Composable
private fun QuickRange(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Fen.colors.surfaceMuted,
        modifier = Modifier.height(34.dp),
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Fen.colors.inkSoft,
            )
        }
    }
}

@Composable
private fun RangeCalendarGrid(
    month: YearMonth,
    start: LocalDate,
    end: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    val leading = (firstDay.dayOfWeek.value + 6) % 7
    val days = month.lengthOfMonth()
    val rows = (leading + days + 6) / 7
    val today = LocalDate.now()
    val activeText = if (Fen.colors.isDark) Color(0xFF15171B) else Color.White

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val dayNumber = index - leading + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..days) {
                            val date = month.atDay(dayNumber)
                            val future = date > today
                            val inRange = date >= start && date <= end
                            val isEdge = date == start || date == end
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isEdge -> Fen.colors.ink
                                            inRange && !future -> Fen.colors.surfaceMuted
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(enabled = !future) { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$dayNumber",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isEdge || date == today) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        future -> Fen.colors.inkFaint
                                        isEdge -> activeText
                                        date == today -> Fen.colors.ink
                                        inRange -> Fen.colors.ink
                                        else -> Fen.colors.inkSoft
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
