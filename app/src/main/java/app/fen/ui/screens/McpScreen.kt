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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fen.mcp.McpServerState
import app.fen.mcp.SelfTestResult
import app.fen.mcp.config.setupInstruction
import app.fen.mcp.config.toolDocsMarkdown
import app.fen.ui.McpViewModel
import app.fen.ui.components.CircleAction
import app.fen.ui.components.RowDivider
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
    var autoStart by remember { mutableStateOf(viewModel.autoStartEnabled()) }
    var localOnly by remember { mutableStateOf(viewModel.localOnly()) }
    var idleMinutes by remember { mutableStateOf(viewModel.idleMinutes()) }
    var queryLimit by remember { mutableStateOf(viewModel.queryLimit()) }
    var showReset by remember { mutableStateOf(false) }
    var showIdle by remember { mutableStateOf(false) }
    var showLimit by remember { mutableStateOf(false) }
    var showFaq by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<SelfTestResult?>(null) }
    var selfTesting by remember { mutableStateOf(false) }
    var lanAddresses by remember { mutableStateOf(emptyList<String>()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        viewModel.refreshCalls()
        while (true) {
            now = System.currentTimeMillis()
            lanAddresses = viewModel.lanAddresses()
            delay(3_000)
        }
    }

    val running = state as? McpServerState.Running
    val port = running?.port ?: viewModel.port()
    val address = if (localOnly) {
        null
    } else {
        (running?.addresses?.firstOrNull() ?: lanAddresses.firstOrNull())?.let { "$it:$port" }
    }
    val writeUntil = running?.writeUntil
    val writeEnabled = writeUntil != null && writeUntil > now
    val maskedToken = "Bearer ${mask(token)}"
    val setup = setupInstruction(address ?: "手机IP:端口", token)
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 36.dp),
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
                "电脑需与手机连接同一 WiFi",
                style = MaterialTheme.typography.bodySmall,
                color = Fen.colors.inkSoft,
                modifier = Modifier.padding(top = 8.dp),
            )
            (state as? McpServerState.Error)?.let {
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = Fen.colors.expense)
            }
        }

        SectionLabel("2. 添加到 AI 客户端", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        SectionCard {
            Text(
                "复制后发给 AI 助手，让它帮你配置",
                style = MaterialTheme.typography.bodySmall,
                color = Fen.colors.inkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                WideButton("复制配置", Modifier.weight(1f), enabled = address != null) {
                    copy(context, "AI 配置", setup)
                }
                WideButton("分享到电脑", Modifier.weight(1f), enabled = address != null) {
                    share(context, "Fen MCP 配置", setup)
                }
            }
            InfoRow(
                label = "地址",
                value = when {
                    localOnly -> "仅本机可访问"
                    address != null -> "http://$address/mcp"
                    else -> "连接 WiFi 后可用"
                },
                action = "复制",
                onAction = { if (address != null) copy(context, "MCP 地址", "http://$address/mcp") },
            )
            InfoRow(
                label = "Authorization",
                value = maskedToken,
                action = "复制",
                onAction = { copy(context, "Authorization", "Bearer $token") },
                secondaryAction = "重置",
                onSecondary = { showReset = true },
            )
        }

        SectionLabel("3. 发送接口说明（可选）", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        SectionCard {
            Text(
                "AI 不会调用或查错数据时，发给它参考",
                style = MaterialTheme.typography.bodySmall,
                color = Fen.colors.inkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                WideButton("复制说明", Modifier.weight(1f)) { copy(context, "接口说明", docs) }
                WideButton("分享说明", Modifier.weight(1f)) { share(context, "Fen MCP 接口说明", docs) }
            }
        }

        SectionLabel("4. 测试", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        SectionCard {
            WideButton(
                text = if (selfTesting) "测试中…" else "本机自测",
                modifier = Modifier.fillMaxWidth(),
                enabled = running != null && !selfTesting,
            ) {
                selfTesting = true
                scope.launch {
                    try {
                        testResult = viewModel.selfTest()
                        viewModel.refreshCalls()
                    } finally {
                        selfTesting = false
                    }
                }
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
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            RowDivider(startInset = 0.dp, modifier = Modifier.padding(top = 12.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onOpenLog() }.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("调用记录", style = MaterialTheme.typography.titleMedium)
                    Text(
                        calls.firstOrNull()?.let { call ->
                            "${call.tool ?: call.method} · ${if (call.ok) "成功" else "失败"}"
                        } ?: "暂无",
                        style = MaterialTheme.typography.bodySmall,
                        color = Fen.colors.inkSoft,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Fen.colors.inkFaint, modifier = Modifier.size(18.dp))
            }
        }

        SectionLabel("偏好设置", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        SectionCard {
            PreferenceSwitch(
                title = "软件重启后保持开启",
                subtitle = "关闭时需每次手动开启",
                checked = autoStart,
                onCheckedChange = {
                    autoStart = it
                    viewModel.setAutoStart(it)
                },
            )
            RowDivider(startInset = 0.dp)
            PreferenceSwitch(
                title = "仅允许本机访问",
                subtitle = "电脑将无法连接",
                checked = localOnly,
                onCheckedChange = {
                    localOnly = it
                    viewModel.setLocalOnly(it)
                },
            )
            RowDivider(startInset = 0.dp)
            PreferenceSwitch(
                title = "允许 AI 记账",
                subtitle = "关闭时 AI 只能查询",
                checked = writeEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setWriteDuration(if (enabled) 30L * 60_000 else null)
                },
            )
            RowDivider(startInset = 0.dp)
            PreferenceValue(
                title = "闲置自动关闭",
                subtitle = "无调用时自动关闭服务",
                value = if (idleMinutes <= 0) "不自动关闭" else "$idleMinutes 分钟",
                onClick = { showIdle = true },
            )
            RowDivider(startInset = 0.dp)
            PreferenceValue(
                title = "单次查询上限",
                subtitle = "单次最多返回的账单条数",
                value = "$queryLimit 条",
                onClick = { showLimit = true },
            )
            RowDivider(startInset = 0.dp)
            PreferenceValue(
                title = "常见问题",
                subtitle = "连接失败、数据不对怎么办",
                value = "",
                onClick = { showFaq = true },
            )
        }

        Text(
            "数据仅在本机处理，不会上传。请勿泄露令牌，避免在公共 WiFi 下使用。",
            style = MaterialTheme.typography.bodySmall,
            color = Fen.colors.inkSoft,
            modifier = Modifier.padding(top = 16.dp),
        )
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

    if (showIdle) {
        OptionDialog(
            title = "闲置自动关闭",
            options = listOf(15 to "15 分钟", 30 to "30 分钟", 60 to "60 分钟", 0 to "不自动关闭"),
            selected = idleMinutes,
            onSelect = {
                idleMinutes = it
                viewModel.setIdleMinutes(it)
                showIdle = false
            },
            onDismiss = { showIdle = false },
        )
    }

    if (showLimit) {
        OptionDialog(
            title = "单次查询上限",
            options = listOf(50 to "50 条", 100 to "100 条", 200 to "200 条"),
            selected = queryLimit,
            onSelect = {
                queryLimit = it
                viewModel.setQueryLimit(it)
                showLimit = false
            },
            onDismiss = { showLimit = false },
        )
    }

    if (showFaq) {
        AlertDialog(
            onDismissRequest = { showFaq = false },
            title = { Text("常见问题") },
            text = {
                Column {
                    Text("连接失败：确认电脑和手机在同一 WiFi；关闭手机 VPN 或允许局域网；确认地址和端口与手机上显示一致。")
                    Spacer(Modifier.size(8.dp))
                    Text("401：token 被重置或复制不完整，重新复制 Authorization。")
                    Spacer(Modifier.size(8.dp))
                    Text("数据不对：AI 应先调用 get_overview；统计口径和 App 一致，未分类会单独列出。")
                    Spacer(Modifier.size(8.dp))
                    Text("写入失败：确认“允许 AI 记账”已开启，且分类 id 来自 list_categories。")
                    Spacer(Modifier.size(8.dp))
                    Text("息屏后连不上：HyperOS 可能冻结后台。到系统设置把 Fen·feat-mcp 的省电策略设为“无限制”，并允许后台运行。")
                }
            },
            confirmButton = { TextButton(onClick = { showFaq = false }) { Text("知道了") } },
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
private fun WideButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Fen.colors.surfaceMuted,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Fen.colors.ink else Fen.colors.inkFaint,
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    action: String,
    onAction: () -> Unit,
    secondaryAction: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
        }
        TextButton(onClick = onAction) { Text(action, color = Fen.colors.link) }
        if (secondaryAction != null && onSecondary != null) {
            TextButton(onClick = onSecondary) { Text(secondaryAction, color = Fen.colors.link) }
        }
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Fen.colors.inkSoft)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PreferenceValue(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Fen.colors.inkSoft)
        }
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Fen.colors.inkFaint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected == value) Fen.colors.ink else Fen.colors.inkSoft,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected == value) Text("✓", color = Fen.colors.ink)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun statusText(state: McpServerState): String = when (state) {
    McpServerState.Stopped -> "未开启"
    McpServerState.Starting -> "启动中…"
    is McpServerState.Running -> "运行中"
    is McpServerState.Error -> "启动失败：${state.message}"
}

private fun mask(token: String): String =
    if (token.length <= 10) "••••••" else "${token.take(4)}••••••${token.takeLast(4)}"

private fun copy(context: Context, label: String, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制：$label", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
