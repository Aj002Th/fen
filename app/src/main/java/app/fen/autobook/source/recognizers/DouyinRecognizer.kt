package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource

object DouyinRecognizer : SourceRecognizer {
    override val id = "douyin"

    private val titles = listOf("抖音极速版", "抖音火山版", "抖音精选", "抖音", "Douyin")
    private val pages = listOf("账单详情", "订单详情", "我的钱包")
    private val aux = listOf("实付", "打赏", "订单号")

    override fun detect(text: String) = scoreSource(text, titles, pages, aux)

    override fun pageHint(text: String): String? = when {
        text.contains("订单详情") -> "order_detail"
        text.contains("账单详情") || text.contains("我的钱包") -> "bill_detail"
        else -> null
    }

    override val billTypeHints = mapOf(
        "order_detail" to "shopping",
        "bill_detail" to "shopping",
    )
}
