package app.fen.autobook.ui

object SourceLabels {
    fun source(id: String?): String? = when (id) {
        "wechat" -> "微信"
        "alipay" -> "支付宝"
        "unionpay" -> "云闪付"
        "jd" -> "京东"
        "jd_finance" -> "京东金融"
        "meituan" -> "美团"
        "meituan_takeout" -> "美团外卖"
        "pdd" -> "拼多多"
        "taobao" -> "淘宝/天猫"
        "douyin" -> "抖音"
        else -> null
    }

    fun page(page: String?): String? = when (page) {
        "payment_done" -> "支付完成"
        "bill_detail" -> "账单详情"
        "order_detail" -> "订单详情"
        "transfer" -> "转账"
        else -> null
    }
}
