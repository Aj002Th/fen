package app.fen.autobook.source

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
import org.junit.Test

class RemainingSourcesTest {
    private val detection = SourceDetection(
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
    )

    @Test
    fun `detects jd payment`() {
        val match = detection.detect("京东 支付成功 实付 32.50")
        assertEquals("jd", match.sourceId)
        assertEquals("payment_done", match.page)
    }

    @Test
    fun `detects jd finance bill`() {
        val match = detection.detect("京东金融 账单详情 账单金额 32.50")
        assertEquals("jd_finance", match.sourceId)
        assertEquals("bill_detail", match.page)
    }

    @Test
    fun `detects meituan order`() {
        val match = detection.detect("美团 订单详情 实付 32.50")
        assertEquals("meituan", match.sourceId)
        assertEquals("order_detail", match.page)
    }

    @Test
    fun `detects meituan takeout`() {
        val match = detection.detect("美团外卖 支付成功 配送费 5.00")
        assertEquals("meituan_takeout", match.sourceId)
        assertEquals("payment_done", match.page)
    }

    @Test
    fun `detects pdd bill`() {
        val match = detection.detect("拼多多 账单详情 实付 32.50")
        assertEquals("pdd", match.sourceId)
        assertEquals("bill_detail", match.page)
    }

    @Test
    fun `groups tmall into taobao`() {
        val match = detection.detect("天猫 订单详情 实付 32.50")
        assertEquals("taobao", match.sourceId)
        assertEquals("order_detail", match.page)
    }

    @Test
    fun `groups douyin lite into douyin`() {
        val match = detection.detect("抖音极速版 订单详情 实付 32.50")
        assertEquals("douyin", match.sourceId)
        assertEquals("order_detail", match.page)
    }

    @Test
    fun `falls back to generic for bare order page`() {
        val match = detection.detect("订单详情")
        assertEquals("generic", match.sourceId)
    }
}
