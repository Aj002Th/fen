package app.fen.autobook.source.templates

import app.fen.autobook.source.ImageHints

object WechatTemplate {
    val hints = ImageHints(
        amountAnchors = listOf("支付金额", "支付成功", "¥"),
        merchantAnchors = listOf("商户全称", "收款方", "商户"),
        timeAnchors = listOf("支付时间", "转账时间"),
        directionAnchors = listOf("支付成功", "付款", "转账"),
        excludePatterns = listOf("订单号", "尾号", "余额", "零钱"),
    )
}
