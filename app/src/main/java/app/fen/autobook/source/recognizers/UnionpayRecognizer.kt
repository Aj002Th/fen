package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource

object UnionpayRecognizer : SourceRecognizer {
    override val id = "unionpay"

    private val titles = listOf("云闪付", "银联")
    private val pages = listOf("交易详情", "交易记录", "支付消息")
    private val aux = listOf("交易金额", "交易时间", "商户名称")

    override fun detect(text: String) = scoreSource(text, titles, pages, aux)

    override fun pageHint(text: String): String? = when {
        text.contains("交易详情") || text.contains("交易记录") || text.contains("支付消息") -> "bill_detail"
        else -> null
    }

    override val billTypeHints = mapOf(
        "bill_detail" to "merchant_payment",
    )
}
