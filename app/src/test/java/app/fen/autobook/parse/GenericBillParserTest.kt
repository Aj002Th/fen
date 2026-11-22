package app.fen.autobook.parse

import app.fen.autobook.model.BoundingBox
import app.fen.autobook.model.OcrLine
import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GenericBillParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 11, 23, 15, 30)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun line(text: String, height: Int = 20) =
        OcrLine(text, BoundingBox(0, 0, 300, height), null)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `parses fields from shuffled lines`() {
        val lines = listOf(
            line("订单号 202311230001"),
            line("收款方 某某餐厅"),
            line("¥32.50", 60),
            line("支付时间 2026-11-23 12:30"),
            line("微信支付"),
        )

        val bill = GenericBillParser().parse(lines, now, zone)

        assertEquals(3250L, bill.amountMinor)
        assertEquals(TxType.EXPENSE, bill.type)
        assertEquals(at(2026, 11, 23, 12, 30), bill.occurredAt)
        assertEquals("某某餐厅", bill.merchant)
        assertTrue(bill.confidence > 0.6f)
    }

    @Test
    fun `falls back to now and blank merchant`() {
        val bill = GenericBillParser().parse(listOf(line("¥10.00")), now, zone)

        assertEquals(1000L, bill.amountMinor)
        assertEquals(now, bill.occurredAt)
        assertEquals("", bill.merchant)
        assertTrue(bill.confidence < 0.7f)
    }
}
