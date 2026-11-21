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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.fen.ui.components.IconBadge
import app.fen.ui.components.RowDivider
import app.fen.ui.components.SectionLabel
import app.fen.ui.theme.Fen

private data class SettingRow(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val tint: Color,
)

@Composable
fun SettingsScreen() {
    val groups = listOf(
        "记账" to listOf(
            SettingRow("分类管理", "图标、颜色与排序", Icons.Outlined.Category, Color(0xFFF2A93E)),
            SettingRow("账户管理", "余额与账户排序", Icons.Outlined.CreditCard, Color(0xFF3E8BF2)),
            SettingRow("预算", "每月支出上限", Icons.Outlined.Savings, Color(0xFF17A467)),
        ),
        "外观" to listOf(
            SettingRow("主题色", "强调色与图标风格", Icons.Outlined.ColorLens, Color(0xFF8B6CF2)),
            SettingRow("深色模式", "跟随系统", Icons.Outlined.DarkMode, Color(0xFF7C8794)),
        ),
        "数据" to listOf(
            SettingRow("备份与恢复", "导出 / 导入 JSON", Icons.Outlined.CloudUpload, Color(0xFF21B8C7)),
            SettingRow("导出 CSV", "用表格打开账单", Icons.Outlined.Book, Color(0xFFE05C9A)),
        ),
        "关于" to listOf(
            SettingRow("Fen", "版本 0.2.0 · 开源", Icons.Outlined.Info, Color(0xFFF2583E)),
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
    ) {
        item {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        groups.forEach { (title, items) ->
            item { SectionLabel(title, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Fen.colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        items.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconBadge(item.icon, item.tint, size = 36.dp, iconSize = 18.dp)
                                Spacer(Modifier.size(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    if (item.subtitle != null) {
                                        Text(
                                            item.subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Fen.colors.inkSoft,
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = Fen.colors.inkFaint,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (index != items.lastIndex) RowDivider(startInset = 64.dp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(4.dp)) }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

