package app.fen.autobook.parse

import app.fen.autobook.model.OcrLine

object MerchantExtractor {
    private val defaultAnchors = listOf("商户全称", "收款方", "商户", "订单摘要", "商品说明")
    private val noise = listOf("支付", "收款")
    private val idWords = listOf("单号", "订单号", "交易单号", "卡号", "尾号", "编号")
    private val titles = setOf(
        "微信", "微信支付", "支付宝", "云闪付", "京东", "京东金融",
        "美团", "美团外卖", "拼多多", "淘宝", "天猫", "抖音",
    )

    fun extract(lines: List<OcrLine>, anchors: List<String> = defaultAnchors): String {
        for (anchor in anchors) {
            for ((index, line) in lines.withIndex()) {
                val text = line.text.trim()
                if (!text.contains(anchor)) continue
                val sameLine = clean(text.substringAfter(anchor))
                val value = when {
                    sameLine.isNotBlank() &&
                        !looksLikeId(sameLine) &&
                        !looksLikeAmountOrTime(sameLine) -> sameLine

                    else -> lines.getOrNull(index + 1)?.text
                        ?.let(::clean)
                        ?.takeIf {
                            it.isNotBlank() &&
                                !looksLikeId(it) &&
                                !looksLikeAmountOrTime(it) &&
                                it !in titles
                        }
                }
                if (value != null) return value
            }
        }

        return lines
            .filter { line ->
                val text = line.text.trim()
                text.isNotBlank() &&
                    !looksLikeId(text) &&
                    !looksLikeAmountOrTime(text) &&
                    clean(text) !in titles
            }
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

    private fun looksLikeId(text: String): Boolean = idWords.any { text.contains(it) }

    private fun looksLikeAmountOrTime(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        val amountOnly = Regex("""^[¥￥]?\s*\d[\d,]*(?:\.\d{1,2})?\s*元?$""")
        val dateOnly = Regex("""^\d{4}[-/年]\d{1,2}[-/月]\d{1,2}日?(\s+\d{1,2}:\d{2})?$""")
        val timeOnly = Regex("""^\d{1,2}:\d{2}(?::\d{2})?$""")
        if (amountOnly.matches(value) || dateOnly.matches(value) || timeOnly.matches(value)) return true
        return value.contains("时间") || value.contains("日期")
    }
}
