package app.fen.autobook.parse

import app.fen.data.TxType

object DirectionParser {
    private val unsupported = listOf("转账", "还款")
    private val income = listOf("收款", "入账", "退款", "收入")
    private val expense = listOf("付款", "支付", "消费", "实付", "支出")

    fun parse(text: String): TxType? {
        if (unsupported.any { text.contains(it) }) return null
        if (income.any { text.contains(it) }) return TxType.INCOME
        if (expense.any { text.contains(it) }) return TxType.EXPENSE
        return null
    }
}
