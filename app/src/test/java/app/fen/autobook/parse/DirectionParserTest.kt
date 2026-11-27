package app.fen.autobook.parse

import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectionParserTest {
    @Test
    fun `payment is expense`() {
        assertEquals(TxType.EXPENSE, DirectionParser.parse("微信支付"))
    }

    @Test
    fun `receipt is income`() {
        assertEquals(TxType.INCOME, DirectionParser.parse("已收款"))
    }

    @Test
    fun `refund arrival is income`() {
        assertEquals(TxType.INCOME, DirectionParser.parse("退款到账"))
    }

    @Test
    fun `transfer is unsupported`() {
        assertNull(DirectionParser.parse("转账给朋友"))
    }

    @Test
    fun `repayment is unsupported`() {
        assertNull(DirectionParser.parse("信用卡还款"))
    }

    @Test
    fun `unknown text has no direction`() {
        assertNull(DirectionParser.parse("随便一段文字"))
    }
}
