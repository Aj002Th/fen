package app.fen.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fen.data.Category
import app.fen.data.Snapshot
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.data.toDate
import app.fen.ui.components.AmountKeypad
import app.fen.ui.components.CircleAction
import app.fen.ui.components.Segmented
import app.fen.ui.icons.categoryIcon
import app.fen.ui.theme.Fen
import app.fen.util.label
import app.fen.util.parseAmountToMinor
import app.fen.util.toAmountInput
import java.time.LocalDate

@Composable
fun AddTransactionScreen(
    snapshot: Snapshot,
    initial: Tx? = null,
    onClose: () -> Unit,
    onSave: (type: TxType, amountMinor: Long, categoryId: String?, note: String, date: LocalDate) -> Unit,
) {
    val initialCategory = remember(snapshot.categories, initial) {
        initial?.categoryId?.let { id -> snapshot.categories.firstOrNull { it.id == id } }
    }
    var type by remember(initial) { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var amountText by remember(initial) { mutableStateOf(initial?.amount?.toAmountInput() ?: "") }
    var amountTouched by remember(initial) { mutableStateOf(false) }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var date by remember(initial) { mutableStateOf(initial?.occurredAt?.toDate() ?: LocalDate.now()) }
    var parentId by remember(initial) { mutableStateOf(initialCategory?.parentId) }
    var childId by remember(initial) {
        mutableStateOf(if (initialCategory?.parentId != null) initialCategory.id else null)
    }

    val accent = if (type == TxType.EXPENSE) Fen.colors.expense else Fen.colors.income
    val parents = remember(snapshot.categories, type) {
        snapshot.categories.filter { it.type == type && it.isParent }.sortedBy { it.sortOrder }
    }
    val effectiveParent = parents.firstOrNull { it.id == parentId } ?: parents.firstOrNull()
    val children = remember(snapshot.categories, effectiveParent) {
        snapshot.categories.filter { it.parentId != null && it.parentId == effectiveParent?.id }
            .sortedBy { it.sortOrder }
    }
    val effectiveChild = children.firstOrNull { it.id == childId }
    val selectedCategoryId = effectiveChild?.id ?: effectiveParent?.id

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

    fun submit() {
        val category = selectedCategoryId ?: return
        onSave(type, amountMinor, category, note, date)
    }

    Surface(color = Fen.colors.bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
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
            }

            Segmented(
                options = listOf("支出", "收入"),
                selected = if (type == TxType.EXPENSE) 0 else 1,
                onSelect = { index ->
                    val next = if (index == 0) TxType.EXPENSE else TxType.INCOME
                    if (next != type) {
                        type = next
                        parentId = null
                        childId = null
                    }
                },
                accent = accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(6.dp))

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    parents.chunked(5).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { category ->
                                Box(Modifier.weight(1f)) {
                                    CategoryTile(
                                        category = category,
                                        selected = category.id == effectiveParent?.id,
                                        accent = accent,
                                        onClick = {
                                            parentId = category.id
                                            childId = null
                                        },
                                    )
                                }
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                if (children.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Fen.colors.surfaceMuted,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            children.forEach { child ->
                                val active = child.id == selectedCategoryId
                                Surface(
                                    onClick = { childId = child.id },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (active) Fen.colors.surface else Color.Transparent,
                                    shadowElevation = if (active) 1.dp else 0.dp,
                                ) {
                                    Text(
                                        text = child.name,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (active) Fen.colors.ink else Fen.colors.inkSoft,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "日期",
                            style = MaterialTheme.typography.labelMedium,
                            color = Fen.colors.inkSoft,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        DateChip(date = date, onPick = { date = it })
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "备注",
                            style = MaterialTheme.typography.labelMedium,
                            color = Fen.colors.inkSoft,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        BasicTextField(
                            value = note,
                            onValueChange = { note = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Fen.colors.ink),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (note.isEmpty()) {
                                    Text(
                                        "点击添加备注",
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "¥",
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 6.dp, bottom = 8.dp),
                )
                Text(
                    text = amountText.ifEmpty { "0.00" },
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
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

            AmountKeypad(
                onDigit = ::key,
                onDot = { key(".") },
                onBackspace = {
                    amountTouched = true
                    amountText = amountText.dropLast(1)
                },
                onRecord = {
                    submit()
                    amountText = ""
                    note = ""
                },
                onDone = {
                    submit()
                },
                canSave = canSave,
                accent = accent,
                showRecord = initial == null,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val color = Color(category.color)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (selected) color else color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(category.icon),
                contentDescription = null,
                tint = if (selected) Color.White else color,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
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
private fun DateChip(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            ).show()
        },
        shape = RoundedCornerShape(9.dp),
        color = Fen.colors.surfaceMuted,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.CalendarToday,
                contentDescription = null,
                tint = Fen.colors.inkSoft,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (date == LocalDate.now()) "今天" else date.label(),
                style = MaterialTheme.typography.labelMedium,
                color = Fen.colors.ink,
            )
        }
    }
}
