package app.fen.autobook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fen.autobook.capture.BatchItem
import app.fen.autobook.capture.BatchSelection
import app.fen.autobook.classify.DuplicateDetector
import app.fen.autobook.model.ParsedBill
import app.fen.data.Category
import app.fen.data.Tx
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney
import app.fen.util.toLocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchConfirmSheet(
    items: List<BatchItem>,
    categories: List<Category>,
    existing: List<Tx>,
    onSaveAll: (List<ParsedBill>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(items) {
        mutableStateOf(BatchSelection.initialSelection(items, existing))
    }
    val successCount = items.count { it.bill != null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Fen.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("识别到 $successCount 张账单", style = MaterialTheme.typography.headlineSmall)

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(items) { index, item ->
                    val bill = item.bill
                    if (bill == null) {
                        Text(
                            text = "未识别：${item.error ?: "未知错误"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Fen.colors.inkSoft,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        return@itemsIndexed
                    }

                    val duplicate = DuplicateDetector.find(bill, existing) != null
                    val categoryName = categories.firstOrNull { it.id == bill.categoryId }?.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (index in selected) {
                                    selected - index
                                } else {
                                    selected + index
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = index in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + index else selected - index
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = formatMoney(bill.amountMinor),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = buildString {
                                    append(bill.merchant.ifBlank { "未识别商户" })
                                    append(" · ")
                                    append(categoryName ?: "未分类")
                                    append(" · ")
                                    append(
                                        bill.occurredAt.toLocalDateTime()
                                            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                                    )
                                    if (duplicate) append(" · 可能重复")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Fen.colors.inkSoft,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }

                Button(
                    onClick = {
                        onSaveAll(BatchSelection.saveable(items, selected))
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("保存选中") }
            }
        }
    }
}
