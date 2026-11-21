package app.fen.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CsvAndXlsxTest {

    @Test
    fun csvQuotedFields() {
        val table = Csv.parse("日期,备注\r\n2024/01/01 10:00,\"午饭, 咖啡\"\r\n2024/01/02 11:00,\"他说\"\"好\"\"\"\r\n")
        assertEquals(3, table.rows.size)
        assertEquals(listOf("日期", "备注"), table.rows[0])
        assertEquals("午饭, 咖啡", table.rows[1][1])
        assertEquals("他说\"好\"", table.rows[2][1])
    }

    @Test
    fun csvNewlineInQuotesAndSemicolonSniff() {
        val table = Csv.parse("a;b\n1;\"x\ny\"\n")
        assertEquals(2, table.rows.size)
        assertEquals("x\ny", table.rows[1][1])
    }

    @Test
    fun csvRoundTrip() {
        val rows = listOf(
            listOf("日期", "备注"),
            listOf("2024/01/01 10:00", "a,b\"c\nd"),
        )
        val text = Csv.write(rows)
        assertEquals(rows, Csv.parse(text).rows)
    }

    @Test
    fun xlsxSharedStringsAndNumeric() {
        val bytes = buildXlsx()
        val table = Xlsx.read(bytes)
        assertNotNull(table)
        val rows = table!!.rows
        assertEquals(listOf("日期", "金额", "备注"), rows[0])
        assertEquals("2024/01/01 00:06", rows[1][0])
        assertEquals("123.45", rows[1][1])
        assertEquals("测试", rows[1][2])
        // 第 3 行用 inlineStr，且跳过 B 列
        assertEquals("内联", rows[2][0])
        assertEquals("", rows[2][1])
        assertEquals("文本", rows[2][2])
    }

    @Test
    fun csvWithBom() {
        val table = Csv.parse("\uFEFF日期,金额\n2024/01/01,1\n")
        assertEquals("日期", table.rows[0][0])
    }

    private fun buildXlsx(): ByteArray {
        val shared = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="5" uniqueCount="5">
              <si><t>日期</t></si>
              <si><t>金额</t></si>
              <si><t>备注</t></si>
              <si><t>2024/01/01 00:06</t></si>
              <si><t xml:space="preserve">测试</t></si>
            </sst>
        """.trimIndent()
        val sheet = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <dimension ref="A1"/>
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                  <c r="C1" t="s"><v>2</v></c>
                </row>
                <row r="2">
                  <c r="A2" t="s"><v>3</v></c>
                  <c r="B2"><v>123.45</v></c>
                  <c r="C2" t="s"><v>4</v></c>
                </row>
                <row r="3">
                  <c r="A3" t="inlineStr"><is><t>内联</t></is></c>
                  <c r="C3" t="inlineStr"><is><t>文本</t></is></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add("xl/sharedStrings.xml", shared)
            add("xl/worksheets/sheet1.xml", sheet)
        }
        assertTrue(out.size() > 0)
        return out.toByteArray()
    }
}
