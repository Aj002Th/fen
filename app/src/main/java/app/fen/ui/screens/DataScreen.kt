package app.fen.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.rounded.ChevronRight
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
import app.fen.data.exchange.FenTemplate
import app.fen.ui.components.CircleAction
import app.fen.ui.components.IconBadge
import app.fen.ui.components.RowDivider
import app.fen.ui.components.SectionLabel
import app.fen.ui.theme.Fen
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun DataScreen(
    batchCount: Int,
    onBack: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenHistory: () -> Unit,
    onExportCsv: (Uri) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onSaveTemplate: (Uri) -> Unit,
) {
    val csvExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExportCsv) }
    val jsonExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportBackup) }
    val templateSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onSaveTemplate) }

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
                Text("数据", style = MaterialTheme.typography.headlineLarge)
            }
        }

        item { SectionLabel("导入", modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Fen.colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    DataItem(
                        title = "账单导入",
                        subtitle = "iCost / 微信 / 支付宝 / Fen CSV / JSON 备份",
                        icon = Icons.Outlined.FileUpload,
                        tint = Color(0xFFF2A93E),
                        onClick = onOpenImport,
                    )
                    RowDivider(startInset = 64.dp)
                    DataItem(
                        title = "恢复完整备份",
                        subtitle = "从 Fen JSON 备份还原（会覆盖当前数据）",
                        icon = Icons.Outlined.Restore,
                        tint = Color(0xFF8B6CF2),
                        onClick = onOpenRestore,
                    )
                    RowDivider(startInset = 64.dp)
                    DataItem(
                        title = "导入记录",
                        subtitle = if (batchCount > 0) "共 $batchCount 次，可整批撤销" else "还没有导入过",
                        icon = Icons.Outlined.History,
                        tint = Color(0xFF7C8794),
                        onClick = onOpenHistory,
                    )
                }
            }
        }

        item { SectionLabel("导出", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Fen.colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    DataItem(
                        title = "导出表格",
                        subtitle = "CSV · 用 Excel / WPS 打开、编辑或归档",
                        icon = Icons.Outlined.TableChart,
                        tint = Color(0xFF3E8BF2),
                        onClick = { csvExport.launch(stamped("fen", "csv")) },
                    )
                    RowDivider(startInset = 64.dp)
                    DataItem(
                        title = "下载导入模板",
                        subtitle = "CSV · 日期 / 类型 / 金额 / 分类 / 备注",
                        icon = Icons.Outlined.FileDownload,
                        tint = Color(0xFF17A467),
                        onClick = { templateSaver.launch(FenTemplate.fileName()) },
                    )
                    RowDivider(startInset = 64.dp)
                    DataItem(
                        title = "完整备份",
                        subtitle = "JSON · 保留分类、图标与精确时间，换机恢复",
                        icon = Icons.Outlined.Restore,
                        tint = Color(0xFF8B6CF2),
                        onClick = { jsonExport.launch(stamped("fen-backup", "json")) },
                    )
                }
            }
        }

        item {
            Text(
                "导入时可以先选「自动识别」，也可以指定 iCost / 微信 / 支付宝 / Fen 模板。" +
                    "所有导入都会先预览，再让你调整分类。",
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
                modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

private fun stamped(prefix: String, extension: String): String {
    val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    return "$prefix-$now.$extension"
}

@Composable
private fun DataItem(
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
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Fen.colors.inkFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}
