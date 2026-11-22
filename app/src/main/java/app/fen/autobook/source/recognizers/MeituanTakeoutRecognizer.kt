package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource

object MeituanTakeoutRecognizer : SourceRecognizer {
    override val id = "meituan_takeout"

    private val titles = listOf("美团外卖")
    private val pages = listOf("支付成功", "订单详情", "钱包账单")
    private val aux = listOf("配送费", "实付", "外卖")

    override fun detect(text: String) = scoreSource(text, titles, pages, aux)

    override fun pageHint(text: String): String? = when {
        text.contains("支付成功") -> "payment_done"
        text.contains("订单详情") -> "order_detail"
        text.contains("钱包账单") -> "bill_detail"
        else -> null
    }

    override val billTypeHints = mapOf(
        "payment_done" to "takeout",
        "order_detail" to "takeout",
        "bill_detail" to "takeout",
    )
}
