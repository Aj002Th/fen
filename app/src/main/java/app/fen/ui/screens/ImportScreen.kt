package app.fen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fen.data.Category
import app.fen.data.TxType
import app.fen.data.exchange.CategoryMapping
import app.fen.data.exchange.CategorySuggest
import app.fen.data.exchange.MappingKind
import app.fen.data.exchange.MappingTarget
import app.fen.data.exchange.TransferPolicy
import app.fen.ui.ImportStage
import app.fen.ui.ImportUiState
import app.fen.ui.components.CircleAction
import app.fen.ui.components.SectionLabel
import app.fen.ui.components.Segmented
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val rangeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

@Composable
fun ImportScreen(
    state: ImportUiState,
    categories: List<Category>,
    onBack: () -> Unit,
    onSetSkipDuplicates: (Boolean) -> Unit,
    onSetTransferPolicy: (TransferPolicy) -> Unit,
    onUpdateMapping: (String, TxType, MappingTarget) -> Unit,
    onConfirm: () -> Unit,
    onRestore: () -> Unit,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    var step by remember(state.fileName) { mutableStateOf(0) }
    var editing by remember { mutableStateOf<CategoryMapping?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    LaunchedEffect(state.fileName, state.stage) {
        if (state.stage == ImportStage.READY) step = 0
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                tint = Fen.colors.ink,
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = if (state.stage == ImportStage.READY && step == 1) "调整分类" else "导入账单",
                style = MaterialTheme.typography.headlineLarge,
            )
        }

        when (state.stage) {
            ImportStage.PARSING, ImportStage.IMPORTING -> Busy(
                text = if (state.stage == ImportStage.PARSING) "正在解析文件…" else "正在写入…",
                subtitle = state.fileName,
            )

            ImportStage.ERROR -> ErrorState(state.error ?: "导入失败", onBack)

            ImportStage.READY -> {
                val session = state.session
                if (session == null) {
                    ErrorState("没有可导入的内容", onBack)
                } else if (session.isBackup) {
                    BackupReady(
                        fileName = state.fileName,
                        categories = session.backup?.categories?.size ?: 0,
                        transactions = session.backup?.transactions?.size ?: 0,
                        exportedAt = session.backup?.exportedAt ?: 0L,
                        onRestore = { confirmRestore = true },
                        onCancel = onBack,
                    )
                } else if (step == 0) {
                    Overview(
                        state = state,
                        onNext = { step = 1 },
                        onCancel = onBack,
                        onSetSkipDuplicates = onSetSkipDuplicates,
                        onSetTransferPolicy = onSetTransferPolicy,
                    )
                } else {
                    MappingList(
                        state = state,
                        categories = categories,
                        onEdit = { editing = it },
                        onBack = { step = 0 },
                        onConfirm = onConfirm,
                    )
                }
            }

            ImportStage.DONE -> DoneState(
                state = state,
                onDone = onDone,
                onUndo = { confirmUndo = true },
            )

            ImportStage.IDLE -> Unit
        }
    }

    editing?.let { mapping ->
        val records = state.session?.recordsFor(mapping.source, mapping.type).orEmpty()
        val distribution = CategorySuggest.autoDistribution(records, mapping.type, categories)
        MappingDialog(
            mapping = mapping,
            categories = categories,
            autoPreview = distribution.entries.take(4).joinToString(" · ") { "${it.key} ${it.value}" },
            onDismiss = { editing = null },
            onPick = { target ->
                onUpdateMapping(mapping.source, mapping.type, target)
                editing = null
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复这个备份？") },
            text = { Text("会先清空当前所有分类和流水，再写回备份内容。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    onRestore()
                }) { Text("恢复", color = Fen.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("取消") }
            },
        )
    }

    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("撤销这次导入？") },
            text = { Text("会删除这次导入写入的全部流水。导入时新建的分类会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUndo = false
                    onUndo()
                }) { Text("撤销", color = Fen.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUndo = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun Busy(text: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Fen.colors.ink, strokeWidth = 3.dp)
        Spacer(Modifier.height(18.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Fen.colors.inkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = RoundedCornerShape(22.dp), color = Fen.colors.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = Fen.colors.expense,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                ActionButton("返回", onClick = onBack, color = Fen.colors.surfaceMuted, textColor = Fen.colors.ink)
            }
        }
    }
}

@Composable
private fun BackupReady(
    fileName: String,
    categories: Int,
    transactions: Int,
    exportedAt: Long,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = Fen.colors.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(fileName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        InfoRow("分类", "$categories 个")
                        InfoRow("流水", "$transactions 条")
                        if (exportedAt > 0) {
                            InfoRow("导出时间", formatDateTime(exportedAt))
                        }
                    }
                }
            }
            item {
                WarningCard(
                    title = "恢复会覆盖当前数据",
                    body = "当前设备上的分类和流水都会被备份内容替换。建议先导出一份当前备份。",
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("取消", onClick = onCancel, color = Fen.colors.surfaceMuted, textColor = Fen.colors.ink, modifier = Modifier.weight(1f))
            ActionButton("恢复备份", onClick = onRestore, color = Fen.colors.expense, modifier = Modifier.weight(1.6f))
        }
    }
}

