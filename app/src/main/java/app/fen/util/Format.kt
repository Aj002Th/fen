package app.fen.util

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val symbols = DecimalFormatSymbols(Locale.CHINA)

private fun decimal(pattern: String) = DecimalFormat(pattern, symbols).apply {
    roundingMode = RoundingMode.HALF_UP
}

/** 123456 -> "1,234.56" */
fun formatMoney(minor: Long): String = decimal("#,##0.00").format(minor / 100.0)

/** 123456 -> "1,234.56"，带正负号 */
fun formatSignedMoney(minor: Long): String {
    val text = decimal("#,##0.00").format(kotlin.math.abs(minor) / 100.0)
    return when {
        minor > 0 -> "+$text"
        minor < 0 -> "-$text"
        else -> text
    }
}

/** "12.34" / "12." / ".5" -> 分 */
fun parseAmountToMinor(input: String): Long {
    if (input.isBlank()) return 0
    val cleaned = input.replace(",", "").trim()
    val parts = cleaned.split(".")
    val yuan = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0
    val centsText = (parts.getOrNull(1) ?: "").padEnd(2, '0').take(2)
    val cents = centsText.toLongOrNull() ?: 0
    return yuan * 100 + cents
}

fun YearMonth.label(): String = "${year}年${monthValue}月"

fun LocalDate.label(): String {
    val week = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
    return "${monthValue}月${dayOfMonth}日 $week"
}

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun LocalDate.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

fun Long.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

fun Long.toMonthKey(zone: ZoneId = ZoneId.systemDefault()): YearMonth =
    YearMonth.from(toLocalDateTime(zone))

fun LocalDate.shortDate(): String =
    format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
