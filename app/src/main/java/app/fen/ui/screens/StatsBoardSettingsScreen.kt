package app.fen.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import app.fen.data.StatsBoardConfig
import app.fen.data.StatsSection
import app.fen.ui.components.CircleAction
import app.fen.ui.components.RowDivider
import app.fen.ui.components.SectionLabel
import app.fen.ui.theme.Fen

@Composable
fun StatsBoardSettingsScreen(
    config: StatsBoardConfig,
    onBack: () -> Unit,
    onToggle: (StatsSection, Boolean) -> Unit,
    onMove: (StatsSection, Int) -> Unit,
    onRankingLimit: (Int) -> Unit,
    onSummaryShowEmpty: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleAction(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    tint = Fen.colors.ink,
                )
                Spacer(Modifier.size(10.dp))
                Text("统计看板", style = MaterialTheme.typography.headlineLarge)
            }
        }

        item {
            Text(
                text = "开关控制是否显示，箭头调整顺序。所有数据仍然在统计页内展开，不会进入子页面。",
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        item { SectionLabel("显示与顺序", modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    config.order.forEachIndexed { index, section ->
                        SectionSettingRow(
                            index = index,
                            section = section,
                            visible = section !in config.hidden,
                            canUp = index > 0,
                            canDown = index < config.order.lastIndex,
                            onToggle = { onToggle(section, it) },
                            onUp = { onMove(section, -1) },
                            onDown = { onMove(section, 1) },
                        )
                        if (index < config.order.lastIndex) {
                            RowDivider(startInset = 48.dp)
                        }
                    }
                }
            }
        }

        item { SectionLabel("明细选项", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) }
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("分类排行显示数量", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "超出的分类可点击“查看全部”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Fen.colors.inkSoft,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        LimitChips(current = config.rankingLimit, onChange = onRankingLimit)
                    }
                    RowDivider(startInset = 14.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("账单汇总显示无记录行", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "关闭时只显示有记录的日/月/年",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Fen.colors.inkSoft,
                            )
                        }
                        Switch(
                            checked = config.summaryShowEmpty,
                            onCheckedChange = onSummaryShowEmpty,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = if (Fen.colors.isDark) Color(0xFF15171B) else Color.White,
                                checkedTrackColor = Fen.colors.ink,
                            ),
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "统计范围不在这里设置，直接在统计页顶部切换；最后选择会被记住。",
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.inkFaint,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        item {
            Text(
                text = "恢复默认看板",
                style = MaterialTheme.typography.labelLarge,
                color = Fen.colors.expense,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showResetConfirm = true }
                    .padding(vertical = 18.dp),
            )
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复默认看板？") },
            text = { Text("会恢复默认顺序、全部显示，并把排行数量改回 5。") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetConfirm = false
                }) { Text("恢复", color = Fen.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionSettingRow(
    index: Int,
    section: StatsSection,
    visible: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkFaint,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (visible) Fen.colors.ink else Fen.colors.inkSoft,
            )
            Text(
                text = section.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
                maxLines = 1,
            )
        }
        ArrowButton(
            icon = Icons.Outlined.KeyboardArrowUp,
            contentDescription = "上移${section.title}",
            enabled = canUp,
            onClick = onUp,
        )
        Spacer(Modifier.width(2.dp))
        ArrowButton(
            icon = Icons.Outlined.KeyboardArrowDown,
            contentDescription = "下移${section.title}",
            enabled = canDown,
            onClick = onDown,
        )
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = visible,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (Fen.colors.isDark) Color(0xFF15171B) else Color.White,
                checkedTrackColor = Fen.colors.ink,
            ),
        )
    }
}

@Composable
private fun ArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (enabled) Fen.colors.surfaceMuted else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Fen.colors.ink else Fen.colors.inkFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LimitChips(current: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Fen.colors.surfaceMuted)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(5 to "5", 10 to "10", 0 to "全部").forEach { (value, label) ->
            val active = current == value
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Fen.colors.surface else Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Fen.colors.ink else Fen.colors.inkSoft,
                )
            }
        }
    }
}
