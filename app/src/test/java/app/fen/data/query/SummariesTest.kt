package app.fen.data.query

import app.fen.data.Category
import app.fen.data.Tx
import app.fen.data.TxType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummariesTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val food = Category("c-food", "餐饮", TxType.EXPENSE, "food", 0L, 0)
    private val shopping = Category("c-shopping", "购物", TxType.EXPENSE, "shopping", 0L, 1)
    private val salary = Category("c-salary", "工资", TxType.INCOME, "salary", 0L, 2)
    private val categories = listOf(food, shopping, salary)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(year, month, day, hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun tx(
        id: String,
        amount: Long,
        type: TxType = TxType.EXPENSE,
        categoryId: String? = food.id,
        occurredAt: Long = at(2026, 11, 15),
    ) = Tx(id, type, amount, categoryId, "", occurredAt)

    private fun range(year: Int, month: Int): Pair<Long, Long> =
        at(year, month, 1, 0) to at(year, month, 1, 0).let { _ ->
            LocalDateTime.of(year, month, 1, 0, 0)
                .plusMonths(1)
                .minusNanos(1_000_000)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }

    @Test
    fun totalsAlwaysIncludesBothSides() {
        val txs = listOf(
            tx("1", 10_000, TxType.EXPENSE),
            tx("2", 50_000, TxType.INCOME, salary.id),
        )
        val (from, to) = range(2026, 11)

        val summary = summarize(txs, categories, from, to, GroupBy.CATEGORY, SummaryType.EXPENSE, zone)

        assertEquals(10_000, summary.totals.expense)
        assertEquals(50_000, summary.totals.income)
        assertEquals(40_000, summary.totals.net)
        assertEquals(2, summary.count)
        assertTrue(summary.groups.all { it.type == TxType.EXPENSE })
    }

    @Test
    fun uncategorizedGroupReconciles() {
        val txs = listOf(
            tx("1", 6_000, categoryId = food.id),
            tx("2", 4_000, categoryId = null),
        )
        val (from, to) = range(2026, 11)

        val summary = summarize(txs, categories, from, to, GroupBy.CATEGORY, SummaryType.EXPENSE, zone)

        assertEquals(10_000, summary.groups.sumOf { it.amount })
        assertTrue(summary.groups.any { it.key == null && it.label == "未分类" })
    }

    @Test
    fun shareUsesSameTypeDenominator() {
        val txs = listOf(
            tx("1", 3_000, categoryId = food.id),
            tx("2", 7_000, categoryId = shopping.id),
        )
        val (from, to) = range(2026, 11)

        val summary = summarize(txs, categories, from, to, GroupBy.CATEGORY, SummaryType.EXPENSE, zone)

        assertEquals(30.0, summary.groups.first { it.key == food.id }.sharePercent, 0.001)
        assertEquals(70.0, summary.groups.first { it.key == shopping.id }.sharePercent, 0.001)
    }

    @Test
    fun monthGroupingUsesLocalZone() {
        // 2026-01-31 16:30 UTC = 2026-02-01 00:30 Asia/Shanghai
        val instant = java.time.Instant.parse("2026-01-31T16:30:00Z").toEpochMilli()
        val txs = listOf(tx("1", 1_000, occurredAt = instant))
        val (from, to) = range(2026, 2)

        val summary = summarize(txs, categories, from, to, GroupBy.MONTH, SummaryType.EXPENSE, zone)

        assertEquals("2026-02", summary.groups.single().key)
    }

    @Test
    fun previousMonthUsesCalendarMonth() {
        val current = listOf(tx("1", 10_000, occurredAt = at(2026, 11, 15)))
        val previous = listOf(tx("2", 8_000, occurredAt = at(2026, 10, 15)))
        val (from, to) = range(2026, 11)
        val (prevFrom, prevTo) = range(2026, 10)

        val summary = summarize(
            current + previous, categories, from, to,
            GroupBy.CATEGORY, SummaryType.EXPENSE, zone,
            previousFromMillis = prevFrom, previousToMillis = prevTo,
        )

        assertEquals(8_000L, summary.previous?.expense)
        assertEquals(25.0, summary.expenseChangePercent ?: 0.0, 0.001)
    }

    @Test
    fun previousRangeIsEqualLength() {
        val from = at(2026, 11, 1, 0)
        val to = at(2026, 11, 10, 23)
        val txs = listOf(tx("1", 5_000, occurredAt = at(2026, 10, 25)))

        val summary = summarize(txs, categories, from, to, GroupBy.CATEGORY, SummaryType.EXPENSE, zone)

        assertEquals(5_000L, summary.previous?.expense)
        assertEquals(1, summary.previousCount)
    }

    @Test
    fun previousZeroGivesNullPercent() {
        val txs = listOf(tx("1", 100, occurredAt = at(2026, 11, 15)))
        val (from, to) = range(2026, 11)

        val summary = summarize(txs, categories, from, to, GroupBy.CATEGORY, SummaryType.EXPENSE, zone)

        assertNull(summary.expenseChangePercent)
        assertNull(summary.incomeChangePercent)
    }

    @Test
    fun allTypesSplitRows() {
        val txs = listOf(
            tx("1", 10_000, TxType.EXPENSE),
            tx("2", 50_000, TxType.INCOME, salary.id),
        )
        val (from, to) = range(2026, 11)

        val summary = summarize(txs, categories, from, to, GroupBy.TYPE, SummaryType.ALL, zone)

        assertEquals(2, summary.groups.size)
        assertEquals(setOf(TxType.EXPENSE, TxType.INCOME), summary.groups.map { it.type }.toSet())
    }
}
