package app.fen.autobook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceLabelsTest {
    @Test
    fun `maps known sources to chinese`() {
        assertEquals("微信", SourceLabels.source("wechat"))
        assertEquals("支付宝", SourceLabels.source("alipay"))
        assertEquals("淘宝/天猫", SourceLabels.source("taobao"))
    }

    @Test
    fun `hides generic source`() {
        assertNull(SourceLabels.source("generic"))
        assertNull(SourceLabels.source(null))
    }

    @Test
    fun `maps pages to chinese`() {
        assertEquals("支付完成", SourceLabels.page("payment_done"))
        assertEquals("账单详情", SourceLabels.page("bill_detail"))
    }
}
