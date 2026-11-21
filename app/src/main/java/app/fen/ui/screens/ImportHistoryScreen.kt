package app.fen.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import app.fen.data.ImportBatch
import app.fen.ui.components.CircleAction
import app.fen.ui.components.EmptyState
import app.fen.ui.theme.Fen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val batchDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

@Composable
fun ImportHistoryScreen(
    batches: List<ImportBatch>,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ImportBatch?>(null) }

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
                Text("导入记录", style = MaterialTheme.typography.headlineLarge)
            }
        }

        if (batches.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有导入过",
                    subtitle = "导入 iCost 账单或从备份恢复后会在这里留下记录",
                )
            }
        } else {
            items(batches, key = { it.id }) { batch ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Fen.colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                batch.label.ifBlank { "未命名导入" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                "${batch.source} · ${batch.rows} 条 · ${formatBatchTime(batch.createdAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Fen.colors.inkSoft,
                            )
                        }
                        CircleAction(
                            icon = Icons.Outlined.DeleteOutline,
                            contentDescription = "删除这次导入",
                            onClick = { pendingDelete = batch },
                            container = Fen.colors.surface,
                            tint = Fen.colors.inkFaint,
                        )
                    }
                }
            }

            item {
                Text(
                    "删除导入记录会同时删除那次导入写入的流水；导入时新建的分类会保留。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fen.colors.inkSoft,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }

    pendingDelete?.let { batch ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这次导入？") },
            text = {
                Text("会删除「${batch.label.ifBlank { "未命名导入" }}」写入的 ${batch.rows} 条流水。此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(batch.id)
                    pendingDelete = null
                }) { Text("删除", color = Fen.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

private fun formatBatchTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(batchDateFormatter)
