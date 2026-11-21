package app.fen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fen.data.Category
import app.fen.data.Snapshot
import app.fen.data.TxType
import app.fen.ui.components.CircleAction
import app.fen.ui.components.IconBadge
import app.fen.ui.components.RowDivider
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
    onDelete: (Category) -> Unit,
    countFor: (String) -> Int,
) {
    var editorTarget by remember { mutableStateOf<Category?>(null) }
    var editorType by remember { mutableStateOf(TxType.EXPENSE) }
    var showEditor by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf<Pair<String, Int>?>(null) }

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
            val items = snapshot.categories.filter { it.type == type }.sortedBy { it.sortOrder }
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
                            CategoryRow(
                                category = category,
                                onClick = {
                                    editorTarget = category
                                    editorType = category.type
                                    showEditor = true
                                },
                            )
                            if (index != items.lastIndex) RowDivider(startInset = 64.dp)
                        }
                        if (items.isNotEmpty()) RowDivider(startInset = 0.dp)
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
                        blocked = target.name to used
                    } else {
                        onDelete(target)
                        showEditor = false
                    }
                }
            },
        )
    }

    blocked?.let { (name, count) ->
        AlertDialog(
            onDismissRequest = { blocked = null },
            title = { Text("无法删除「$name」") },
            text = { Text("这个分类下还有 $count 条记录。请先把这些记录改到其他分类，再删除。") },
            confirmButton = {
                TextButton(onClick = { blocked = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = categoryIcon(category.icon),
            color = Color(category.color),
            size = 36.dp,
            iconSize = 18.dp,
        )
        Spacer(Modifier.size(14.dp))
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
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除分类", color = Fen.colors.expense)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${initial?.name}」？") },
            text = { Text("删除后不可恢复。此操作只会删除分类本身。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete?.invoke()
                }) { Text("删除", color = Fen.colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
