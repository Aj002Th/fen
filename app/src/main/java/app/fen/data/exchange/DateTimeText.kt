package app.fen.data.exchange

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * 日期文本 → 本地时区的毫秒。
 *
 * 兼容：
 * - iCost 导出：`2024/01/01 00:06`
 * - iCost 模板：`2011年01月11日 12:00:00`
 * - 通用：`2024-01-01 00:06`、`2024-01-01T00:06:00`、只有日期
 * - Excel 日期序列号（数字单元格）
 */
fun parseDateTimeToMillis(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
    var text = raw.trim()
    if (text.isEmpty()) return null
    text = text
        .replace('／', '/')
        .replace('－', '-')
        .replace('\u00A0', ' ')
        .replace('\u3000', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    // Excel 序列号：1900 日期系统（1899-12-30 起算）
    val numeric = text.toDoubleOrNull()
    if (numeric != null && numeric > 20_000 && numeric < 80_000) {
        val days = numeric.toLong()
        val fraction = numeric - days
        val date = LocalDate.of(1899, 12, 30).plusDays(days)
        val seconds = (fraction * 86_400.0).toLong().coerceIn(0, 86_399)
        return LocalDateTime.of(date, java.time.LocalTime.ofSecondOfDay(seconds))
            .atZone(zone).toInstant().toEpochMilli()
    }

    for (formatter in dateTimeFormatters) {
        runCatching { LocalDateTime.parse(text, formatter) }
            .getOrNull()
            ?.let { return it.atZone(zone).toInstant().toEpochMilli() }
    }
    for (formatter in dateFormatters) {
        runCatching { LocalDate.parse(text, formatter) }
            .getOrNull()
            ?.let { return it.atStartOfDay(zone).toInstant().toEpochMilli() }
    }
    return null
}

private fun formatter(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, Locale.CHINA).withResolverStyle(ResolverStyle.SMART)

private val dateTimeFormatters = listOf(
    formatter("uuuu/MM/dd HH:mm"),
    formatter("uuuu/M/d H:mm"),
    formatter("uuuu/MM/dd HH:mm:ss"),
    formatter("uuuu/M/d H:mm:ss"),
    formatter("uuuu-MM-dd HH:mm"),
    formatter("uuuu-M-d H:mm"),
    formatter("uuuu-MM-dd HH:mm:ss"),
    formatter("uuuu-M-d H:mm:ss"),
    formatter("uuuu-MM-dd'T'HH:mm"),
    formatter("uuuu-MM-dd'T'HH:mm:ss"),
    formatter("uuuu年MM月dd日 HH:mm:ss"),
    formatter("uuuu年M月d日 H:mm:ss"),
    formatter("uuuu年MM月dd日 HH:mm"),
    formatter("uuuu年M月d日 H:mm"),
    formatter("uuuu年MM月dd日"),
    formatter("uuuu年M月d日"),
)

private val dateFormatters = listOf(
    formatter("uuuu/MM/dd"),
    formatter("uuuu/M/d"),
    formatter("uuuu-MM-dd"),
    formatter("uuuu-M-d"),
)
