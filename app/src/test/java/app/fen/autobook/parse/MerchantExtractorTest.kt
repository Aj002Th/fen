package app.fen.autobook.parse

import app.fen.autobook.model.BoundingBox
import app.fen.autobook.model.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantExtractorTest {
    private fun line(text: String, height: Int = 20) =
        OcrLine(text, BoundingBox(0, 0, 300, height), null)

    @Test
    fun `prefers payee anchor`() {
        val lines = listOf(
            line("微信支付"),
            line("¥32.50", 60),
            line("收款方 某某餐厅"),
            line("支付时间 2026-11-23 12:30"),
        )

        assertEquals("某某餐厅", MerchantExtractor.extract(lines))
    }

    @Test
    fun `falls back to largest non amount line`() {
        val lines = listOf(
            line("某某超市", 40),
            line("¥10.00", 80),
        )

        assertEquals("某某超市", MerchantExtractor.extract(lines))
    }

    @Test
    fun `returns blank when nothing usable`() {
        val lines = listOf(
            line("¥10.00"),
            line("2026-11-23 12:30"),
        )

        assertEquals("", MerchantExtractor.extract(lines))
    }

    @Test
    fun `prefers merchant over app title`() {
        val lines = listOf(
            line("微信支付", 40),
            line("测试餐厅", 20),
        )

        assertEquals("测试餐厅", MerchantExtractor.extract(lines))
    }

    @Test
    fun `keeps merchant names containing digits`() {
        val lines = listOf(
            line("星巴克 2 店", 40),
            line("¥10.00", 80),
        )

        assertEquals("星巴克 2 店", MerchantExtractor.extract(lines))
    }

    @Test
    fun `strips merchant label without space`() {
        val lines = listOf(line("商户全称测试餐厅", 40))

        assertEquals("测试餐厅", MerchantExtractor.extract(lines))
    }

    @Test
    fun `strips traditional merchant label`() {
        val lines = listOf(line("商戶全稱测试餐厅", 40))

        assertEquals("测试餐厅", MerchantExtractor.extract(lines))
    }

    @Test
    fun `skips merchant id lines`() {
        val lines = listOf(
            line("商户单号 4200001234", 40),
            line("测试餐厅", 20),
        )

        assertEquals("测试餐厅", MerchantExtractor.extract(lines))
    }
}
