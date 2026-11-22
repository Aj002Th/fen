package app.fen.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
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
import app.fen.data.exchange.FenTemplate
import app.fen.data.exchange.ImportSource
import app.fen.data.exchange.MappingKind
import app.fen.data.exchange.MappingTarget
import app.fen.data.exchange.TransferPolicy
import app.fen.data.exchange.UNBUCKETED_NAME
import app.fen.data.exchange.UNCATEGORIZED_NAME
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
    batchCount: Int,
    onPickFile: (Uri) -> Unit,
    onSaveTemplate: (Uri) -> Unit,
    onSetSource: (ImportSource) -> Unit,
    onStartParse: () -> Unit,
    onExit: () -> Unit,
    onBackToSetup: () -> Unit,
    onOpenHistory: () -> Unit,
    onSetSkipDuplicates: (Boolean) -> Unit,
    onSetTransferPolicy: (TransferPolicy) -> Unit,
    onUpdateMapping: (String, TxType, MappingTarget) -> Unit,
    onSetMappingView: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onRestore: () -> Unit,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    var editing by remember { mutableStateOf<CategoryMapping?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    var sourcePicker by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPickFile) }
    val templateSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onSaveTemplate) }

    fun back() {
        when {
            state.stage == ImportStage.READY && state.mapping -> onSetMappingView(false)
            state.stage == ImportStage.READY -> onBackToSetup()
            state.stage == ImportStage.IDLE -> onExit()
            else -> onExit()
        }
    }

    BackHandler { back() }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = { back() },
                tint = Fen.colors.ink,
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = when {
                    state.stage == ImportStage.READY && state.mapping -> "调整分类"
                    state.stage == ImportStage.READY || state.stage == ImportStage.PARSING ->
                        if (state.source.isBackup) "恢复预览" else "导入预览"
                    state.source.isBackup -> "恢复备份"
                    else -> "账单导入"
                },
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            if (!state.source.isBackup) {
                CircleAction(
                    icon = Icons.Outlined.HelpOutline,
                    contentDescription = "导入说明",
                    onClick = { showHelp = true },
                    tint = Fen.colors.inkSoft,
                )
            }
        }

        when (state.stage) {
            ImportStage.IDLE -> SetupContent(
                state = state,
                batchCount = batchCount,
                onPickFile = { filePicker.launch(arrayOf("*/*")) },
                onDownloadTemplate = { templateSaver.launch(FenTemplate.fileName()) },
                onPickSource = { sourcePicker = true },
                onParse = onStartParse,
                onOpenHistory = onOpenHistory,
            )

            ImportStage.PARSING, ImportStage.IMPORTING -> Busy(
                text = if (state.stage == ImportStage.PARSING) "正在解析文件…" else "正在写入…",
                subtitle = state.fileName,
            )

            ImportStage.ERROR -> ErrorState(state.error ?: "导入失败", onExit)

            ImportStage.READY -> {
                val session = state.session
                if (session == null) {
                    ErrorState("没有可导入的内容", onExit)
                } else if (session.isBackup) {
                    BackupReady(
                        fileName = state.fileName,
                        categories = session.backup?.categories?.size ?: 0,
                        transactions = session.backup?.transactions?.size ?: 0,
                        exportedAt = session.backup?.exportedAt ?: 0L,
                        onRestore = { confirmRestore = true },
                        onCancel = onBackToSetup,
                    )
                } else if (!state.mapping) {
                    Overview(
                        state = state,
                        onNext = { onSetMappingView(true) },
                        onCancel = onBackToSetup,
                        onSetSkipDuplicates = onSetSkipDuplicates,
                        onSetTransferPolicy = onSetTransferPolicy,
                    )
                } else {
                    MappingList(
                        state = state,
                        categories = categories,
                        onEdit = { editing = it },
                        onBack = { onSetMappingView(false) },
                        onConfirm = onConfirm,
                    )
                }
            }

            ImportStage.DONE -> DoneState(
                state = state,
                onDone = onDone,
                onUndo = { confirmUndo = true },
            )
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("导入说明") },
            text = {
                Text(
                    "自动识别：按表头判断格式，大多数情况够用。\n\n" +
                        "通用表格：只依赖表头（日期、金额、分类或类型），不要求列顺序。\n\n" +
                        "来源只是提示；选错也可以在预览页返回重选。",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("知道了") }
            },
        )
    }

    if (sourcePicker) {
        SourcePickerDialog(
            current = state.source,
            onDismiss = { sourcePicker = false },
            onPick = {
                onSetSource(it)
                sourcePicker = false
            },
        )
    }

    editing?.let { mapping ->
        MappingDialog(
            mapping = mapping,
            categories = categories,
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
            text = { Text("会删除这次导入写入的全部流水（包括后来改过金额或分类的）。导入时新建的分类会保留。") },
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

/* ------------------------------- 第一步：选文件与来源 ------------------------------- */

@Composable
private fun SetupContent(
    state: ImportUiState,
    batchCount: Int,
    onPickFile: () -> Unit,
    onDownloadTemplate: () -> Unit,
    onPickSource: () -> Unit,
    onParse: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (state.source.isBackup) {
                item {
                    WarningCard(
                        title = "正在恢复完整备份",
                        body = "会先清空当前所有分类和流水，再写回备份。请选择 .json 备份文件。",
                        modifier = Modifier.padding(top = 12.dp),
                        color = Fen.colors.expense,
                    )
                }
                item {
                    SectionLabel("按步骤操作", modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Fen.colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        StepRow(
                            label = "选择备份文件",
                            value = state.fileName.ifBlank { "点此选择" },
                            valueStrong = state.fileName.isNotBlank(),
                            placeholder = state.fileName.isBlank(),
                            onClick = onPickFile,
                        )
                    }
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Fen.colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickAction(
                                title = "导入记录",
                                badge = if (batchCount > 0) batchCount.toString() else null,
                                icon = Icons.Outlined.History,
                                onClick = onOpenHistory,
                                modifier = Modifier.weight(1f),
                            )
                            QuickAction(
                                title = "下载模板文件",
                                badge = null,
                                icon = Icons.Outlined.FileOpen,
                                onClick = onDownloadTemplate,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item {
                    SectionLabel(
                        "按步骤操作",
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Fen.colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            StepRow(
                                label = "选择数据文件",
                                value = state.fileName.ifBlank { "点此选择" },
                                valueStrong = state.fileName.isNotBlank(),
                                placeholder = state.fileName.isBlank(),
                                onClick = onPickFile,
                            )
                            RowDivider()
                            StepRow(
                                label = "选择导入来源",
                                value = sourceText(state.source),
                                valueStrong = true,
                                placeholder = false,
                                onClick = onPickSource,
                            )
                        }
                    }
                }
            }

            state.error?.let { error ->
                item {
                    WarningCard(
                        title = "解析失败",
                        body = error,
                        modifier = Modifier.padding(top = 14.dp),
                        color = Fen.colors.expense,
                    )
                }
            }
        }

        ActionButton(
            label = if (state.pickedUri == null) "请先选择文件" else "解析",
            onClick = onParse,
            color = Fen.colors.ink,
            enabled = state.pickedUri != null,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun QuickAction(
    title: String,
    badge: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Fen.colors.surfaceMuted,
        modifier = modifier.height(52.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Fen.colors.inkSoft, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Fen.colors.ink)
            if (badge != null) {
                Surface(shape = RoundedCornerShape(8.dp), color = Fen.colors.ink) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = Fen.colors.surface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    label: String,
    value: String,
    valueStrong: Boolean,
    placeholder: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Fen.colors.ink)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                placeholder -> Fen.colors.inkFaint
                valueStrong -> Fen.colors.ink
                else -> Fen.colors.inkSoft
            },
            maxLines = 1,
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Fen.colors.inkFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Fen.colors.divider))
}

private fun sourceText(source: ImportSource): String = when {
    source == ImportSource.AUTO -> "自动识别（推荐）"
    source == ImportSource.FEN_JSON -> "完整备份"
    source.group.isBlank() -> source.label
    else -> "${source.group} · ${source.label}"
}

/**
 * 二级来源选择：第一层是「自动识别 / Fen / iCost / 平台账单 / 其他」，
 * 第二层才是具体格式。Fen 和 iCost 各自有 CSV/JSON、导出/模板两种。
 */
@Composable
private fun SourcePickerDialog(
    current: ImportSource,
    onDismiss: () -> Unit,
    onPick: (ImportSource) -> Unit,
) {
    var group by remember { mutableStateOf<String?>(null) }
    val activeGroup = group

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activeGroup != null) {
                    Text(
                        "‹",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.clickable { group = null }.padding(end = 12.dp),
                    )
                }
                Text(if (activeGroup == null) "选择导入来源" else activeGroup)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (activeGroup == null) {
                    OptionRow(
                        title = "自动识别",
                        subtitle = "推荐：按表头自动判断，不用先选来源",
                        selected = current == ImportSource.AUTO,
                    ) { onPick(ImportSource.AUTO) }
                    OptionRow(
                        title = "Fen 表格",
                        subtitle = "日期 / 类型 / 金额 / 分类 / 备注",
                        selected = current == ImportSource.FEN_CSV,
                    ) { onPick(ImportSource.FEN_CSV) }
                    ImportSource.groups.forEach { name ->
                        val children = ImportSource.inGroup(name)
                        OptionRow(
                            title = name,
                            subtitle = children.joinToString(" / ") { it.label },
                            selected = current.group == name,
                            trailingChevron = true,
                        ) { group = name }
                    }
                    OptionRow(
                        title = "通用表格",
                        subtitle = "只依赖表头：日期 / 金额 / 分类或类型",
                        selected = current == ImportSource.GENERIC,
                    ) { onPick(ImportSource.GENERIC) }
                } else {
                    ImportSource.inGroup(activeGroup).forEach { source ->
                        OptionRow(
                            title = source.label,
                            subtitle = source.note,
                            selected = current == source,
                        ) { onPick(source) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/* ------------------------------- 解析结果 ------------------------------- */

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
            ActionButton("返回", onClick = onCancel, color = Fen.colors.surfaceMuted, textColor = Fen.colors.ink, modifier = Modifier.weight(1f))
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
            ActionButton("返回", onClick = onCancel, color = Fen.colors.surfaceMuted, textColor = Fen.colors.ink, modifier = Modifier.weight(1f))
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
            "共 ${session.mappings.size} 个分类结果。默认已按备注归好，点任意一行可以整体改。",
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
                                "${mapping.type.label()} · ${mapping.count} 笔 · ¥${formatMoney(mapping.amount)}" +
                                    if (mapping.origins.isNotEmpty()) {
                                        " · 来自 ${mapping.origins.joinToString("、")}"
                                    } else "",
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
    onDismiss: () -> Unit,
    onPick: (MappingTarget) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${mapping.source}」归到哪一类？") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "${mapping.type.label()} · ${mapping.count} 笔 · ¥${formatMoney(mapping.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fen.colors.inkSoft,
                )
                Spacer(Modifier.height(8.dp))

                OptionRow(
                    title = "未分类",
                    subtitle = "先放入「未分类」，之后可以再改",
                    selected = mapping.target.kind == MappingKind.UNCATEGORIZED,
                ) { onPick(MappingTarget(MappingKind.UNCATEGORIZED)) }

                val sourceName = mapping.source.trim()
                val canCreate = sourceName.isNotEmpty() &&
                    sourceName != UNBUCKETED_NAME &&
                    sourceName != UNCATEGORIZED_NAME &&
                    categories.none { it.type == mapping.type && it.name == sourceName }
                if (canCreate) {
                    Spacer(Modifier.height(6.dp))
                    OptionRow(
                        title = "新建「$sourceName」分类",
                        subtitle = "现有分类都不合适时使用",
                        selected = mapping.target.kind == MappingKind.NEW,
                    ) { onPick(MappingTarget(MappingKind.NEW, newName = sourceName)) }
                }

                Spacer(Modifier.height(10.dp))
                Text("现有分类", style = MaterialTheme.typography.labelLarge, color = Fen.colors.inkSoft)
                Spacer(Modifier.height(4.dp))
                categories
                    .filter { it.type == mapping.type && it.name != UNCATEGORIZED_NAME }
                    .forEach { category ->
                        OptionRow(
                            title = category.name,
                            subtitle = category.type.label(),
                            selected = mapping.target.kind == MappingKind.EXISTING &&
                                mapping.target.categoryId == category.id,
                        ) { onPick(MappingTarget(MappingKind.EXISTING, categoryId = category.id)) }
                    }
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
    trailingChevron: Boolean = false,
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
        when {
            selected -> Text("✓", color = Fen.colors.ink)
            trailingChevron -> Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Fen.colors.inkFaint,
                modifier = Modifier.size(18.dp),
            )
        }
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
    MappingKind.UNCATEGORIZED -> "未分类"
}

private fun TxType.label(): String = if (this == TxType.EXPENSE) "支出" else "收入"

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(rangeFormatter)

private fun formatDateTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)
}
