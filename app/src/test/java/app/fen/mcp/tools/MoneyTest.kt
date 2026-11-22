package app.fen.mcp.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun parsesValidYuan() {
        assertEquals(3_200L, Money.parseYuanToMinor("32"))
        assertEquals(3_250L, Money.parseYuanToMinor("32.5"))
        assertEquals(3_250L, Money.parseYuanToMinor("32.50"))
        assertEquals(100_000_000L, Money.parseYuanToMinor("1000000"))
    }

    @Test
    fun rejectsInvalidAmounts() {
        listOf("1,234", "-1", "0", "1.234", "1e3", "", " 1", "+1", "1000000000", "00.00").forEach {
            assertNull("should reject [$it]", Money.parseYuanToMinor(it))
        }
    }

    @Test
    fun formatsPlainTwoDecimals() {
        assertEquals("32.00", Money.formatPlain(3_200))
        assertEquals("0.05", Money.formatPlain(5))
        assertEquals("1234.56", Money.formatPlain(123_456))
    }
}
