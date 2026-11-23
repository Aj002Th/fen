package app.fen.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fen.autobook.model.ParsedBill
import app.fen.autobook.ui.SourceLabels
import app.fen.data.Category
import app.fen.data.Snapshot
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.ui.components.AmountKeypad
import app.fen.ui.components.CircleAction
import app.fen.ui.components.DateTimePickerSheet
import app.fen.ui.components.Segmented
import app.fen.ui.icons.categoryIcon
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney
import app.fen.util.parseAmountToMinor
import app.fen.util.toAmountInput
import app.fen.util.toLocalDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddTransactionScreen(
    snapshot: Snapshot,
    initial: Tx? = null,
    draft: ParsedBill? = null,
    draftPreview: Bitmap? = null,
    suggestionPenalties: Map<String, Int> = emptyMap(),
    onPenalizeSuggestion: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onManageCategories: () -> Unit,
    onSave: (
        type: TxType,
        amountMinor: Long,
        categoryId: String?,
        note: String,
        dateTime: LocalDateTime,
        andContinue: Boolean,
    ) -> Unit,
) {
    val baseType = initial?.type ?: draft?.type ?: TxType.EXPENSE
    val baseAmount = initial?.amount ?: draft?.amountMinor ?: 0L
    val baseNote = initial?.note ?: draft?.merchant ?: ""
    val baseTime = initial?.occurredAt ?: draft?.occurredAt
    val baseCategoryId = initial?.categoryId ?: draft?.categoryId
    val initialCategory = remember(snapshot.categories, initial, draft) {
        baseCategoryId?.let { id -> snapshot.categories.firstOrNull { it.id == id } }
    }
    var type by remember(initial, draft) { mutableStateOf(baseType) }
    var amountText by remember(initial, draft) {
        mutableStateOf(if (baseAmount > 0) baseAmount.toAmountInput() else "")
    }
    var amountTouched by remember(initial, draft) { mutableStateOf(false) }
    var note by remember(initial, draft) { mutableStateOf(baseNote) }
    var dateTime by remember(initial, draft) {
        mutableStateOf(baseTime?.toLocalDateTime() ?: LocalDateTime.now())
    }
    var categoryId by remember(initial, draft) { mutableStateOf(initialCategory?.id) }
    var showPicker by remember { mutableStateOf(false) }
    var noteFocused by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    val accent = if (type == TxType.EXPENSE) Fen.colors.expense else Fen.colors.income
    val categories = remember(snapshot.categories, type) {
        snapshot.categories.filter { it.type == type }.sortedBy { it.sortOrder }
    }
    val selectedCategoryId = categories.firstOrNull { it.id == categoryId }?.id
        ?: if (initial == null && draft == null) categories.firstOrNull()?.id else null
    val suggestions = remember(
        snapshot.transactions,
        snapshot.categories,
        type,
        suggestionPenalties,
        draft,
    ) {
        if (draft != null) emptyList() else buildSuggestions(snapshot, type, suggestionPenalties)
    }
    val gridState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    LaunchedEffect(hint) {
        if (hint != null) {
            kotlinx.coroutines.delay(1800)
            hint = null
        }
    }

    fun buzz() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    val amountMinor = parseAmountToMinor(amountText)
    val canSave = amountMinor > 0 && selectedCategoryId != null

    fun key(input: String) {
        if (!amountTouched) {
            amountText = ""
            amountTouched = true
        }
        when (input) {
            "." -> if (!amountText.contains(".")) {
                amountText = if (amountText.isEmpty()) "0." else "$amountText."
            }
            else -> {
                val dot = amountText.indexOf('.')
                if (dot >= 0 && amountText.length - dot > 2) return
                if (amountText.length >= 9) return
                amountText = if (amountText == "0") input else amountText + input
            }
        }
    }

    fun submit(andContinue: Boolean) {
        val category = selectedCategoryId ?: return
        onSave(type, amountMinor, category, note, dateTime, andContinue)
        if (andContinue) {
            amountText = ""
            amountTouched = false
            note = ""
        }
    }

    Surface(color = Fen.colors.bg, modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleAction(
                    icon = Icons.Outlined.Close,
                    contentDescription = "关闭",
                    onClick = onClose,
                    tint = Fen.colors.ink,
                )
                if (draft != null) {
                    Spacer(Modifier.width(12.dp))
                    Text("确认账单", style = MaterialTheme.typography.headlineSmall)
                }
            }

            if (draft != null) {
                val source = SourceLabels.source(draft.sourceId)
                if (source != null) {
                    Text(
                        text = buildString {
                            append("来源：")
                            append(source)
                            SourceLabels.page(draft.sourcePage)?.let { append(" / $it") }
                            append(" · 图片 · 置信度 ")
                            append((draft.confidence * 100).toInt())
                            append("%")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Fen.colors.inkSoft,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (draftPreview != null) {
                    Image(
                        bitmap = draftPreview.asImageBitmap(),
                        contentDescription = "账单截图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Segmented(
                options = listOf("支出", "收入"),
                selected = if (type == TxType.EXPENSE) 0 else 1,
                onSelect = { index ->
                    val next = if (index == 0) TxType.EXPENSE else TxType.INCOME
                    if (next != type) {
                        type = next
                        categoryId = null
                    }
                },
                accent = accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(6.dp))

            // 只有分类区域可以滚动；底部滚到底部会淡出，提示还有内容
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
                ) {
                    items(
                        count = categories.size + 1,
                        key = { index -> if (index == categories.size) "manage" else categories[index].id },
                    ) { index ->
                        if (index == categories.size) {
                            ManageCategoriesTile(onClick = onManageCategories)
                        } else {
                            val category = categories[index]
                            CategoryTile(
                                category = category,
                                selected = category.id == selectedCategoryId,
                                onClick = { categoryId = category.id },
                            )
                        }
                    }
                }

                if (gridState.canScrollForward) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(26.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Fen.colors.bg)
                                )
                            )
                    )
                }
            }

            if (!noteFocused && amountText.isEmpty() && suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Fen.colors.surface,
                        ) {
                            Row(
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            categoryId = suggestion.category.id
                                            amountText = suggestion.amount.toAmountInput()
                                            amountTouched = true
                                        },
                                        onLongClick = {
                                            buzz()
                                            onPenalizeSuggestion(suggestion.category.id)
                                            hint = "已降低「${suggestion.category.name}」的推荐权重"
                                        },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = categoryIcon(suggestion.category.icon),
                                    contentDescription = null,
                                    tint = Color(suggestion.category.color),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    suggestion.category.name,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    formatMoney(suggestion.amount),
                                    style = MaterialTheme.typography.labelLarge
                                        .copy(fontFeatureSettings = "tnum"),
                                    color = Fen.colors.inkSoft,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "¥",
                            style = MaterialTheme.typography.headlineSmall,
                            color = accent.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 6.dp, bottom = 8.dp),
                        )
                        Text(
                            text = amountText.ifEmpty { "0.00" },
                            style = MaterialTheme.typography.displayLarge
                                .copy(fontFeatureSettings = "tnum"),
                            color = if (amountText.isEmpty()) accent.copy(alpha = 0.3f) else accent,
                            maxLines = 1,
                        )
                        Spacer(Modifier.weight(1f))
                        if (amountText.isNotEmpty()) {
                            Text(
                                text = if (type == TxType.EXPENSE) "支出" else "收入",
                                style = MaterialTheme.typography.labelMedium,
                                color = Fen.colors.inkSoft,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DateTimeChip(dateTime = dateTime, onClick = { showPicker = true })
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = note,
                            onValueChange = { note = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Fen.colors.ink),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { noteFocused = it.isFocused },
                            decorationBox = { inner ->
                                if (note.isEmpty()) {
                                    Text(
                                        "点击填写备注",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Fen.colors.inkFaint,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!noteFocused) {
                AmountKeypad(
                    onDigit = ::key,
                    onDot = { key(".") },
                    onBackspace = {
                        amountTouched = true
                        amountText = amountText.dropLast(1)
                    },
                    onRecord = { submit(andContinue = true) },
                    onDone = { submit(andContinue = false) },
                    canSave = canSave,
                    accent = accent,
                    showRecord = initial == null && draft == null,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

            AnimatedVisibility(
                visible = hint != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp),
                enter = androidx.compose.animation.fadeIn() +
                    androidx.compose.animation.slideInVertically { it / 3 },
                exit = androidx.compose.animation.fadeOut(),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (Fen.colors.isDark) Color(0xFF2A2D33) else Color(0xFF23262B),
                ) {
                    Text(
                        text = hint.orEmpty(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }

    if (showPicker) {
        DateTimePickerSheet(
            initial = dateTime,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                dateTime = picked
                showPicker = false
            },
        )
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(category.color)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(50.dp)
                        .border(1.5.dp, color.copy(alpha = 0.28f), CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (selected) color else color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(category.icon),
                    contentDescription = null,
                    tint = if (selected) Color.White else color,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Fen.colors.ink else Fen.colors.inkSoft,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ManageCategoriesTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Fen.colors.surfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "管理分类",
                    tint = Fen.colors.inkSoft,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "管理",
            style = MaterialTheme.typography.labelMedium,
            color = Fen.colors.inkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DateTimeChip(dateTime: LocalDateTime, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = Fen.colors.surfaceMuted,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = Fen.colors.inkSoft,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = formatDateTime(dateTime),
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.ink,
            )
        }
    }
}

private fun formatDateTime(dateTime: LocalDateTime): String {
    val time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
    return if (dateTime.toLocalDate() == LocalDate.now()) {
        "今天 $time"
    } else {
        "${dateTime.monthValue}月${dateTime.dayOfMonth}日 $time"
    }
}



private data class Suggestion(val category: Category, val amount: Long)

/** 近 30 天出现次数 >= 3 的分类，按「次数 - 3×降权次数」排序；不足 3 个时用最近记录补齐 */
private fun buildSuggestions(
    snapshot: Snapshot,
    type: TxType,
    penalties: Map<String, Int>,
): List<Suggestion> {
    val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val month = snapshot.transactions.filter {
        it.type == type && it.categoryId != null && it.occurredAt >= cutoff
    }
    val frequent = month.groupBy { it.categoryId!! }
        .map { (id, list) -> Triple(id, list.size - 3 * (penalties[id] ?: 0), list) }
        .filter { it.second >= 3 }
        .sortedByDescending { it.second }
        .mapNotNull { (id, _, list) ->
            snapshot.categories.firstOrNull { it.id == id }?.let {
                Suggestion(it, median(list.map { tx -> tx.amount }))
            }
        }
        .take(3)

    if (frequent.size >= 3) return frequent

    val used = frequent.map { it.category.id }.toSet()
    val extra = snapshot.transactions
        .filter {
            it.type == type && it.categoryId != null &&
                it.categoryId !in used && (penalties[it.categoryId] ?: 0) == 0
        }
        .distinctBy { it.categoryId }
        .take(3 - frequent.size)
        .mapNotNull { tx ->
            snapshot.categories.firstOrNull { it.id == tx.categoryId }?.let { Suggestion(it, tx.amount) }
        }
    return frequent + extra
}

private fun median(values: List<Long>): Long {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}



