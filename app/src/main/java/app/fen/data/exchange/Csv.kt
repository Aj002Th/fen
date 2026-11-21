package app.fen.data.exchange

/**
 * 极简 RFC 4180 CSV 读写。
 * 支持引号内的逗号/换行、`""` 转义、CRLF、UTF-8 BOM，以及简单的分隔符嗅探。
 */
object Csv {

    fun parse(text: String, delimiter: Char? = null): Table {
        val clean = text.removePrefix("\uFEFF")
        val sep = delimiter ?: sniffDelimiter(clean)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < clean.length) {
            val ch = clean[index]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (index + 1 < clean.length && clean[index + 1] == '"') {
                            field.append('"')
                            index++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == sep -> {
                    row.add(field.toString())
                    field.setLength(0)
                }
                ch == '\r' -> Unit
                ch == '\n' -> {
                    row.add(field.toString())
                    field.setLength(0)
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return Table(rows)
    }

    fun write(rows: List<List<String>>, delimiter: Char = ','): String {
        val text = rows.joinToString("\r\n") { row ->
            row.joinToString(delimiter.toString()) { escape(it, delimiter) }
        }
        return text + "\r\n"
    }

    /** Excel 打开中文 CSV 时最稳妥：UTF-8 BOM + CRLF。 */
    fun writeForExcel(rows: List<List<String>>): String = "\uFEFF" + write(rows)

    fun escape(value: String, delimiter: Char = ','): String {
        val needsQuote = value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun sniffDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val candidates = listOf(',', ';', '\t')
        var best = ','
        var bestCount = 0
        for (candidate in candidates) {
            val count = firstLine.count { it == candidate }
            if (count > bestCount) {
                best = candidate
                bestCount = count
            }
        }
        return best
    }
}
