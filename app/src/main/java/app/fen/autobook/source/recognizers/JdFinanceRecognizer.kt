package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer
import app.fen.autobook.source.scoreSource

object JdFinanceRecognizer : SourceRecognizer {
    override val id = "jd_finance"

    private val titles = listOf("京东金融", "京东白条", "京东金条", "白条", "金条")
    private val pages = listOf("账单详情", "我的账单")
    private val aux = listOf("还款", "账单金额", "白条")

    override fun detect(text: String) = scoreSource(text, titles, pages, aux)

    override fun pageHint(text: String): String? =
        if (text.contains("账单详情") || text.contains("我的账单")) "bill_detail" else null

    override val billTypeHints = mapOf(
        "bill_detail" to "credit",
    )
}
