package app.fen.autobook.classify

import app.fen.data.TxType

object MerchantKey {
    private val noise = listOf("支付", "收款", "订单", "尾号")
    private val punctuation = Regex("""[\s·\-—_:：,，.。()（）]""")
    private val titles = setOf(
        "微信", "微信支付", "支付宝", "云闪付", "京东", "京东金融",
        "美团", "美团外卖", "拼多多", "淘宝", "天猫", "抖音",
    )

    fun of(merchant: String, type: TxType): String {
        var value = merchant.lowercase()
        for (word in noise) value = value.replace(word, "")
        value = value.replace(punctuation, "")
        if (value.isBlank() || value in titles) return ""
        return "$value|${type.name}"
    }
}
