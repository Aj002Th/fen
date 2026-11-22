package app.fen.autobook.classify

import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryAutoMapStoreTest {
    @Test
    fun `merchant key ignores payment noise`() {
        assertEquals(
            MerchantKey.of("某某餐厅-支付", TxType.EXPENSE),
            MerchantKey.of("某某餐厅 收款", TxType.EXPENSE),
        )
    }

    @Test
    fun `merchant key includes type`() {
        assertNotEquals(
            MerchantKey.of("某某餐厅", TxType.EXPENSE),
            MerchantKey.of("某某餐厅", TxType.INCOME),
        )
    }

    @Test
    fun `upsert and find`() {
        val store = InMemoryAutoMapStore()

        store.upsert("k", "cat")

        assertEquals("cat", store.find("k"))
    }

    @Test
    fun `delete for category removes only that category`() {
        val store = InMemoryAutoMapStore()
        store.upsert("a", "cat1")
        store.upsert("b", "cat1")
        store.upsert("c", "cat2")

        store.deleteForCategory("cat1")

        assertNull(store.find("a"))
        assertNull(store.find("b"))
        assertEquals("cat2", store.find("c"))
    }

    @Test
    fun `all returns snapshot`() {
        val store = InMemoryAutoMapStore()
        store.upsert("a", "cat")

        assertEquals(mapOf("a" to "cat"), store.all())
    }
}
