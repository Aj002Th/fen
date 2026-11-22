package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource
import app.fen.autobook.source.templates.AlipayTemplate

object AlipayRecognizer : SourceRecognizer {
    override val id = "alipay"
    override val imageHints = AlipayTemplate.hints

    private val titles = listOf("支付宝", "Alipay")
    private val pages = listOf("支付成功", "账单详情", "交易详情")
    private val aux = listOf("收款方", "订单号", "创建时间")

    override fun detect(text: String) = scoreSource(text, titles, pages, aux)

    override fun pageHint(text: String): String? = when {
        text.contains("支付成功") -> "payment_done"
        text.contains("账单详情") || text.contains("交易详情") -> "bill_detail"
        else -> null
    }

    override val billTypeHints = mapOf(
        "payment_done" to "merchant_payment",
        "bill_detail" to "merchant_payment",
    )
}
