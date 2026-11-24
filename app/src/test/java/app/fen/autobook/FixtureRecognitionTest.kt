package app.fen.autobook

import app.fen.autobook.model.BoundingBox
import app.fen.autobook.model.OcrLine
import app.fen.autobook.parse.GenericBillParser
import app.fen.autobook.pipeline.RecognitionEngine
import app.fen.autobook.source.SourceDetection
import app.fen.autobook.source.recognizers.AlipayRecognizer
import app.fen.autobook.source.recognizers.DouyinRecognizer
import app.fen.autobook.source.recognizers.GenericRecognizer
import app.fen.autobook.source.recognizers.JdFinanceRecognizer
import app.fen.autobook.source.recognizers.JdRecognizer
import app.fen.autobook.source.recognizers.MeituanRecognizer
import app.fen.autobook.source.recognizers.MeituanTakeoutRecognizer
import app.fen.autobook.source.recognizers.PddRecognizer
import app.fen.autobook.source.recognizers.TaobaoRecognizer
import app.fen.autobook.source.recognizers.UnionpayRecognizer
import app.fen.autobook.source.recognizers.WechatRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class FixtureRecognitionTest {
    private val engine = RecognitionEngine(
        SourceDetection(
            listOf(
                WechatRecognizer,
                AlipayRecognizer,
                UnionpayRecognizer,
                JdRecognizer,
                JdFinanceRecognizer,
                MeituanRecognizer,
                MeituanTakeoutRecognizer,
                PddRecognizer,
                TaobaoRecognizer,
                DouyinRecognizer,
                GenericRecognizer,
            )
        ),
        GenericBillParser(),
    )

    private val now = 1_763_800_000_000L
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `wechat fixture`() = check("wechat_payment.txt", "wechat")

    @Test
    fun `alipay fixture`() = check("alipay_bill.txt", "alipay")

    @Test
    fun `unionpay fixture`() = check("unionpay_detail.txt", "unionpay")

    @Test
    fun `jd fixture`() = check("jd_order.txt", "jd")

    @Test
    fun `jd finance fixture`() = check("jd_finance_bill.txt", "jd_finance")

    @Test
    fun `meituan fixture`() = check("meituan_bill.txt", "meituan")

    @Test
    fun `meituan takeout fixture`() = check("meituan_takeout_bill.txt", "meituan_takeout")

    @Test
    fun `pdd fixture`() = check("pdd_bill.txt", "pdd")

    @Test
    fun `taobao fixture`() = check("taobao_order.txt", "taobao")

    @Test
    fun `douyin fixture`() = check("douyin_bill.txt", "douyin")

    private fun check(name: String, expectedSource: String) {
        val bill = engine.recognize(readFixture(name), now, zone)

        assertEquals(expectedSource, bill.sourceId)
        assertTrue(bill.amountMinor > 0)
    }

    private fun readFixture(name: String): List<OcrLine> {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name")
            ?: error("missing fixture: $name")
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val parts = line.split("|")
                    OcrLine(
                        text = parts[0],
                        box = BoundingBox(
                            parts[1].toInt(),
                            parts[2].toInt(),
                            parts[3].toInt(),
                            parts[4].toInt(),
                        ),
                        confidence = null,
                    )
                }
                .toList()
        }
    }
}
