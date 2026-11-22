package app.fen.autobook.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {
    @Test
    fun `parses currency symbol and decimals`() {
        assertEquals(1234L, AmountParser.parse("¥12.34"))
    }

    @Test
    fun `parses yuan suffix`() {
        assertEquals(1234L, AmountParser.parse("12.34元"))
    }

    @Test
    fun `parses thousands separator`() {
        assertEquals(123456L, AmountParser.parse("1,234.56"))
    }

    @Test
    fun `parses integer amount`() {
        assertEquals(1200L, AmountParser.parse("实付 12"))
    }

    @Test
    fun `parses one cent`() {
        assertEquals(1L, AmountParser.parse("0.01"))
    }

    @Test
    fun `ignores leading minus`() {
        assertEquals(1200L, AmountParser.parse("-12.00"))
    }

    @Test
    fun `rounds three decimal places`() {
        assertEquals(1235L, AmountParser.parse("12.345"))
    }

    @Test
    fun `rejects order numbers`() {
        assertNull(AmountParser.parse("订单号 202311230001"))
    }

    @Test
    fun `rejects card tail numbers`() {
        assertNull(AmountParser.parse("尾号1234"))
    }
}
