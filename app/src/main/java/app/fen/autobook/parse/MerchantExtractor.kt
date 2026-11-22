package app.fen.autobook.parse

import app.fen.autobook.model.OcrLine

object MerchantExtractor {
    private val defaultAnchors = listOf("商户全称", "收款方", "商户", "订单摘要", "商品说明")
    private val noise = listOf("支付", "收款")

    fun extract(lines: List<OcrLine>, anchors: List<String> = defaultAnchors): String {
        for (anchor in anchors) {
            for (line in lines) {
                val text = line.text.trim()
                if (!text.contains(anchor)) continue
                val value = clean(text.substringAfter(anchor))
                if (value.isNotBlank() && !looksLikeAmountOrTime(value)) return value
            }
        }

        return lines
            .filter { it.text.isNotBlank() && !looksLikeAmountOrTime(it.text) }
            .maxByOrNull { it.box.height }
            ?.text
            ?.let(::clean)
            .orEmpty()
    }

    private fun clean(raw: String): String {
        var value = raw.trim()
            .trim(':', '：', '-', '—', '·', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        for (word in noise) {
            value = value.removePrefix(word).removeSuffix(word).trim()
        }
        return value
    }

    private fun looksLikeAmountOrTime(text: String): Boolean {
        if (AmountParser.parse(text) != null) return true
        if (text.contains("时间") || text.contains("日期")) return true
        return Regex("""\d{1,2}:\d{2}""").containsMatchIn(text)
    }
}
