package app.fen.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fen.mcp.audit.CallRecord
import app.fen.mcp.audit.auditJsonl
import app.fen.ui.McpViewModel
import app.fen.ui.components.CircleAction
import app.fen.ui.components.EmptyState
import app.fen.ui.theme.Fen
import java.io.File
import kotlinx.coroutines.launch

private enum class LogFilter(val label: String) {
    ALL("全部"), READ("读取"), WRITE("写入"), DENIED("拒绝"),
}

@Composable
fun McpLogScreen(
    viewModel: McpViewModel,
    onBack: () -> Unit,
) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var selected by remember { mutableStateOf<CallRecord?>(null) }
    var showClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshCalls() }

    val visible = calls.filter { call ->
        when (filter) {
            LogFilter.ALL -> true
            LogFilter.READ -> call.tool?.let {
                !it.startsWith("add_") && !it.startsWith("update_") && !it.startsWith("delete_")
            } == true
            LogFilter.WRITE -> call.tool?.startsWith("add_") == true ||
                call.tool?.startsWith("update_") == true ||
                call.tool?.startsWith("delete_") == true
            LogFilter.DENIED -> !call.ok
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                tint = Fen.colors.ink,
            )
            Spacer(Modifier.size(10.dp))
            Text("调用记录", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { export(context, calls) }) { Text("导出", color = Fen.colors.link) }
            TextButton(onClick = { showClear = true }) { Text("清空", color = Fen.colors.expense) }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogFilter.values().forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                )
            }
        }

        if (visible.isEmpty()) {
            EmptyState("还没有调用记录", "AI 调用 Fen 后会出现在这里")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { "${it.tsMs}-${it.requestId}" }) { call ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Fen.colors.surface,
                        modifier = Modifier.fillMaxWidth().clickable { selected = call },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    call.tool ?: call.method,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (call.ok) "成功" else "失败",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (call.ok) Fen.colors.income else Fen.colors.expense,
                                )
                            }
                            Text(
                                "${call.clientName ?: call.clientIp} · ${call.durationMs}ms · ${call.era.removePrefix("MODERN_").removePrefix("LEGACY_")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Fen.colors.inkSoft,
                            )
                            if (!call.ok) {
                                Text(
                                    call.errorMessage ?: call.errorCode ?: "未知错误",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Fen.colors.expense,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { call ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(call.tool ?: call.method) },
            text = {
                Column {
                    Text("客户端：${call.clientName ?: "-"} ${call.clientVersion ?: ""}")
                    Text("IP：${call.clientIp}")
                    Text("协议：${call.era} / ${call.protocolVersion ?: "-"}")
                    Text("耗时：${call.durationMs}ms · 状态：${call.httpStatus}")
                    Text("参数：${call.argsSummary ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    call.batchId?.let { batch ->
                        Text("批次：$batch", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val result = viewModel.undo(batch)
                                    Toast.makeText(context, result.toString(), Toast.LENGTH_SHORT).show()
                                    selected = null
                                    viewModel.refreshCalls()
                                }
                            },
                        ) { Text("撤销这一批", color = Fen.colors.expense) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("关闭") }
            },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清空调用记录？") },
            text = { Text("只清空本地审计流水，不影响账本数据。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLog()
                    showClear = false
                }) { Text("清空", color = Fen.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("取消") }
            },
        )
    }
}

private fun export(context: Context, calls: List<CallRecord>) {
    try {
        val dir = File(context.cacheDir, "audit").apply { mkdirs() }
        val file = File(dir, "fen-mcp-audit-${System.currentTimeMillis()}.jsonl")
        file.writeText(auditJsonl(calls))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出调用记录"))
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "导出失败", Toast.LENGTH_SHORT).show()
    }
}
