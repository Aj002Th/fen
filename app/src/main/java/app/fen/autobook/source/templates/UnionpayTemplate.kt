package app.fen.autobook.source.templates

import app.fen.autobook.source.ImageHints

object UnionpayTemplate {
    val hints = ImageHints(
        amountAnchors = listOf("交易金额", "金额", "¥"),
        merchantAnchors = listOf("商户名称", "交易商户", "收款方"),
        timeAnchors = listOf("交易时间", "交易日期"),
        directionAnchors = listOf("消费", "转账", "还款"),
        excludePatterns = listOf("交易序号", "卡号", "余额"),
    )
}
