package app.fen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import app.fen.ui.theme.Fen
import app.fen.util.label
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * 自绘的日期 + 时间选择器，替代系统 / M3 默认弹窗。
 * 结构：月份导航 → 日历网格 → 时间调节 → 快捷时间 → 取消/确定。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerSheet(
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cursorMonth by remember { mutableStateOf(YearMonth.from(initial)) }
    var selectedDate by remember { mutableStateOf(initial.toLocalDate()) }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }

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
                    text = "选择时间",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 " +
                        "%02d:%02d".format(hour, minute),
                    style = MaterialTheme.typography.titleMedium,
                    color = Fen.colors.inkSoft,
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleAction(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "上一月",
                    onClick = { cursorMonth = cursorMonth.minusMonths(1) },
                )
                Text(
                    text = cursorMonth.label(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                CircleAction(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "下一月",
                    onClick = { cursorMonth = cursorMonth.plusMonths(1) },
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

            CalendarGrid(
                month = cursorMonth,
                selected = selectedDate,
                onSelect = { date ->
                    selectedDate = date
                    if (YearMonth.from(date) != cursorMonth) cursorMonth = YearMonth.from(date)
                },
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeSpinner(
                    value = hour,
                    label = "时",
                    onChange = { hour = (it + 24) % 24 },
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.displaySmall,
                    color = Fen.colors.inkFaint,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                TimeSpinner(
                    value = minute,
                    label = "分",
                    onChange = { minute = (it + 60) % 60 },
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickTime("现在") {
                    val now = LocalDateTime.now()
                    selectedDate = now.toLocalDate()
                    cursorMonth = YearMonth.from(now)
                    hour = now.hour
                    minute = now.minute
                }
                QuickTime("08:00") { hour = 8; minute = 0 }
                QuickTime("12:00") { hour = 12; minute = 0 }
                QuickTime("18:00") { hour = 18; minute = 0 }
            }

            Spacer(Modifier.height(20.dp))

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
                    onClick = { onConfirm(LocalDateTime.of(selectedDate, LocalTime.of(hour, minute))) },
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

@Composable
private fun CalendarGrid(month: YearMonth, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val firstDay = month.atDay(1)
    val leading = (firstDay.dayOfWeek.value + 6) % 7 // 周一 = 0
    val days = month.lengthOfMonth()
    val cells = leading + days
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val dayNumber = index - leading + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..days) {
                            val date = month.atDay(dayNumber)
                            val active = date == selected
                            val today = date == LocalDate.now()
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (active) Fen.colors.ink else Color.Transparent)
                                    .then(
                                        if (today && !active) {
                                            Modifier.background(Fen.colors.surfaceMuted, CircleShape)
                                        } else Modifier
                                    )
                                    .clickable { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$dayNumber",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (active || today) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        active -> if (Fen.colors.isDark) Color(0xFF15171B) else Color.White
                                        else -> Fen.colors.ink
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSpinner(value: Int, label: String, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpinnerButton("−") { onChange(value - 1) }
            Text(
                text = "%02d".format(value),
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                color = Fen.colors.ink,
                modifier = Modifier.width(72.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            SpinnerButton("+") { onChange(value + 1) }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkFaint,
        )
    }
}

@Composable
private fun SpinnerButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Fen.colors.surfaceMuted)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = Fen.colors.inkSoft,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun RowScope.QuickTime(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Fen.colors.surfaceMuted,
        modifier = Modifier.weight(1f).height(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Fen.colors.inkSoft,
            )
        }
    }
}


