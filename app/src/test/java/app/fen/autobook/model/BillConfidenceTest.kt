package app.fen.autobook.model

import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillConfidenceTest {
    private fun bill(
        amount: Long = 3250,
        type: TxType? = TxType.EXPENSE,
        merchant: String = "某某餐厅",
        occurredAt: Long = 1_763_800_000_000,
        sourceId: String? = "wechat",
    ) = ParsedBill(
        amountMinor = amount,
        type = type,
        categoryId = null,
        merchant = merchant,
        occurredAt = occurredAt,
        rawText = "",
        sourceId = sourceId,
        sourcePage = "payment_done",
        extractor = ExtractorMode.IMAGE,
        confidence = 0f,
        evidence = emptyList(),
    )

    @Test
    fun `full bill is high confidence`() {
        assertEquals(1.0f, BillConfidence.of(bill()), 0.001f)
    }

    @Test
    fun `missing amount is unusable`() {
        assertEquals(0f, BillConfidence.of(bill(amount = 0)), 0.001f)
    }

    @Test
    fun `missing type lowers confidence`() {
        assertTrue(BillConfidence.of(bill(type = null)) < 0.8f)
    }

    @Test
    fun `guessed time and generic source lower confidence`() {
        val guessed = bill().copy(timeGuessed = true, sourceId = "generic")

        assertTrue(BillConfidence.of(guessed) < 0.8f)
    }
}
