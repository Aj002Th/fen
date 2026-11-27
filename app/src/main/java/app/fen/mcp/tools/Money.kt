package app.fen.mcp.tools

import java.math.BigDecimal

object Money {

    private val pattern = Regex("""^\d{1,9}(\.\d{1,2})?$""")
    private const val MAX_MINOR = 100_000_000L // ¥1,000,000

    /** 严格解析元字符串；拒绝分组、负号、指数、超过两位小数和 0。 */
    fun parseYuanToMinor(input: String): Long? {
        if (!pattern.matches(input)) return null
        val parts = input.split(".")
        val yuan = parts[0].toLongOrNull() ?: return null
        val cents = parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0
        val minor = yuan * 100 + cents
        return minor.takeIf { it in 1..MAX_MINOR }
    }

    /** 3200 -> "32.00" */
    fun formatPlain(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
}
