package app.fen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import app.fen.data.Category
import app.fen.data.Snapshot
import app.fen.data.TxType
import app.fen.ui.components.CircleAction
import app.fen.ui.components.IconBadge
import app.fen.ui.components.SectionLabel
import app.fen.ui.icons.CategoryIconKeys
import app.fen.ui.icons.categoryIcon
import app.fen.ui.theme.CategoryColorValues
import app.fen.ui.theme.Fen

@Composable
fun CategoryManagerScreen(
    snapshot: Snapshot,
    onBack: () -> Unit,
    onAdd: (name: String, type: TxType, icon: String, color: Long) -> Unit,
    onUpdate: (Category) -> Unit,
    onReorder: (TxType, List<String>) -> Unit,
    onDelete: (Category) -> Unit,
    onMerge: (Category, Category) -> Unit,
    onDeleteWithTransactions: (Category) -> Unit,
    countFor: (String) -> Int,
) {
    var editorTarget by remember { mutableStateOf<Category?>(null) }
    var editorType by remember { mutableStateOf(TxType.EXPENSE) }
    var showEditor by remember { mutableStateOf(false) }
    var mergeSource by remember { mutableStateOf<Category?>(null) }
    var mergeTarget by remember { mutableStateOf<Category?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var draft by remember { mutableStateOf<Map<TxType, List<String>>>(emptyMap()) }
    val density = LocalDensity.current
    val rowHeight = 60.dp
    val rowHeightPx = with(density) { rowHeight.toPx() }

    fun openNew(type: TxType) {
        editorTarget = null
        editorType = type
        showEditor = true
    }

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
                Text("分类管理", style = MaterialTheme.typography.headlineLarge)
            }
        }

        listOf(TxType.EXPENSE to "支出", TxType.INCOME to "收入").forEach { (type, title) ->
            val stored = snapshot.categories.filter { it.type == type }.sortedBy { it.sortOrder }
            val override = draft[type]
            val byId = stored.associateBy { it.id }
            val drafted = override?.mapNotNull { byId[it] }.orEmpty()
            val items = if (drafted.size == stored.size) drafted else stored
            item {
                SectionLabel(
                    text = "${title}分类",
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    trailing = {
                        Text(
                            "${items.size} 个",
                            style = MaterialTheme.typography.labelMedium,
                            color = Fen.colors.inkSoft,
                        )
                    },
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Fen.colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        items.forEachIndexed { index, category ->
                            key(category.id) {
                                DraggableCategoryRow(
                                    category = category,
                                    index = index,
                                    lastIndex = items.lastIndex,
                                    dragging = draggedId == category.id,
                                    dragOffset = if (draggedId == category.id) dragOffset else 0f,
                                    rowHeight = rowHeight,
                                    onClick = {
                                        editorTarget = category
                                        editorType = category.type
                                        showEditor = true
                                    },
                                    onDragStart = {
                                        draggedId = category.id
                                        dragOffset = 0f
                                    },
                                    onDrag = { amount ->
                                        dragOffset += amount
                                        val currentIndex = items.indexOfFirst { it.id == category.id }
                                        if (currentIndex < 0) return@DraggableCategoryRow
                                        var movedToIndex = currentIndex
                                        if (dragOffset > rowHeightPx / 2f && currentIndex < items.lastIndex) {
                                            draft = draft + (type to items.move(currentIndex, currentIndex + 1).map { it.id })
                                            dragOffset -= rowHeightPx
                                            movedToIndex = currentIndex + 1
                                        } else if (dragOffset < -rowHeightPx / 2f && currentIndex > 0) {
                                            draft = draft + (type to items.move(currentIndex, currentIndex - 1).map { it.id })
                                            dragOffset += rowHeightPx
                                            movedToIndex = currentIndex - 1
                                        }
                                        if (movedToIndex <= 0 && dragOffset < 0f) dragOffset = 0f
                                        if (movedToIndex >= items.lastIndex && dragOffset > 0f) dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        val order = draft[type]
                                        if (order != null && order != stored.map { it.id }) {
                                            onReorder(type, order)
                                        }
                                        draft = draft - type
                                        draggedId = null
                                        dragOffset = 0f
                                    },
                                )
                            }
                        }
                        if (items.isNotEmpty()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Fen.colors.divider),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openNew(type) }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Fen.colors.surfaceMuted),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = Fen.colors.inkSoft,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.size(14.dp))
                            Text(
                                "新增${title}分类",
                                style = MaterialTheme.typography.titleMedium,
                                color = Fen.colors.inkSoft,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        CategoryEditorSheet(
            initial = editorTarget,
            type = editorType,
            onDismiss = { showEditor = false },
            onSave = { name, icon, color ->
                val target = editorTarget
                if (target == null) {
                    onAdd(name, editorType, icon, color)
                } else {
                    onUpdate(target.copy(name = name, icon = icon, color = color))
                }
                showEditor = false
            },
            onDelete = editorTarget?.let { target ->
                {
                    val used = countFor(target.id)
                    if (used > 0) {
                        mergeSource = target
                        mergeTarget = null
                        showEditor = false
                    } else {
                        onDelete(target)
                        showEditor = false
                    }
                }
            },
        )
    }

    mergeSource?.let { source ->
        val used = countFor(source.id)
        val candidates = snapshot.categories
            .filter { it.type == source.type && it.id != source.id }
            .sortedBy { it.sortOrder }
        var hardConfirm by remember(source.id) { mutableStateOf(false) }
        var mergeConfirm by remember(source.id) { mutableStateOf(false) }

        Dialog(
            onDismissRequest = {
                when {
                    mergeConfirm -> mergeConfirm = false
                    hardConfirm -> hardConfirm = false
                    else -> {
                        mergeSource = null
                        mergeTarget = null
                    }
                }
            },
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth(0.92f),
            ) {
                Column(Modifier.padding(24.dp)) {
                    if (mergeConfirm) {
                        Text(
                            "把 $used 条记录转移到「${mergeTarget?.name}」？",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "「${source.name}」会被删除，记录本身会保留。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fen.colors.inkSoft,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { mergeConfirm = false }) {
                                Text("取消", color = Fen.colors.inkSoft)
                            }
                            TextButton(onClick = {
                                val target = mergeTarget
                                if (target != null) {
                                    onMerge(source, target)
                                }
                                mergeSource = null
                                mergeTarget = null
                                mergeConfirm = false
                            }) { Text("转移并删除") }
                        }
                    } else if (!hardConfirm) {
                        Text(
                            "「${source.name}」下有 $used 条记录",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (candidates.isEmpty()) {
                            Text(
                                "还没有同类型的其他分类。可以先新建一个分类再转移，或者直接删除这些记录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Fen.colors.inkSoft,
                            )
                        } else {
                            Text(
                                "选择要转移到的分类；转移后会删除「${source.name}」。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Fen.colors.inkSoft,
                            )
                            Spacer(Modifier.height(4.dp))
                            Column(
                                Modifier
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                candidates.forEach { candidate ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { mergeTarget = candidate }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = mergeTarget?.id == candidate.id,
                                            onClick = { mergeTarget = candidate },
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        IconBadge(
                                            icon = categoryIcon(candidate.icon),
                                            color = Color(candidate.color),
                                            size = 32.dp,
                                            iconSize = 16.dp,
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Text(
                                            candidate.name,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { hardConfirm = true }) {
                                Text("直接删除", color = Fen.colors.expense)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                mergeSource = null
                                mergeTarget = null
                            }) { Text("取消", color = Fen.colors.inkSoft) }
                            TextButton(
                                enabled = mergeTarget != null,
                                onClick = {
                                    if (mergeTarget != null) {
                                        mergeConfirm = true
                                    }
                                },
                            ) { Text("转移并删除") }
                        }
                    } else {
                        Text(
                            "删除「${source.name}」及 $used 条记录？",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "这些记录会一起删除，且无法恢复。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fen.colors.inkSoft,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { hardConfirm = false }) {
                                Text("取消", color = Fen.colors.inkSoft)
                            }
                            TextButton(onClick = {
                                onDeleteWithTransactions(source)
                                mergeSource = null
                                mergeTarget = null
                                hardConfirm = false
                            }) { Text("删除", color = Fen.colors.expense) }
                        }
                    }
                }
            }
        }
    }
}

