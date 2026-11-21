package app.fen.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.fen.ui.components.CircleAction
import app.fen.ui.components.IconBadge
import app.fen.ui.components.RowDivider
import app.fen.ui.components.SectionLabel
import app.fen.ui.theme.Fen

@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    showDebug: Boolean = false,
    onSeedDemo: () -> Unit = {},
    onClearData: () -> Unit = {},
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSeedConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // 开发选项默认隐藏：在「关于」弹窗里连点标题 10 次才出现；进程退出后重置
    var devUnlocked by remember { mutableStateOf(false) }
    var aboutTaps by remember { mutableStateOf(0) }

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
                Text("设置", style = MaterialTheme.typography.headlineLarge)
            }
        }

        item { SectionLabel("记账", modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    SettingItem(
                        title = "分类管理",
                        subtitle = "新增、编辑图标与颜色",
                        icon = Icons.Outlined.Category,
                        tint = Color(0xFFF2A93E),
                        onClick = onOpenCategories,
                    )
                }
            }
        }

        item { SectionLabel("外观", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) }
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingItem(
                    title = "深色模式",
                    subtitle = when (themeMode) {
                        1 -> "浅色"
                        2 -> "深色"
                        else -> "跟随系统"
                    },
                    icon = Icons.Outlined.DarkMode,
                    tint = Color(0xFF7C8794),
                    onClick = { showThemeDialog = true },
                )
            }
        }

        item { SectionLabel("关于", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) }
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingItem(
                    title = "Fen",
                    subtitle = "版本 0.5.2 · 开源",
                    icon = Icons.Outlined.Info,
                    tint = Color(0xFFF2583E),
                    onClick = {
                        aboutTaps = 0
                        showAbout = true
                    },
                )
            }
        }

        if (showDebug && devUnlocked) {
            item { SectionLabel("开发", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) }
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Fen.colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SettingItem(
                            title = "生成示例数据",
                            subtitle = "最近 6 个月 · 400 条",
                            icon = Icons.Outlined.BugReport,
                            tint = Color(0xFF21B8C7),
                            onClick = { showSeedConfirm = true },
                        )
                        RowDivider(startInset = 64.dp)
                        SettingItem(
                            title = "清空全部记录",
                            subtitle = "保留分类，仅删除流水",
                            icon = Icons.Outlined.Delete,
                            tint = Color(0xFFF2583E),
                            onClick = { showClearConfirm = true },
                        )
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("深色模式") },
            text = {
                Column {
                    listOf("跟随系统" to 0, "浅色" to 1, "深色" to 2).forEach { (label, mode) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (themeMode == mode) Fen.colors.ink else Fen.colors.inkSoft,
                                modifier = Modifier.weight(1f),
                            )
                            if (themeMode == mode) {
                                Text("✓", color = Fen.colors.ink)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("关闭") }
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = {
                Text(
                    text = "Fen · 分",
                    modifier = Modifier.clickable {
                        aboutTaps += 1
                        if (aboutTaps >= 10) {
                            devUnlocked = true
                            aboutTaps = 0
                            showAbout = false
                        }
                    },
                )
            },
            text = {
                Text(
                    "一个开源、克制的 Android 记账应用。\n" +
                        "数据只保存在本机，没有账号、没有网络请求。\n\n" +
                        "版本 0.4.0"
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("好的") }
            },
        )
    }
    if (showSeedConfirm) {
        AlertDialog(
            onDismissRequest = { showSeedConfirm = false },
            title = { Text("生成示例数据？") },
            text = { Text("会插入约 400 条最近 6 个月的记录，用于验收界面与统计。") },
            confirmButton = {
                TextButton(onClick = {
                    onSeedDemo()
                    showSeedConfirm = false
                }) { Text("生成") }
            },
            dismissButton = {
                TextButton(onClick = { showSeedConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部记录？") },
            text = { Text("只删除流水，分类会保留。此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearData()
                    showClearConfirm = false
                }) { Text("清空", color = Fen.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Fen.colors.inkFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}







