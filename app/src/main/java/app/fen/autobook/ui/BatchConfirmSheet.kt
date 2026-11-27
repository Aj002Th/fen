package app.fen.autobook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fen.autobook.capture.BatchItem
import app.fen.autobook.capture.BatchSelection
import app.fen.autobook.classify.DuplicateDetector
import app.fen.autobook.model.ParsedBill
import app.fen.data.Category
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.ui.components.IconBadge
import app.fen.ui.icons.categoryIcon
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney
import app.fen.util.toLocalDateTime
import java.time.format.DateTimeFormatter

private val batchTimeFormat = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val importBlue = Color(0xFF3E8BF2)
private val fallbackCategoryColor = Color(0xFF7C8794)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchConfirmSheet(
    items: List<BatchItem>,
    categories: List<Category>,
    existing: List<Tx>,
    selected: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
    onEdit: (Int) -> Unit,
    onSaveAll: (List<ParsedBill>) -> Unit,
    onDismiss: () -> Unit,
) {
    val successCount = items.count { it.bill != null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Fen.colors.bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BatchHeader(
                count = successCount,
                selectedCount = selected.size,
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 2.dp),
            ) {
                itemsIndexed(
                    items = items,
                    key = { index, _ -> index },
                ) { index, item ->
                    val bill = item.bill
                    if (bill == null) {
                        BatchErrorCard(item)
                        return@itemsIndexed
                    }

                    BatchBillCard(
                        bill = bill,
                        category = categories.firstOrNull { it.id == bill.categoryId },
                        duplicate = DuplicateDetector.find(bill, existing) != null,
                        checked = index in selected,
                        onToggle = {
                            onSelectedChange(
                                if (index in selected) selected - index else selected + index
                            )
                        },
                        onEdit = { onEdit(index) },
                    )
                }
            }

            BatchFooter(
                selectedCount = selected.size,
                onDismiss = onDismiss,
                onSave = { onSaveAll(BatchSelection.saveable(items, selected)) },
            )
        }
    }
}

@Composable
private fun BatchHeader(count: Int, selectedCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            color = importBlue,
            size = 46.dp,
            iconSize = 22.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "识别到 $count 张账单",
                style = MaterialTheme.typography.headlineSmall,
                color = Fen.colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (count == 0) "没有可保存的账单" else "已自动分类，点击任意一笔可修改",
                style = MaterialTheme.typography.bodyMedium,
                color = Fen.colors.inkSoft,
            )
        }
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Fen.colors.surface,
            ) {
                Text(
                    text = "已选 $selectedCount / $count",
                    style = MaterialTheme.typography.labelMedium,
                    color = Fen.colors.inkSoft,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun BatchBillCard(
    bill: ParsedBill,
    category: Category?,
    duplicate: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val type = bill.type ?: TxType.EXPENSE
    val categoryColor = category?.let { Color(it.color) } ?: fallbackCategoryColor
    val amountColor = if (type == TxType.INCOME) Fen.colors.income else Fen.colors.expense
    val sign = if (type == TxType.INCOME) "+" else "-"

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Fen.colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(start = 2.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BillCheckbox(checked = checked, onToggle = onToggle)
            Spacer(Modifier.width(2.dp))
            IconBadge(
                icon = categoryIcon(category?.icon ?: "other"),
                color = categoryColor,
                size = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bill.merchant.ifBlank { "未识别商户" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Fen.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$sign${formatMoney(bill.amountMinor)}",
                        style = MaterialTheme.typography.titleLarge
                            .copy(fontFeatureSettings = "tnum"),
                        color = amountColor,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildString {
                            append(category?.name ?: "未分类")
                            append(" · ")
                            append(bill.occurredAt.toLocalDateTime().format(batchTimeFormat))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fen.colors.inkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (duplicate) {
                        Spacer(Modifier.width(6.dp))
                        DuplicateBadge()
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = Fen.colors.inkFaint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun BatchErrorCard(item: BatchItem) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Fen.colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = Icons.Outlined.ErrorOutline,
                color = Fen.colors.expense,
                size = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "这张没识别出来",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Fen.colors.ink,
                )
                Text(
                    text = item.error ?: "未知错误",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fen.colors.inkSoft,
                )
            }
        }
    }
}

@Composable
private fun DuplicateBadge() {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        shape = shape,
        color = Fen.colors.expense.copy(alpha = if (Fen.colors.isDark) 0.18f else 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Fen.colors.expense)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "可能重复",
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.expense,
            )
        }
    }
}

@Composable
private fun BillCheckbox(checked: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(shape)
                .background(if (checked) Fen.colors.ink else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (checked) Fen.colors.ink else Fen.colors.inkFaint,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Fen.colors.surface,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun BatchFooter(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val enabled = selectedCount > 0
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onDismiss,
            shape = RoundedCornerShape(16.dp),
            color = Fen.colors.surface,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.titleMedium,
                    color = Fen.colors.ink,
                )
            }
        }
        Surface(
            onClick = onSave,
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = if (enabled) Fen.colors.ink else Fen.colors.surfaceMuted,
            modifier = Modifier
                .weight(1.5f)
                .height(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (enabled) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Fen.colors.surface,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (enabled) "保存 $selectedCount 笔" else "保存选中",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) Fen.colors.surface else Fen.colors.inkFaint,
                    )
                }
            }
        }
    }
}
