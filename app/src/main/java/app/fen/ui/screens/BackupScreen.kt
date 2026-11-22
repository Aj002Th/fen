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
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.fen.ui.components.CircleAction
import app.fen.ui.components.DataRow
import app.fen.ui.components.RowDivider
import app.fen.ui.components.SectionLabel
import app.fen.ui.theme.Fen
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onExportBackup: (Uri) -> Unit,
    onRestore: () -> Unit,
) {
    val jsonExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportBackup) }

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
                Text("备份", style = MaterialTheme.typography.headlineLarge)
            }
        }

        item { SectionLabel("完整备份（JSON）", modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Fen.colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    DataRow(
                        title = "导出完整备份",
                        subtitle = "保留分类图标、颜色、id 与精确时间",
                        icon = Icons.Outlined.Upload,
                        tint = Color(0xFF17A467),
                        onClick = { jsonExport.launch(stamped("fen-backup", "json")) },
                    )
                    RowDivider(startInset = 64.dp)
                    DataRow(
                        title = "恢复完整备份",
                        subtitle = "从 JSON 还原（会覆盖当前全部数据）",
                        icon = Icons.Outlined.Restore,
                        tint = Color(0xFF8B6CF2),
                        onClick = onRestore,
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
