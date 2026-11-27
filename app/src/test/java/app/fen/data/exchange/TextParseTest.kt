package app.fen.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TextParseTest {

    @Test
    fun moneyVariants() {
        assertEquals(2100L, parseMoneyToMinor("21"))
        assertEquals(-2100L, parseMoneyToMinor("-21"))
        assertEquals(123450L, parseMoneyToMinor("1,234.5"))
        assertEquals(1234L, parseMoneyToMinor("¥12.34"))
        assertEquals(1234L, parseMoneyToMinor("12.34元"))
        assertEquals(50L, parseMoneyToMinor(".5"))
        assertEquals(-1234L, parseMoneyToMinor("(12.34)"))
        assertEquals(0L, parseMoneyToMinor("0"))
        assertEquals(1299L, parseMoneyToMinor("12.999"))
        assertNull(parseMoneyToMinor("abc"))
        assertNull(parseMoneyToMinor(""))
        assertNull(parseMoneyToMinor("1.2.3"))
    }

    @Test
    fun plainText() {
        assertEquals("21.00", minorToPlainText(2100))
        assertEquals("0.05", minorToPlainText(5))
    }

    @Test
    fun dates() {
        val zone = ZoneId.of("Asia/Shanghai")
        fun text(raw: String): LocalDateTime =
            java.time.Instant.ofEpochMilli(parseDateTimeToMillis(raw, zone)!!).atZone(zone).toLocalDateTime()

        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 6), text("2024/01/01 00:06"))
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 6), text("2024/1/1 0:06"))
        assertEquals(LocalDateTime.of(2011, 1, 11, 12, 0), text("2011年01月11日 12:00:00"))
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 6), text("2024-01-01 00:06"))
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 6, 5), text("2024-01-01T00:06:05"))
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0), text("2024/01/01"))
        // Excel 序列号：45292 = 2024-01-01
        assertEquals(LocalDateTime.of(2024, 1, 1, 12, 0), text("45292.5"))
        assertNull(parseDateTimeToMillis("不是日期", zone))
        assertNull(parseDateTimeToMillis("", zone))
    }
}
