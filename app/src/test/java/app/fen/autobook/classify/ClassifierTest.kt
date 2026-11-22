package app.fen.autobook.classify

import app.fen.autobook.model.ExtractorMode
import app.fen.autobook.model.ParsedBill
import app.fen.data.Category
import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassifierTest {
    private fun category(id: String, name: String, type: TxType) =
        Category(id, name, type, "other", 0L, 0)

    private fun bill(
        merchant: String = "某某餐厅",
        type: TxType? = TxType.EXPENSE,
        rawText: String = "",
    ) = ParsedBill(
        amountMinor = 3250,
        type = type,
        categoryId = null,
        merchant = merchant,
        occurredAt = 1_763_800_000_000,
        rawText = rawText.ifBlank { merchant },
        sourceId = "wechat",
        sourcePage = "payment_done",
        extractor = ExtractorMode.IMAGE,
        confidence = 0.9f,
        evidence = emptyList(),
    )

    private val food = category("cat-food", "餐饮", TxType.EXPENSE)
    private val salary = category("cat-salary", "工资", TxType.INCOME)

    @Test
    fun `mapping hit wins`() {
        val store = InMemoryAutoMapStore()
        store.upsert(MerchantKey.of("某某餐厅", TxType.EXPENSE), food.id)
        val classifier = Classifier(store, RuleClassifier(emptyMap()))

        val result = classifier.classify(bill(), listOf(food, salary))

        assertEquals(food.id, result.categoryId)
        assertEquals(ClassificationSource.MAPPING, result.source)
    }

    @Test
    fun `rule hit when no mapping`() {
        val classifier = Classifier(
            InMemoryAutoMapStore(),
            RuleClassifier(mapOf("餐厅" to "餐饮")),
        )

        val result = classifier.classify(bill(), listOf(food, salary))

        assertEquals(food.id, result.categoryId)
        assertEquals(ClassificationSource.RULE, result.source)
    }

    @Test
    fun `deleted mapping is skipped`() {
        val store = InMemoryAutoMapStore()
        store.upsert(MerchantKey.of("某某餐厅", TxType.EXPENSE), "missing")
        val classifier = Classifier(store, RuleClassifier(emptyMap()))

        val result = classifier.classify(bill(), listOf(food, salary))

        assertNull(result.categoryId)
        assertEquals(ClassificationSource.RULE, result.source)
    }

    @Test
    fun `type mismatch is skipped`() {
        val store = InMemoryAutoMapStore()
        store.upsert(MerchantKey.of("某某餐厅", TxType.EXPENSE), salary.id)
        val classifier = Classifier(store, RuleClassifier(emptyMap()))

        val result = classifier.classify(bill(type = TxType.EXPENSE), listOf(food, salary))

        assertNull(result.categoryId)
    }

    @Test
    fun `apply sets category id`() {
        val store = InMemoryAutoMapStore()
        store.upsert(MerchantKey.of("某某餐厅", TxType.EXPENSE), food.id)
        val classifier = Classifier(store, RuleClassifier(emptyMap()))

        val result = classifier.apply(bill(), listOf(food, salary))

        assertEquals(food.id, result.categoryId)
    }
}
