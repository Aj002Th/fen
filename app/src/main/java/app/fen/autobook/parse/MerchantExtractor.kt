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
    private val pageWords = setOf(
        "支付成功", "支付完成", "账单详情", "交易详情", "订单详情",
        "微信支付", "支付宝", "云闪付", "我的账单", "交易记录",
    )
    private val labelPrefix = Regex(
        """^(?:商[户戶]全[称稱]|商[户戶]名[称稱]|收款方|商[户戶])\s*[:：]?\s*"""
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
                                it !in titles &&
                                it !in pageWords
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
                    text !in pageWords &&
                    clean(text) !in titles &&
                    clean(text) !in pageWords
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
        // OCR 有时把标签和内容连成一行，或输出繁体「商戶全稱」。
        value = value.replace(labelPrefix, "").trim()
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
