package app.fen.autobook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fen.autobook.classify.DuplicateDetector
import app.fen.autobook.model.ParsedBill
import app.fen.data.Category
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.ui.theme.Fen
import app.fen.util.parseAmountToMinor
import app.fen.util.toAmountInput
import app.fen.util.toLocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmBillSheet(
    bill: ParsedBill,
    categories: List<Category>,
    existing: List<Tx>,
    onSave: (TxType, Long, String?, String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember(bill) { mutableStateOf(bill.type ?: TxType.EXPENSE) }
    var amountText by remember(bill) { mutableStateOf(bill.amountMinor.toAmountInput()) }
    var categoryId by remember(bill) { mutableStateOf(bill.categoryId) }
    var note by remember(bill) { mutableStateOf(bill.merchant) }
    var occurredAt by remember(bill) { mutableStateOf(bill.occurredAt) }
    var showDuplicateConfirm by remember(bill) { mutableStateOf(false) }

    val options = categories.filter { it.type == type }
    LaunchedEffect(options, categoryId) {
        if (options.none { it.id == categoryId }) {
            categoryId = options.firstOrNull()?.id
        }
    }

    val amount = parseAmountToMinor(amountText)
    val canSave = amount > 0 && categoryId != null
    val candidate = bill.copy(
        amountMinor = amount,
        type = type,
        merchant = note.trim(),
        occurredAt = occurredAt,
    )
    val save = { onSave(type, amount, categoryId, note.trim(), occurredAt) }

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
            Text("确认账单", style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TxType.EXPENSE,
                    onClick = { type = TxType.EXPENSE },
                    label = { Text("支出") },
                )
                FilterChip(
                    selected = type == TxType.INCOME,
                    onClick = { type = TxType.INCOME },
                    label = { Text("收入") },
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("金额") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("分类", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options, key = { it.id }) { category ->
                    FilterChip(
                        selected = category.id == categoryId,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "时间 " + occurredAt.toLocalDateTime()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fen.colors.inkSoft,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { occurredAt = System.currentTimeMillis() }) {
                    Text("改为现在")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("忽略") }

                Button(
                    onClick = {
                        if (DuplicateDetector.find(candidate, existing) != null) {
                            showDuplicateConfirm = true
                        } else {
                            save()
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }

            if (bill.sourceId != null) {
                val page = bill.sourcePage?.let { " / $it" } ?: ""
                Text(
                    text = "来源：${bill.sourceId}$page",
                    style = MaterialTheme.typography.bodySmall,
                    color = Fen.colors.inkFaint,
                )
            }
        }
    }

    if (showDuplicateConfirm) {
        AlertDialog(
            onDismissRequest = { showDuplicateConfirm = false },
            title = { Text("可能重复") },
            text = { Text("已有一笔金额和时间相近的记录，仍要保存吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDuplicateConfirm = false
                        save()
                    }
                ) { Text("继续保存") }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateConfirm = false }) { Text("取消") }
            },
        )
    }
}
