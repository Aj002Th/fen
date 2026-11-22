package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource

object WechatRecognizer : SourceRecognizer {
    override val id = "wechat"

    private val titles = listOf("微信支付", "微信", "WeChat")
    private val pages = listOf("支付成功", "账单详情", "转账", "红包")
    private val aux = listOf("支付时间", "商户全称", "收款方")

    override fun detect(text: String) = scoreSource(text, titles, pages, aux)

    override fun pageHint(text: String): String? = when {
        text.contains("支付成功") -> "payment_done"
        text.contains("账单详情") -> "bill_detail"
        text.contains("转账") || text.contains("红包") -> "transfer"
        else -> null
    }

    override val billTypeHints = mapOf(
        "payment_done" to "merchant_payment",
        "bill_detail" to "merchant_payment",
        "transfer" to "transfer",
    )
}