private fun <T> List<T>.move(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val next = toMutableList()
    val item = next.removeAt(from)
    next.add(to, item)
    return next
}

@Composable
private fun DraggableCategoryRow(
    category: Category,
    index: Int,
    lastIndex: Int,
    dragging: Boolean,
    dragOffset: Float,
    rowHeight: Dp,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .then(
                if (dragging) {
                    Modifier
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Fen.colors.surface)
                        .border(1.dp, Fen.colors.divider, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 6.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .pointerInput(category.id) {
                        detectDragGestures(
                            onDragStart = { currentOnDragStart() },
                            onDragEnd = { currentOnDragEnd() },
                            onDragCancel = { currentOnDragEnd() },
                            onDrag = { change, amount ->
                                change.consume()
                                currentOnDrag(amount.y)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragIndicator,
                    contentDescription = "拖动排序${category.name}",
                    tint = if (dragging) Fen.colors.ink else Fen.colors.inkFaint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            IconBadge(
                icon = categoryIcon(category.icon),
                color = Color(category.color),
                size = 36.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Fen.colors.inkFaint,
                modifier = Modifier.size(18.dp),
            )
        }
        if (index < lastIndex) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 92.dp)
                    .height(1.dp)
                    .background(Fen.colors.divider),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorSheet(
    initial: Category?,
    type: TxType,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: Long) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var icon by remember(initial) { mutableStateOf(initial?.icon ?: CategoryIconKeys.first()) }
    var color by remember(initial) { mutableStateOf(initial?.color ?: CategoryColorValues.first()) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Fen.colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Fen.colors.inkFaint) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                text = if (initial == null) "新增${if (type == TxType.EXPENSE) "支出" else "收入"}分类" else "编辑分类",
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Fen.colors.surfaceMuted,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { if (it.length <= 8) name = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = Fen.colors.ink),
                        cursorBrush = SolidColor(Color(color)),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (name.isEmpty()) {
                                Text(
                                    "分类名称（最多 8 个字）",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Fen.colors.inkFaint,
                                )
                            }
                            inner()
                        },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("图标", style = MaterialTheme.typography.labelLarge, color = Fen.colors.inkSoft)
            Spacer(Modifier.height(8.dp))

            CategoryIconKeys.chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        val active = key == icon
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    if (active) Color(color) else Fen.colors.surfaceMuted
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = categoryIcon(key),
                                contentDescription = null,
                                tint = if (active) Color.White else Fen.colors.inkSoft,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(10.dp))
            Text("颜色", style = MaterialTheme.typography.labelLarge, color = Fen.colors.inkSoft)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryColorValues.forEach { value ->
                    val active = value == color
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(value))
                            .then(
                                if (active) {
                                    Modifier.border(2.dp, Fen.colors.ink, CircleShape)
                                } else Modifier
                            )
                            .clickable { color = value },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            Text("✓", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Surface(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), icon, color) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (name.isNotBlank()) Fen.colors.ink else Fen.colors.surfaceMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "保存",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (name.isNotBlank()) {
                            if (Fen.colors.isDark) Color(0xFF15171B) else Color.White
                        } else {
                            Fen.colors.inkFaint
                        },
                    )
                }
            }

            if (onDelete != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onDelete.invoke() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除分类", color = Fen.colors.expense)
                }
            }
        }
    }
}



