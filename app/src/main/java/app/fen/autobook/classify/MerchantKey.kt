package app.fen.autobook.classify

import app.fen.data.TxType

object MerchantKey {
    private val noise = listOf("支付", "收款", "订单", "尾号")
    private val punctuation = Regex("""[\s·\-—_:：,，.。()（）]""")

    fun of(merchant: String, type: TxType): String {
        var value = merchant.lowercase()
        for (word in noise) value = value.replace(word, "")
        value = value.replace(punctuation, "")
        return "$value|${type.name}"
    }
}
