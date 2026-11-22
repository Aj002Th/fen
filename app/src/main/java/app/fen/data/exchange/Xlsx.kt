package app.fen.data.exchange

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * 最小 XLSX 读取器：不依赖 Apache POI。
 *
 * 只做导入需要的事：读第一个工作表、共享字符串、内联字符串和数字。
 * 不处理样式/公式/图片；日期样式会以 Excel 序列号返回，由 [parseDateTimeToMillis] 兜底。
 *
 * iCost 导出的 `<dimension ref="A1"/>` 是错的，所以这里完全按 `<row>` / `<c r="...">` 遍历。
 */
object Xlsx {

    private const val MAX_ROWS = 200_000
    private const val MAX_COLS = 200
    private const val MAX_ENTRY_BYTES = 80L * 1024 * 1024

    fun read(bytes: ByteArray): Table? {
        var shared = emptyList<String>()
        val sheets = mutableListOf<ByteArray>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.size > MAX_ENTRY_BYTES) continue
                when {
                    name == "xl/sharedStrings.xml" -> shared = parseSharedStrings(zip.readBytes())
                    name.startsWith("xl/worksheets/") && name.endsWith(".xml") ->
                        sheets.add(zip.readBytes())
                }
            }
        }
        if (sheets.isEmpty()) return null
        return Table(parseSheet(sheets.first(), shared))
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val handler = object : DefaultHandler() {
            val list = ArrayList<String>()
            private val current = StringBuilder()
            private var inText = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                if (tag(qName) == "t") inText = true
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) current.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (tag(qName)) {
                    "t" -> inText = false
                    "si" -> {
                        list.add(current.toString())
                        current.setLength(0)
                    }
                }
            }
        }
        runCatching { parseXml(bytes, handler) }
        return handler.list
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val handler = object : DefaultHandler() {
            private var row: MutableList<String>? = null
            private var nextColumn = 0
            private var column = 0
            private var cellType: String? = null
            private var inValue = false
            private var inInlineText = false
            private val buffer = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (tag(qName)) {
                    "row" -> {
                        row = ArrayList()
                        nextColumn = 0
                    }
                    "c" -> {
                        column = columnIndex(attributes?.getValue("r")) ?: nextColumn
                        nextColumn = column + 1
                        cellType = attributes?.getValue("t")
                        buffer.setLength(0)
                        inValue = false
                        inInlineText = false
                    }
                    "v" -> inValue = true
                    "t" -> inInlineText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue || inInlineText) buffer.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (tag(qName)) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val current = row ?: return
                        if (rows.size < MAX_ROWS) {
                            val text = when (cellType) {
                                "s" -> buffer.toString().trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                                "inlineStr", "str" -> buffer.toString()
                                "b" -> if (buffer.toString().trim() == "1") "TRUE" else "FALSE"
                                else -> buffer.toString()
                            }
                            while (current.size < column && current.size < MAX_COLS) current.add("")
                            if (current.size < MAX_COLS) {
                                if (current.size == column) current.add(text) else current[column] = text
                            }
                        }
                        buffer.setLength(0)
                        cellType = null
                    }
                    "row" -> {
                        val current = row
                        if (current != null && rows.size < MAX_ROWS) rows.add(current)
                        row = null
                    }
                }
            }
        }
        runCatching { parseXml(bytes, handler) }
        return rows
    }

    private fun parseXml(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.isNamespaceAware = false
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }

    private fun tag(qName: String?): String = qName?.substringAfterLast(':') ?: ""

    /** "AB12" → 27（0-based） */
    private fun columnIndex(ref: String?): Int? {
        if (ref.isNullOrEmpty()) return null
        var value = 0
        var found = false
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                value = value * 26 + (ch - 'A' + 1)
                found = true
            } else if (ch in 'a'..'z') {
                value = value * 26 + (ch - 'a' + 1)
                found = true
            } else {
                break
            }
        }
        return if (found) value - 1 else null
    }
}
