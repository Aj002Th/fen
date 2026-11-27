package app.fen.data.query

import app.fen.data.Category
import app.fen.data.Tx
import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerQueryTest {

    private val food = Category("c-food", "餐饮", TxType.EXPENSE, "food", 0L, 0)
    private val salary = Category("c-salary", "工资", TxType.INCOME, "salary", 0L, 1)
    private val categories = listOf(food, salary).associateBy { it.id }

    private fun tx(
        id: String,
        amount: Long,
        note: String = "",
        categoryId: String? = food.id,
        type: TxType = TxType.EXPENSE,
        at: Long = 1_700_000_000_000L,
    ) = Tx(
        id = id,
        type = type,
        amount = amount,
        categoryId = categoryId,
        note = note,
        occurredAt = at,
    )

    @Test
    fun keywordMatchesNoteCategoryAndFormattedAmount() {
        val list = listOf(tx("1", 123456, note = "午饭"))

        assertTrue(list.query(TxFilter(keyword = "午饭"), categories).isNotEmpty())
        assertTrue(list.query(TxFilter(keyword = "餐饮"), categories).isNotEmpty())
        assertTrue(list.query(TxFilter(keyword = "1,234.56"), categories).isNotEmpty())
    }

    @Test
    fun keywordDoesNotMatchUngroupedAmount() {
        val list = listOf(tx("1", 123456, note = ""))

        assertTrue(list.query(TxFilter(keyword = "1234"), categories).isEmpty())
    }

    @Test
    fun fromAndToAreInclusiveMillis() {
        val at = 1_700_000_000_000L
        val list = listOf(
            tx("1", 100, at = at - 1),
            tx("2", 100, at = at),
            tx("3", 100, at = at + 1),
        )

        val result = list.query(TxFilter(fromMillis = at, toMillis = at), categories)

        assertEquals(listOf("2"), result.map { it.id })
    }

    @Test
    fun filtersByTypeCategoryAndAmount() {
        val list = listOf(
            tx("1", 5_000, categoryId = food.id),
            tx("2", 15_000, categoryId = food.id),
            tx("3", 25_000, categoryId = salary.id, type = TxType.INCOME),
        )

        val result = list.query(
            TxFilter(
                type = TxType.EXPENSE,
                categoryId = food.id,
                minMinor = 10_000,
                maxMinor = 20_000,
            ),
            categories,
        )

        assertEquals(listOf("2"), result.map { it.id })
    }

    @Test
    fun sortsByAmountDescending() {
        val list = listOf(tx("1", 100), tx("2", 300), tx("3", 200))

        assertEquals(
            listOf("2", "3", "1"),
            list.query(TxFilter(), categories, Sort.AMOUNT_DESC).map { it.id },
        )
    }
}
