package app.fen.autobook.classify

import app.fen.autobook.model.ExtractorMode
import app.fen.autobook.model.ParsedBill
import app.fen.data.Tx
import app.fen.data.TxType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateDetectorTest {
    private val now = 1_763_800_000_000L

    private fun bill(
        amount: Long = 3250,
        type: TxType? = TxType.EXPENSE,
        merchant: String = "测试餐厅",
        occurredAt: Long = now,
    ) = ParsedBill(
        amountMinor = amount,
        type = type,
        categoryId = null,
        merchant = merchant,
        occurredAt = occurredAt,
        rawText = "",
        sourceId = "wechat",
        sourcePage = "payment_done",
        extractor = ExtractorMode.IMAGE,
        confidence = 0.9f,
        evidence = emptyList(),
    )

    private fun tx(
        amount: Long = 3250,
        type: TxType = TxType.EXPENSE,
        note: String = "测试餐厅",
        occurredAt: Long = now,
    ) = Tx(
        id = "tx-1",
        type = type,
        amount = amount,
        categoryId = null,
        note = note,
        occurredAt = occurredAt,
    )

    @Test
    fun `matches same amount time and merchant`() {
        assertNotNull(DuplicateDetector.find(bill(), listOf(tx())))
    }

    @Test
    fun `different amount is not duplicate`() {
        assertNull(DuplicateDetector.find(bill(), listOf(tx(amount = 1000))))
    }

    @Test
    fun `three minutes apart is not duplicate`() {
        assertNull(DuplicateDetector.find(bill(), listOf(tx(occurredAt = now + 180_000))))
    }

    @Test
    fun `different type is not duplicate`() {
        assertNull(
            DuplicateDetector.find(
                bill(type = TxType.EXPENSE),
                listOf(tx(type = TxType.INCOME)),
            )
        )
    }

    @Test
    fun `blank merchant matches by amount time and type`() {
        assertNotNull(
            DuplicateDetector.find(
                bill(merchant = ""),
                listOf(tx(note = "")),
            )
        )
    }
}
