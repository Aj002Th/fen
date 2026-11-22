package app.fen.autobook.source

import app.fen.autobook.model.BoundingBox
import app.fen.autobook.model.OcrLine
import app.fen.autobook.parse.GenericBillParser
import app.fen.autobook.pipeline.RecognitionEngine
import app.fen.autobook.source.recognizers.AlipayRecognizer
import app.fen.autobook.source.recognizers.GenericRecognizer
import app.fen.autobook.source.recognizers.TaobaoRecognizer
import app.fen.autobook.source.recognizers.UnionpayRecognizer
import app.fen.autobook.source.recognizers.WechatRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SourceTemplateTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 11, 23, 15, 30)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private val engine = RecognitionEngine(
        SourceDetection(
            listOf(
                WechatRecognizer,
                AlipayRecognizer,
                UnionpayRecognizer,
                TaobaoRecognizer,
                GenericRecognizer,
            )
        ),
        GenericBillParser(),
    )

    private fun line(text: String, height: Int = 20) =
        OcrLine(text, BoundingBox(0, 0, 300, height), null)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `wechat template ignores balance and order number`() {
        val bill = engine.recognize(
            listOf(
                line("微信支付", 40),
                line("订单号 202311230001"),
                line("¥32.50", 60),
                line("余额 ¥1000.00", 50),
                line("收款方 某某餐厅"),
                line("支付时间 2026-11-23 12:30"),
            ),
            now,
            zone,
        )

        assertEquals(3250L, bill.amountMinor)
        assertEquals("某某餐厅", bill.merchant)
        assertEquals(at(2026, 11, 23, 12, 30), bill.occurredAt)
    }

    @Test
    fun `alipay template ignores discount and balance`() {
        val bill = engine.recognize(
            listOf(
                line("支付宝", 40),
                line("优惠 ¥5.00"),
                line("金额 ¥32.50", 60),
                line("余额 ¥1000.00", 50),
                line("收款方 某某餐厅"),
                line("创建时间 2026-11-23 12:30"),
            ),
            now,
            zone,
        )

        assertEquals(3250L, bill.amountMinor)
        assertEquals("某某餐厅", bill.merchant)
    }

    @Test
    fun `unionpay template ignores card number`() {
        val bill = engine.recognize(
            listOf(
                line("云闪付", 40),
                line("交易详情"),
                line("交易金额 ¥32.50", 60),
                line("卡号 6222 0000 0000 0000"),
                line("商户名称 某某餐厅"),
                line("交易时间 2026-11-23 12:30"),
            ),
            now,
            zone,
        )

        assertEquals(3250L, bill.amountMinor)
        assertEquals("某某餐厅", bill.merchant)
    }

    @Test
    fun `other source keeps generic extraction`() {
        val bill = engine.recognize(
            listOf(
                line("淘宝", 40),
                line("订单详情"),
                line("实付 ¥32.50", 60),
            ),
            now,
            zone,
        )

        assertEquals("taobao", bill.sourceId)
        assertEquals(3250L, bill.amountMinor)
    }
}