@Composable
private fun Overview(
    state: ImportUiState,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    onSetSkipDuplicates: (Boolean) -> Unit,
    onSetTransferPolicy: (TransferPolicy) -> Unit,
) {
    val session = state.session ?: return
    val (expenseTotal, incomeTotal) = session.total
    val range = session.dateRange

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Text(session.fileName, style = MaterialTheme.typography.titleMedium)
                Text(session.source, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
                Spacer(Modifier.height(12.dp))
            }

            item {
                Surface(shape = RoundedCornerShape(22.dp), color = Fen.colors.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 16.dp, horizontal = 18.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("支出", style = MaterialTheme.typography.labelMedium, color = Fen.colors.inkSoft)
                            Text("¥${formatMoney(expenseTotal)}", style = MaterialTheme.typography.headlineSmall, color = Fen.colors.expense)
                            Text("${session.expenseRecords.size} 笔", style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("收入", style = MaterialTheme.typography.labelMedium, color = Fen.colors.inkSoft)
                            Text("¥${formatMoney(incomeTotal)}", style = MaterialTheme.typography.headlineSmall, color = Fen.colors.income)
                            Text("${session.incomeRecords.size} 笔", style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    range?.let { "${formatDate(it.first)} – ${formatDate(it.second)}" } ?: "没有可导入的记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fen.colors.inkSoft,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    StatChip("分类 ${session.mappings.size}")
                    StatChip("跳过 ${session.skipped.size}")
                    if (session.transferCount > 0) StatChip("转账 ${session.transferCount}")
                    if (session.duplicateCount > 0) StatChip("重复 ${session.duplicateCount}")
                }
            }

            if (state.hashSeen) {
                item {
                    WarningCard(
                        title = "这个文件之前导入过",
                        body = "继续导入会再写一遍，可能出现重复记录。",
                        modifier = Modifier.padding(top = 14.dp),
                        color = Fen.colors.expense,
                    )
                }
            }

            session.warnings.forEach { warning ->
                item {
                    WarningCard(
                        title = "提示",
                        body = warning,
                        modifier = Modifier.padding(top = 10.dp),
                        color = Color(0xFFF2A93E),
                    )
                }
            }

            if (session.skipped.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Fen.colors.surfaceMuted,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("跳过的行", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            session.skipped.take(3).forEach { row ->
                                Text(
                                    "第 ${row.line} 行 · ${row.reason}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Fen.colors.inkSoft,
                                )
                            }
                            if (session.skipped.size > 3) {
                                Text(
                                    "还有 ${session.skipped.size - 3} 行…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Fen.colors.inkFaint,
                                )
                            }
                        }
                    }
                }
            }

            if (session.transferCount > 0) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("转账记录")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "iCost 模板里的「转账」没有 Fen 对应的类型，默认按金额正负判断：负数记为支出，正数记为收入。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fen.colors.inkSoft,
                    )
                    Spacer(Modifier.height(8.dp))
                    val options = listOf("按正负号", "跳过", "记支出", "记收入")
                    Segmented(
                        options = options,
                        selected = when (state.transferPolicy) {
                            TransferPolicy.SIGN -> 0
                            TransferPolicy.SKIP -> 1
                            TransferPolicy.EXPENSE -> 2
                            TransferPolicy.INCOME -> 3
                        },
                        onSelect = {
                            onSetTransferPolicy(
                                when (it) {
                                    1 -> TransferPolicy.SKIP
                                    2 -> TransferPolicy.EXPENSE
                                    3 -> TransferPolicy.INCOME
                                    else -> TransferPolicy.SIGN
                                }
                            )
                        },
                    )
                }
            }

            if (session.duplicateCount > 0) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(18.dp), color = Fen.colors.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("跳过完全相同的记录", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "同文件内时间、类型、金额、备注都一致，共 ${session.duplicateCount} 条",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Fen.colors.inkSoft,
                                )
                            }
                            Switch(checked = state.skipDuplicates, onCheckedChange = onSetSkipDuplicates)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("取消", onClick = onCancel, color = Fen.colors.surfaceMuted, textColor = Fen.colors.ink, modifier = Modifier.weight(1f))
            ActionButton(
                "调整分类（${session.mappings.size}）",
                onClick = onNext,
                color = Fen.colors.ink,
                modifier = Modifier.weight(1.8f),
                enabled = session.records.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun MappingList(
    state: ImportUiState,
    categories: List<Category>,
    onEdit: (CategoryMapping) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val session = state.session ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            "共 ${session.mappings.size} 个来源分类。默认建议已经填好，点任意一行可以改。",
            style = MaterialTheme.typography.bodyMedium,
            color = Fen.colors.inkSoft,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(session.mappings, key = { "${it.type}-${it.source}" }) { mapping ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Fen.colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        Modifier.clickable { onEdit(mapping) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                mapping.source.ifBlank { "未分类" },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${mapping.type.label()} · ${mapping.count} 笔 · ¥${formatMoney(mapping.amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Fen.colors.inkSoft,
                            )
                        }
                        Text(
                            targetLabel(mapping.target, categories),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mapping.target.kind == MappingKind.UNCATEGORIZED) Fen.colors.inkFaint else Fen.colors.ink,
                        )
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Fen.colors.inkFaint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("返回", onClick = onBack, color = Fen.colors.surfaceMuted, textColor = Fen.colors.ink, modifier = Modifier.weight(1f))
            ActionButton("开始导入", onClick = onConfirm, color = Fen.colors.ink, modifier = Modifier.weight(1.6f))
        }
    }
}

