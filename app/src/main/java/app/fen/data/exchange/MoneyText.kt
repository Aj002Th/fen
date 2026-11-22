package app.fen.data.exchange

/**
 * 金额文本 → 「分」。
 *
 * 兼容 iCost / 银行 / Excel 常见写法：`-21`、`1,234.5`、`¥ 12.34`、`12.34元`、
 * `(12.34)`（会计负数）、全角逗号、不换行空格。无法解析时返回 null。
 *
 * 小数超过 2 位时截断（不四舍五入），与 Fen 的「分」精度保持一致。
 */
fun parseMoneyToMinor(raw: String): Long? {
    var text = raw.trim()
    if (text.isEmpty()) return null
    text = text
        .replace("\u00A0", "")
        .replace("\u3000", "")
        .replace(" ", "")
        .replace(",", "")
        .replace("，", "")
        .replace("￥", "")
        .replace("¥", "")
        .replace("元", "")
    if (text.isEmpty()) return null

    var negative = false
    if (text.startsWith("(") && text.endsWith(")") && text.length > 2) {
        negative = true
        text = text.substring(1, text.length - 1)
    }
    if (text.startsWith("-") || text.startsWith("−")) {
        negative = !negative
        text = text.substring(1)
    } else if (text.startsWith("+")) {
        text = text.substring(1)
    }
    if (text.isEmpty()) return null

    val parts = text.split(".")
    if (parts.size > 2) return null
    val integerPart = parts[0]
    val fractionPart = parts.getOrNull(1) ?: ""
    if (integerPart.isEmpty() && fractionPart.isEmpty()) return null
    if (!integerPart.all { it.isDigit() } || !fractionPart.all { it.isDigit() }) return null

    val yuan = if (integerPart.isEmpty()) 0L else integerPart.toLongOrNull() ?: return null
    val cents = fractionPart.padEnd(2, '0').take(2).ifEmpty { "0" }.toLongOrNull() ?: return null
    val minor = yuan * 100 + cents
    return if (negative) -minor else minor
}

/** 分 → 无符号两位小数字符串，如 2100 → "21.00"。 */
fun minorToPlainText(minor: Long): String {
    val abs = kotlin.math.abs(minor)
    return "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
