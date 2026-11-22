package app.fen.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fen.mcp.McpServerState
import app.fen.mcp.SelfTestResult
import app.fen.mcp.audit.CallRecord
import app.fen.mcp.config.ClientKind
import app.fen.mcp.config.aiSetupPrompt
import app.fen.mcp.config.configText
import app.fen.mcp.config.toolDocsMarkdown
import app.fen.ui.McpViewModel
import app.fen.ui.components.CircleAction
import app.fen.ui.components.SectionLabel
import app.fen.ui.theme.Fen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun McpScreen(
    viewModel: McpViewModel,
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(viewModel.token()) }
    var selectedClient by remember { mutableStateOf(ClientKind.CLAUDE_CODE) }
    var showReset by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<SelfTestResult?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        viewModel.refreshCalls()
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val running = state as? McpServerState.Running
    val address = running?.addresses?.firstOrNull()
    val writeUntil = running?.writeUntil
    val writeEnabled = writeUntil != null && writeUntil > now
    val remainingMinutes = if (writeUntil != null) {
        ((writeUntil - now).coerceAtLeast(0) / 60_000).toInt()
    } else {
        0
    }
    val config = configText(selectedClient, address ?: "手机IP:端口", token)
    val docs = toolDocsMarkdown(viewModel.toolDescriptors(writeEnabled))

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.start() }

    fun startWithPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
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
            Text("本地 MCP", style = MaterialTheme.typography.headlineLarge)
        }

        SectionLabel("1. 开启服务", modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("本地 MCP 服务", style = MaterialTheme.typography.titleMedium)
                    Text(
                        statusText(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fen.colors.inkSoft,
                    )
                }
                Switch(
                    checked = state is McpServerState.Running,
                    onCheckedChange = { enabled ->
                        if (enabled) startWithPermission() else viewModel.stop()
                    },
                )
            }
            Text(
                "电脑需与手机连接同一 WiFi。服务只监听局域网，不会连接互联网。",
                style = MaterialTheme.typography.bodySmall,
                color = Fen.colors.inkSoft,
                modifier = Modifier.padding(top = 10.dp),
            )
            running?.addresses?.forEach { addr ->
                ValueRow("地址", "http://$addr/mcp", context)
            }
            if (running != null && running.addresses.isEmpty()) {
                Text(
                    "连接 WiFi 后可用；本机自测仍可通过 127.0.0.1。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fen.colors.inkSoft,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            (state as? McpServerState.Error)?.let {
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = Fen.colors.expense)
            }
        }

        SectionLabel("2. 添加到 AI 客户端", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        SectionCard {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClientKind.values().forEach { kind ->
                    FilterChip(
                        selected = selectedClient == kind,
                        onClick = { selectedClient = kind },
                        label = { Text(kind.label) },
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Text(
                config,
                style = MaterialTheme.typography.bodySmall,
                color = Fen.colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Fen.colors.surfaceMuted, RoundedCornerShape(12.dp))
                    .padding(10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { copy(context, config) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("复制配置")
                }
                TextButton(onClick = { share(context, "Fen MCP 配置", config) }) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("分享到电脑")
                }
                TextButton(onClick = {
                    copy(context, aiSetupPrompt(selectedClient, address ?: "手机IP:端口", token))
                }) {
                    Text("让 AI 帮我配置")
                }
            }
            ValueRow(
                label = "Authorization",
                value = "Bearer ${mask(token)}",
                context = context,
                copyValue = "Bearer $token",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { copy(context, "Bearer $token") }) {
                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("复制")
                }
                TextButton(onClick = { showReset = true }) {
                    Text("重置", color = Fen.colors.expense)
                }
            }
        }

        SectionLabel("3. 写入权限", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许 AI 写入", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (writeEnabled) "还可写入约 $remainingMinutes 分钟" else "默认关闭，关闭时写工具不可见",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (writeEnabled) Fen.colors.expense else Fen.colors.inkSoft,
                    )
                }
                Switch(
                    checked = writeEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setWriteDuration(if (enabled) 15L * 60_000 else null)
                    },
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5L, 15L, 30L, 60L).forEach { minutes ->
                    TextButton(onClick = { viewModel.setWriteDuration(minutes * 60_000) }) {
                        Text("${minutes} 分钟")
                    }
                }
            }
            Text(
                "开启后，任何持有 token 的客户端都能新增、修改、删除流水；所有操作都会留档，可在调用记录里整批撤销。",
                style = MaterialTheme.typography.bodySmall,
                color = Fen.colors.inkSoft,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SectionLabel("4. 接口说明（可选）", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        SectionCard {
            Text(
                "AI 不会调用或查错数据时，把这份说明发给它。",
                style = MaterialTheme.typography.bodySmall,
                color = Fen.colors.inkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { copy(context, docs) }) { Text("复制") }
                TextButton(onClick = { share(context, "Fen MCP 接口说明", docs) }) { Text("分享") }
            }
        }

        SectionLabel("5. 测试", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = running != null,
                    onClick = {
                        scope.launch { testResult = viewModel.selfTest() }
                    },
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("本机自测")
                }
                TextButton(onClick = { viewModel.refreshCalls() }) { Text("刷新记录") }
                TextButton(onClick = onOpenLog) { Text("全部记录") }
            }
            testResult?.let { result ->
                Text(
                    if (result.modernOk && result.legacyOk) {
                        "modern ✓ / legacy ✓ / ${result.toolCount} 个工具 / ${result.latencyMs}ms"
                    } else {
                        "自测失败：${result.error ?: "未知错误"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.modernOk && result.legacyOk) Fen.colors.income else Fen.colors.expense,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (calls.isEmpty()) {
                Text(
                    "还没有 AI 调用过",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fen.colors.inkSoft,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                calls.take(5).forEach { call ->
                    CallRow(call)
                }
            }
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("重置 token？") },
            text = { Text("所有已配置的客户端会立即断开，需要用新 token 重新配置。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetToken()
                    token = viewModel.token()
                    showReset = false
                    Toast.makeText(context, "已重置", Toast.LENGTH_SHORT).show()
                }) { Text("重置", color = Fen.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Fen.colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ValueRow(
    label: String,
    value: String,
    context: Context,
    copyValue: String = value,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clickable { copy(context, copyValue) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Fen.colors.inkSoft)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.ink)
        }
        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制", tint = Fen.colors.link, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CallRow(call: CallRecord) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                call.tool ?: call.method,
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.ink,
            )
            Text(
                "${call.clientName ?: call.clientIp} · ${call.durationMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = Fen.colors.inkSoft,
            )
        }
        Text(
            if (call.ok) "成功" else "失败",
            style = MaterialTheme.typography.bodySmall,
            color = if (call.ok) Fen.colors.income else Fen.colors.expense,
        )
    }
}

private fun statusText(state: McpServerState): String = when (state) {
    McpServerState.Stopped -> "未开启"
    McpServerState.Starting -> "启动中…"
    is McpServerState.Running -> "运行中 · ${state.addresses.size} 个地址"
    is McpServerState.Error -> "启动失败：${state.message}"
}

private fun mask(token: String): String =
    if (token.length <= 10) "••••••" else "${token.take(4)}••••••${token.takeLast(4)}"

private fun copy(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager.setPrimaryClip(ClipData.newPlainText("Fen MCP", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
