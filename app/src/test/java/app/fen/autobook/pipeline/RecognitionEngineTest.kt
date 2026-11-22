package app.fen.autobook.pipeline

import app.fen.autobook.model.BoundingBox
import app.fen.autobook.model.ExtractorMode
import app.fen.autobook.model.OcrLine
import app.fen.autobook.parse.GenericBillParser
import app.fen.autobook.source.SourceDetection
import app.fen.autobook.source.recognizers.AlipayRecognizer
import app.fen.autobook.source.recognizers.GenericRecognizer
import app.fen.autobook.source.recognizers.UnionpayRecognizer
import app.fen.autobook.source.recognizers.WechatRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecognitionEngineTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 11, 23, 15, 30)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private val engine = RecognitionEngine(
        SourceDetection(
            listOf(WechatRecognizer, AlipayRecognizer, UnionpayRecognizer, GenericRecognizer)
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
    fun `recognizes wechat payment`() {
        val bill = engine.recognize(
            listOf(
                line("微信支付", 40),
                line("支付成功", 30),
                line("¥32.50", 60),
                line("商户全称 某某餐厅"),
                line("支付时间 2026-11-23 12:30"),
            ),
            now,
            zone,
        )

        assertEquals("wechat", bill.sourceId)
        assertEquals("payment_done", bill.sourcePage)
        assertEquals(ExtractorMode.IMAGE, bill.extractor)
        assertEquals(3250L, bill.amountMinor)
        assertEquals(at(2026, 11, 23, 12, 30), bill.occurredAt)
    }

    @Test
    fun `falls back to generic source`() {
        val bill = engine.recognize(
            listOf(line("¥10.00"), line("随便一段文字")),
            now,
            zone,
        )

        assertEquals("generic", bill.sourceId)
        assertEquals(1000L, bill.amountMinor)
    }
}
