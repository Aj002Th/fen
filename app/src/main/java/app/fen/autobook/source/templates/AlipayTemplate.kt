package app.fen.autobook.source.templates

import app.fen.autobook.source.ImageHints

object AlipayTemplate {
    val hints = ImageHints(
        amountAnchors = listOf("金额", "¥", "￥"),
        merchantAnchors = listOf("收款方", "商户", "商品说明"),
        timeAnchors = listOf("创建时间", "付款时间", "交易时间"),
        directionAnchors = listOf("付款", "收款", "退款"),
        excludePatterns = listOf("订单号", "尾号", "余额", "优惠", "花呗"),
    )
}
