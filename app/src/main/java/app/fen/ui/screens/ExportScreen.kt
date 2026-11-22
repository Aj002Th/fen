package app.fen.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.fen.data.exchange.FenTemplate
import app.fen.ui.components.CircleAction
import app.fen.ui.components.DataRow
import app.fen.ui.components.RowDivider
import app.fen.ui.components.SectionLabel
import app.fen.ui.theme.Fen
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    onExportCsv: (Uri) -> Unit,
    onSaveTemplate: (Uri) -> Unit,
) {
    val csvExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExportCsv) }
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
                Text("导出", style = MaterialTheme.typography.headlineLarge)
            }
        }

        item { SectionLabel("CSV 表格", modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Fen.colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    DataRow(
                        title = "导出表格",
                        subtitle = "CSV · 可用 Excel 编辑，不含图标与颜色",
                        icon = Icons.Outlined.FileUpload,
                        tint = Color(0xFF3E8BF2),
                        onClick = { csvExport.launch(stamped("fen", "csv")) },
                    )
                    RowDivider(startInset = 64.dp)
                    DataRow(
                        title = "下载导入模板",
                        subtitle = "CSV · 日期 / 类型 / 金额 / 分类 / 备注",
                        icon = Icons.Outlined.FileDownload,
                        tint = Color(0xFF17A467),
                        onClick = { templateSaver.launch(FenTemplate.fileName()) },
                    )
                }
            }
        }
    }
}

private fun stamped(prefix: String, extension: String): String {
    val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    return "$prefix-$now.$extension"
}
