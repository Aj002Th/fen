package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource

object TaobaoRecognizer : SourceRecognizer {
    override val id = "taobao"

    private val titles = listOf("淘宝闪购", "淘宝", "天猫", "Taobao", "Tmall")
    private val pages = listOf("订单详情", "支付成功", "我的订单")
    private val aux = listOf("实付", "商品", "运费")

    override fun detect(text: String) = scoreSource(text, titles, pages, aux)

    override fun pageHint(text: String): String? = when {
        text.contains("支付成功") -> "payment_done"
        text.contains("订单详情") || text.contains("我的订单") -> "order_detail"
        else -> null
    }

    override val billTypeHints = mapOf(
        "payment_done" to "shopping",
        "order_detail" to "shopping",
    )
}
