package app.fen.autobook.parse

object TextNormalizer {
    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val c = when (ch) {
                in '０'..'９' -> '0' + (ch - '０')
                '．' -> '.'
                '，' -> ','
                '￥' -> '¥'
                '：' -> ':'
                '－' -> '-'
                '\u00A0', '\u3000' -> ' '
                else -> ch
            }
            sb.append(c)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}
