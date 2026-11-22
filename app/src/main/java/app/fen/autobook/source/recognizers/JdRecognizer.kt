package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource

object JdRecognizer : SourceRecognizer {
    override val id = "jd"

    private val titles = listOf("京东支付", "京东", "JD")
    private val pages = listOf("支付成功", "订单详情", "钱包账单", "账单详情")
    private val aux = listOf("实付", "商品总价", "订单号")

    override fun detect(text: String): Float {
        var score = scoreSource(text, titles, pages, aux)
        if (text.contains("京东金融") || text.contains("白条") || text.contains("金条")) score -= 0.2f
        return score.coerceAtLeast(0f)
    }

    override fun pageHint(text: String): String? = when {
        text.contains("支付成功") -> "payment_done"
        text.contains("订单详情") -> "order_detail"
        text.contains("钱包账单") || text.contains("账单详情") -> "bill_detail"
        else -> null
    }

    override val billTypeHints = mapOf(
        "payment_done" to "goods",
        "order_detail" to "goods",
        "bill_detail" to "goods",
    )
}
