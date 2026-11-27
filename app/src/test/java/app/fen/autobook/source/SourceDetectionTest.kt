package app.fen.autobook.source

import app.fen.autobook.source.recognizers.AlipayRecognizer
import app.fen.autobook.source.recognizers.GenericRecognizer
import app.fen.autobook.source.recognizers.JdRecognizer
import app.fen.autobook.source.recognizers.MeituanRecognizer
import app.fen.autobook.source.recognizers.UnionpayRecognizer
import app.fen.autobook.source.recognizers.WechatRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDetectionTest {
    private val detection = SourceDetection(
        listOf(
            WechatRecognizer,
            AlipayRecognizer,
            UnionpayRecognizer,
            JdRecognizer,
            MeituanRecognizer,
            GenericRecognizer,
        )
    )

    @Test
    fun `detects wechat payment`() {
        val match = detection.detect("微信支付 支付成功 商户全称 某某餐厅 支付时间 12:30")

        assertEquals("wechat", match.sourceId)
        assertEquals("payment_done", match.page)
        assertTrue(match.score >= 0.6f)
    }

    @Test
    fun `detects alipay bill`() {
        val match = detection.detect("支付宝 账单详情 收款方 某某餐厅")

        assertEquals("alipay", match.sourceId)
        assertEquals("bill_detail", match.page)
    }

    @Test
    fun `detects unionpay bill`() {
        val match = detection.detect("云闪付 交易详情 交易金额 32.50 交易时间 12:30")

        assertEquals("unionpay", match.sourceId)
        assertEquals("bill_detail", match.page)
    }

    @Test
    fun `falls back to generic when only page word`() {
        val match = detection.detect("账单详情")

        assertEquals("generic", match.sourceId)
    }

    @Test
    fun `falls back to generic when best score is too low`() {
        val match = detection.detect("随便一段文字")

        assertEquals("generic", match.sourceId)
    }

    @Test
    fun `tie between two families falls back to generic`() {
        val match = detection.detect("京东 美团 订单详情")

        assertEquals("generic", match.sourceId)
    }

    @Test
    fun `weak match below gap falls back to generic`() {
        val weak = object : SourceRecognizer {
            override val id = "weak"
            override fun detect(text: String): Float = 0.6f
            override fun pageHint(text: String): String? = null
            override val billTypeHints = emptyMap<String, String>()
        }
        val custom = SourceDetection(listOf(weak, GenericRecognizer))

        assertEquals("generic", custom.detect("x").sourceId)
    }
}
