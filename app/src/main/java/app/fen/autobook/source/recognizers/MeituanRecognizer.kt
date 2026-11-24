package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource

object MeituanRecognizer : SourceRecognizer {
    override val id = "meituan"

    private val titles = listOf("美团支付", "美团")
    private val pages = listOf("支付成功", "订单详情", "美团钱包", "账单")
    private val aux = listOf("实付", "红包", "配送费", "美食", "外卖", "闪购", "电影")

    override fun detect(text: String): Float {
        var score = scoreSource(text, titles, pages, aux)
        if (text.contains("美团外卖")) score -= 0.2f
        return score.coerceAtLeast(0f)
    }

    override fun pageHint(text: String): String? = when {
        text.contains("支付成功") -> "payment_done"
        text.contains("订单详情") -> "order_detail"
        text.contains("美团钱包") || text.contains("账单") -> "bill_detail"
        else -> null
    }

    override val billTypeHints = mapOf(
        "payment_done" to "food",
        "order_detail" to "food",
        "bill_detail" to "food",
    )
}
