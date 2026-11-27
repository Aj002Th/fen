package app.fen.autobook.parse

import java.math.BigDecimal
import java.math.RoundingMode

object AmountParser {
    private val number = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val dateOnly = Regex("""^\d{4}[-/]\d{1,2}[-/]\d{1,2}$""")
    private val timeOnly = Regex("""^\d{1,2}:\d{2}(?::\d{2})?$""")
    private val dateLike = listOf(
        Regex("""\d{4}年\d{1,2}月\d{1,2}日"""),
        Regex("""\d{1,2}月\d{1,2}日"""),
    )
    private val exclusions = listOf("订单号", "尾号", "卡号", "时间", "日期", "电话", "编号")
    private const val MAX_MINOR = 100_000_000_000L

    fun parse(text: String): Long? {
        val segments = TextNormalizer.normalize(text).split(' ').filter { it.isNotBlank() }
        for ((index, segment) in segments.withIndex()) {
            if (dateOnly.matches(segment) || timeOnly.matches(segment)) continue
            if (dateLike.any { it.containsMatchIn(segment) }) continue
            val previous = segments.getOrNull(index - 1).orEmpty()
            if (exclusions.any { previous.contains(it) } || exclusions.any { segment.contains(it) }) continue
            for (match in number.findAll(segment)) {
                val after = segment.substring(match.range.last + 1)
                if (after.startsWith("-") || after.startsWith(":") || after.startsWith("/")) continue
                val value = BigDecimal(match.value.replace(",", ""))
                    .setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .toLong()
                if (value in 1..MAX_MINOR) return value
            }
        }
        return null
    }
}
