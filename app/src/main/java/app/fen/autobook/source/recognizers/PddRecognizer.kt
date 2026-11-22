package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource

object PddRecognizer : SourceRecognizer {
    override val id = "pdd"

    private val titles = listOf("拼多多", "多多钱包", "PDD")
    private val pages = listOf("账单详情", "交易记录", "订单详情")
    private val aux = listOf("实付", "免拼", "订单号")

    override fun detect(text: String) = scoreSource(text, titles, pages, aux)

    override fun pageHint(text: String): String? = when {
        text.contains("订单详情") -> "order_detail"
        text.contains("账单详情") || text.contains("交易记录") -> "bill_detail"
        else -> null
    }

    override val billTypeHints = mapOf(
        "order_detail" to "shopping",
        "bill_detail" to "shopping",
    )
}