@Composable
private fun MappingDialog(
    mapping: CategoryMapping,
    categories: List<Category>,
    autoPreview: String,
    onDismiss: () -> Unit,
    onPick: (MappingTarget) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${mapping.source}」归到哪一类？") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OptionRow(
                    title = "按备注自动归类",
                    subtitle = if (autoPreview.isBlank()) "按商户/备注关键词匹配已有分类" else autoPreview,
                    selected = mapping.target.kind == MappingKind.AUTO,
                ) { onPick(MappingTarget(MappingKind.AUTO)) }
                OptionRow(
                    title = "未分类",
                    subtitle = "保留流水，但不参与分类统计",
                    selected = mapping.target.kind == MappingKind.UNCATEGORIZED,
                ) { onPick(MappingTarget(MappingKind.UNCATEGORIZED)) }

                Spacer(Modifier.height(10.dp))
                Text("现有分类", style = MaterialTheme.typography.labelLarge, color = Fen.colors.inkSoft)
                Spacer(Modifier.height(4.dp))
                categories.filter { it.type == mapping.type }.forEach { category ->
                    OptionRow(
                        title = category.name,
                        subtitle = category.type.label(),
                        selected = mapping.target.kind == MappingKind.EXISTING &&
                            mapping.target.categoryId == category.id,
                    ) { onPick(MappingTarget(MappingKind.EXISTING, categoryId = category.id)) }
                }

                Spacer(Modifier.height(10.dp))
                OptionRow(
                    title = "新建分类「${mapping.source.ifBlank { "未分类" }}」",
                    subtitle = "会创建并保留这个分类",
                    selected = mapping.target.kind == MappingKind.NEW,
                ) { onPick(MappingTarget(MappingKind.NEW, newName = mapping.source)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Fen.colors.ink else Fen.colors.inkSoft,
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkFaint)
            }
        }
        if (selected) Text("✓", color = Fen.colors.ink)
    }
}

@Composable
private fun DoneState(
    state: ImportUiState,
    onDone: () -> Unit,
    onUndo: () -> Unit,
) {
    val outcome = state.outcome
    val restored = state.restored
    val undone = state.undone
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (undone) Fen.colors.inkFaint else Fen.colors.income,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                undone -> "已撤销这次导入"
                restored != null -> "备份已恢复"
                else -> "导入完成"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                undone -> "这次导入写入的流水已全部删除，导入时新建的分类会保留"
                restored != null -> "共写回 $restored 条流水"
                outcome == null -> ""
                outcome.imported == 0 -> "没有新的记录需要写入"
                else -> "已导入 ${outcome.imported} 条流水" +
                    if (outcome.newCategories > 0) " · 新建 ${outcome.newCategories} 个分类" else ""
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Fen.colors.inkSoft,
            textAlign = TextAlign.Center,
        )
        if (!undone && outcome != null && outcome.skipped > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "跳过 ${outcome.skipped} 条",
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkFaint,
            )
        }
        Spacer(Modifier.height(26.dp))
        ActionButton("完成", onClick = onDone, color = Fen.colors.ink, modifier = Modifier.fillMaxWidth())
        if (!undone && outcome?.batchId != null) {
            Spacer(Modifier.height(10.dp))
            ActionButton(
                "撤销这次导入",
                onClick = onUndo,
                color = Fen.colors.surfaceMuted,
                textColor = Fen.colors.expense,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = Fen.colors.surfaceMuted) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkSoft,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun WarningCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF2A93E),
) {
    Surface(shape = RoundedCornerShape(18.dp), color = color.copy(alpha = 0.12f), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.ink)
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    color: Color,
    textColor: Color = Color.White,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else Fen.colors.surfaceMuted)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) textColor else Fen.colors.inkFaint,
        )
    }
}

@Composable
private fun targetLabel(target: MappingTarget, categories: List<Category>): String = when (target.kind) {
    MappingKind.EXISTING -> categories.firstOrNull { it.id == target.categoryId }?.name ?: "未分类"
    MappingKind.NEW -> "新建「${target.newName.ifBlank { "同名分类" }}」"
    MappingKind.AUTO -> "按备注自动归类"
    MappingKind.UNCATEGORIZED -> "未分类"
}

private fun TxType.label(): String = if (this == TxType.EXPENSE) "支出" else "收入"

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(rangeFormatter)

private fun formatDateTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)
}

