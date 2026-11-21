package app.fen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney

/* ---------------------------------- 金额 ---------------------------------- */

@Composable
fun AmountText(
    amount: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    color: Color = Fen.colors.ink,
    prefix: String? = "¥",
    signed: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        if (prefix != null) {
            Text(
                text = prefix,
                style = MaterialTheme.typography.headlineSmall,
                color = color.copy(alpha = 0.45f),
                modifier = Modifier.padding(end = 4.dp, bottom = 5.dp),
            )
        }
        Text(
            text = if (signed) signedText(amount) else formatMoney(amount),
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun signedText(amount: Long): String =
    if (amount > 0) "+${formatMoney(amount)}" else formatMoney(amount)

/* ---------------------------------- 图标 ---------------------------------- */

@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun CircleAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = Fen.colors.surface,
    tint: Color = Fen.colors.inkSoft,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/* ---------------------------------- 标签 ---------------------------------- */

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Fen.colors.inkSoft,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier, startInset: Dp = 66.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(1.dp)
            .background(Fen.colors.divider)
    )
}

/* ---------------------------------- 页头 ---------------------------------- */

@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = Fen.colors.ink,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun MonthBar(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleAction(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = "上一月",
            onClick = onPrev,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        CircleAction(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "下一月",
            onClick = onNext,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索备注、分类或金额",
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    imeAction: androidx.compose.ui.text.input.ImeAction =
        androidx.compose.ui.text.input.ImeAction.Done,
    onSearch: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Fen.colors.surfaceMuted,
        modifier = modifier.fillMaxWidth().height(44.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = Fen.colors.inkSoft,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Fen.colors.ink),
                cursorBrush = SolidColor(Fen.colors.ink),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = imeAction),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch() },
                    onDone = { onSearch() },
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    ),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Fen.colors.inkFaint,
                        )
                    }
                    inner()
                },
            )
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "清空搜索",
                    tint = Fen.colors.inkFaint,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onValueChange("") },
                )
            }
        }
    }
}

/* ---------------------------------- 空态 ---------------------------------- */

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Fen.colors.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text("🌱", fontSize = 28.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Fen.colors.ink)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Fen.colors.inkSoft)
    }
}

/* ---------------------------------- 分段 ---------------------------------- */

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Fen.colors.ink,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Fen.colors.surfaceMuted)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) Fen.colors.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) accent else Fen.colors.inkSoft,
                )
            }
        }
    }
}

/* ---------------------------------- 键盘 ---------------------------------- */

@Composable
fun AmountKeypad(
    onDigit: (String) -> Unit,
    onDot: () -> Unit,
    onBackspace: () -> Unit,
    onRecord: () -> Unit,
    onDone: () -> Unit,
    canSave: Boolean,
    modifier: Modifier = Modifier,
    showRecord: Boolean = true,
    recordLabel: String = "保存再记",
    doneLabel: String = "完成",
    accent: Color = Fen.colors.expense,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeyCell(key, Modifier.weight(1f)) { onDigit(key) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyCell(".", Modifier.weight(1f)) { onDot() }
            KeyCell("0", Modifier.weight(1f)) { onDigit("0") }
            KeyCell("⌫", Modifier.weight(1f)) { onBackspace() }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showRecord) {
                Surface(
                    onClick = onRecord,
                    enabled = canSave,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Fen.colors.surfaceMuted,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = recordLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (canSave) Fen.colors.ink else Fen.colors.inkFaint,
                        )
                    }
                }
            }
            Surface(
                onClick = onDone,
                enabled = canSave,
                modifier = Modifier.weight(if (showRecord) 1.6f else 1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (canSave) accent else Fen.colors.surfaceMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = doneLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (canSave) Color.White else Fen.colors.inkFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyCell(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color = Fen.colors.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Normal,
                color = Fen.colors.ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}
