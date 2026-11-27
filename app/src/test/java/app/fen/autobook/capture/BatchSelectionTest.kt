package app.fen.autobook.capture

import app.fen.autobook.model.ExtractorMode
import app.fen.autobook.model.ParsedBill
import app.fen.data.Tx
import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchSelectionTest {
    private val now = 1_763_800_000_000L

    private fun bill(
        amount: Long = 3250,
        categoryId: String? = "cat-food",
    ) = ParsedBill(
        amountMinor = amount,
        type = TxType.EXPENSE,
        categoryId = categoryId,
        merchant = "测试餐厅",
        occurredAt = now,
        rawText = "",
        sourceId = "wechat",
        sourcePage = "payment_done",
        extractor = ExtractorMode.IMAGE,
        confidence = 0.9f,
        evidence = emptyList(),
    )

    private fun tx() = Tx(
        id = "tx-1",
        type = TxType.EXPENSE,
        amount = 3250,
        categoryId = "cat-food",
        note = "测试餐厅",
        occurredAt = now,
    )

    @Test
    fun `only valid non duplicate rows are selected`() {
        val items = listOf(
            BatchItem(bill(amount = 4000), null),
            BatchItem(bill(amount = 0), null),
            BatchItem(bill(categoryId = null), null),
            BatchItem(bill(), null),
            BatchItem(null, "未识别到金额"),
        )

        assertEquals(setOf(0), BatchSelection.initialSelection(items, listOf(tx())))
    }

    @Test
    fun `saveable filters zero and uncategorized bills`() {
        val items = listOf(
            BatchItem(bill(), null),
            BatchItem(bill(amount = 0), null),
            BatchItem(bill(categoryId = null), null),
        )

        val saved = BatchSelection.saveable(items, setOf(0, 1, 2))

        assertEquals(listOf(bill()), saved)
    }

    @Test
    fun `saveable respects selection`() {
        val items = listOf(
            BatchItem(bill(), null),
            BatchItem(bill(), null),
        )

        val saved = BatchSelection.saveable(items, setOf(1))

        assertEquals(listOf(bill()), saved)
    }
}
