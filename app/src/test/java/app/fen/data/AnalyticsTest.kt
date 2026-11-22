package app.fen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class AnalyticsTest {

    private fun tx(
        type: TxType,
        amount: Long,
        date: LocalDate,
        categoryId: String? = null,
        hour: Int = 12,
    ): Tx = Tx(
        id = UUID.randomUUID().toString(),
        type = type,
        amount = amount,
        categoryId = categoryId,
        note = "",
        occurredAt = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    private fun category(id: String, type: TxType): Category =
        Category(id = id, name = id, type = type, icon = "other", color = 0xFF000000, sortOrder = 0)

    @Test
    fun `week period starts monday and ends today for current week`() {
        val today = LocalDate.of(2026, 11, 22) // Sunday
        val period = statsPeriod(StatsMode.WEEK, today, null, null, today)

        assertEquals(LocalDate.of(2026, 11, 16), period.start)
        assertEquals(LocalDate.of(2026, 11, 22), period.end)
        assertEquals(LocalDate.of(2026, 11, 9), period.previousStart)
        assertEquals(LocalDate.of(2026, 11, 15), period.previousEnd)
        assertEquals(7, period.days)
    }

    @Test
    fun `month period clamps current month to today`() {
        val today = LocalDate.of(2026, 11, 22)
        val period = statsPeriod(StatsMode.MONTH, LocalDate.of(2026, 11, 10), null, null, today)

        assertEquals(LocalDate.of(2026, 11, 1), period.start)
        assertEquals(LocalDate.of(2026, 11, 22), period.end)
        assertEquals(LocalDate.of(2026, 10, 1), period.previousStart)
        assertEquals(LocalDate.of(2026, 10, 22), period.previousEnd)
    }

    @Test
    fun `year period clamps current year to today`() {
        val today = LocalDate.of(2026, 11, 22)
        val period = statsPeriod(StatsMode.YEAR, LocalDate.of(2026, 6, 1), null, null, today)

        assertEquals(LocalDate.of(2026, 1, 1), period.start)
        assertEquals(LocalDate.of(2026, 11, 22), period.end)
        assertEquals(LocalDate.of(2025, 1, 1), period.previousStart)
        assertEquals(LocalDate.of(2025, 11, 22), period.previousEnd)
    }

    @Test
    fun `all period starts at earliest record`() {
        val today = LocalDate.of(2026, 11, 22)
        val period = statsPeriod(StatsMode.ALL, today, null, null, today, LocalDate.of(2024, 1, 5))

        assertEquals(LocalDate.of(2024, 1, 5), period.start)
        assertEquals(today, period.end)
        assertNull(period.previousStart)
        assertEquals(3, period.years)
        assertEquals(BucketUnit.YEAR, period.bucketUnit())
    }

    @Test
    fun `range period swaps inverted custom dates`() {
        val today = LocalDate.of(2026, 11, 22)
        val period = statsPeriod(
            mode = StatsMode.RANGE,
            anchor = today,
            customStart = LocalDate.of(2026, 11, 20),
            customEnd = LocalDate.of(2026, 11, 10),
            today = today,
        )

        assertEquals(LocalDate.of(2026, 11, 10), period.start)
        assertEquals(LocalDate.of(2026, 11, 20), period.end)
        assertEquals(11, period.days)
        assertEquals(LocalDate.of(2026, 10, 30), period.previousStart)
        assertEquals(LocalDate.of(2026, 11, 9), period.previousEnd)
    }

    @Test
    fun `previous month clamps to previous month end`() {
        val today = LocalDate.of(2026, 10, 31)
        val period = statsPeriod(StatsMode.MONTH, LocalDate.of(2026, 10, 15), null, null, today)

        assertEquals(LocalDate.of(2026, 10, 1), period.start)
        assertEquals(LocalDate.of(2026, 10, 31), period.end)
        assertEquals(LocalDate.of(2026, 9, 1), period.previousStart)
        assertEquals(LocalDate.of(2026, 9, 30), period.previousEnd)
    }

    @Test
    fun `week buckets have seven days and totals split by type`() {
        val today = LocalDate.of(2026, 11, 22)
        val period = statsPeriod(StatsMode.WEEK, today, null, null, today)
        val txs = listOf(
            tx(TxType.EXPENSE, 100, LocalDate.of(2026, 11, 16)),
            tx(TxType.INCOME, 500, LocalDate.of(2026, 11, 22)),
        )

        val buckets = txs.statsBuckets(period, BucketUnit.DAY)
        val totals = txs.statsTotals()

        assertEquals(7, buckets.size)
        assertEquals(100L, buckets.first().expense)
        assertEquals(500L, buckets.last().income)
        assertEquals(100L, totals.expense)
        assertEquals(500L, totals.income)
        assertEquals(1, totals.expenseCount)
        assertEquals(1, totals.incomeCount)
    }

    @Test
    fun `year buckets have one per month`() {
        val today = LocalDate.of(2026, 11, 22)
        val period = statsPeriod(StatsMode.YEAR, LocalDate.of(2025, 6, 1), null, null, today)
        val txs = listOf(
            tx(TxType.EXPENSE, 100, LocalDate.of(2025, 1, 5)),
            tx(TxType.EXPENSE, 200, LocalDate.of(2025, 12, 20)),
        )

        val buckets = txs.statsBuckets(period, BucketUnit.MONTH)

        assertEquals(12, buckets.size)
        assertEquals(100L, buckets.first().expense)
        assertEquals(200L, buckets.last().expense)
    }

    @Test
    fun `leap day is included once`() {
        val period = statsPeriod(
            StatsMode.RANGE,
            LocalDate.of(2024, 2, 20),
            LocalDate.of(2024, 2, 1),
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2026, 1, 1),
        )
        val txs = listOf(tx(TxType.EXPENSE, 100, LocalDate.of(2024, 2, 29)))

        val buckets = txs.statsBuckets(period, BucketUnit.DAY)

        assertEquals(29, buckets.size)
        assertEquals(100L, buckets.last().expense)
        assertEquals(100L, buckets.sumOf { it.expense })
    }

    @Test
    fun `uncategorized expense appears as 未分类 and totals match`() {
        val period = statsPeriod(
            StatsMode.RANGE,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 30),
            LocalDate.of(2026, 11, 30),
        )
        val categories = listOf(category("food", TxType.EXPENSE))
        val txs = listOf(
            tx(TxType.EXPENSE, 100, LocalDate.of(2026, 11, 2), "food"),
            tx(TxType.EXPENSE, 50, LocalDate.of(2026, 11, 3), null),
        )

        val stats = buildStats(txs, categories, period)

        assertEquals(2, stats.expenseCategories.size)
        assertEquals(50L, stats.expenseCategories.first { it.category.name == "未分类" }.amount)
        assertEquals(stats.totals.expense, stats.expenseCategories.sumOf { it.amount })
    }

    @Test
    fun `income categories are separate from expense categories`() {
        val period = statsPeriod(
            StatsMode.RANGE,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 30),
            LocalDate.of(2026, 11, 30),
        )
        val categories = listOf(
            category("food", TxType.EXPENSE),
            category("salary", TxType.INCOME),
        )
        val txs = listOf(
            tx(TxType.EXPENSE, 100, LocalDate.of(2026, 11, 2), "food"),
            tx(TxType.INCOME, 500, LocalDate.of(2026, 11, 3), "salary"),
        )

        val stats = buildStats(txs, categories, period)

        assertEquals(listOf("food"), stats.expenseCategories.map { it.category.id })
        assertEquals(listOf("salary"), stats.incomeCategories.map { it.category.id })
    }

    @Test
    fun `category shares sum to one`() {
        val period = statsPeriod(
            StatsMode.RANGE,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 30),
            LocalDate.of(2026, 11, 30),
        )
        val categories = listOf(
            category("a", TxType.EXPENSE),
            category("b", TxType.EXPENSE),
            category("c", TxType.EXPENSE),
        )
        val txs = listOf(
            tx(TxType.EXPENSE, 100, LocalDate.of(2026, 11, 1), "a"),
            tx(TxType.EXPENSE, 200, LocalDate.of(2026, 11, 2), "b"),
            tx(TxType.EXPENSE, 700, LocalDate.of(2026, 11, 3), "c"),
        )

        val stats = buildStats(txs, categories, period)

        assertEquals(1.0, stats.expenseCategories.sumOf { it.share }, 0.000001)
    }

    @Test
    fun `category delta is null when previous is zero`() {
        val period = statsPeriod(
            StatsMode.RANGE,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 20),
            LocalDate.of(2026, 11, 22),
        )
        val categories = listOf(category("a", TxType.EXPENSE))
        val txs = listOf(tx(TxType.EXPENSE, 100, LocalDate.of(2026, 11, 15), "a"))

        val stats = buildStats(txs, categories, period)

        assertNull(stats.expenseCategories.first().deltaRatio)
    }

    @Test
    fun `weekday totals map monday to index zero`() {
        val period = statsPeriod(
            StatsMode.RANGE,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 16),
            LocalDate.of(2026, 11, 22),
            LocalDate.of(2026, 11, 22),
        )
        val txs = listOf(
            tx(TxType.EXPENSE, 100, LocalDate.of(2026, 11, 16)), // Monday
            tx(TxType.EXPENSE, 200, LocalDate.of(2026, 11, 22)), // Sunday
        )

        val stats = buildStats(txs, emptyList(), period)

        assertEquals(100L, stats.weekdayExpense[0])
        assertEquals(200L, stats.weekdayExpense[6])
        assertEquals(1, stats.weekdayCount[0])
        assertEquals(1, stats.weekdayCount[6])
    }

    @Test
    fun `summary has total and average rows`() {
        val period = statsPeriod(
            StatsMode.RANGE,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 22),
        )
        val txs = listOf(tx(TxType.EXPENSE, 1000, LocalDate.of(2026, 11, 5)))

        val stats = buildStats(txs, emptyList(), period)

        val total = stats.summaryRows.first { it.isTotal }
        val average = stats.summaryRows.first { it.isAverage }
        assertEquals(1000L, total.expense)
        assertEquals("日均", average.label)
        assertEquals(100L, average.expense)
    }

    @Test
    fun `cumulative starts at zero and ends at net`() {
        val period = statsPeriod(
            StatsMode.RANGE,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 11, 22),
        )
        val txs = listOf(
            tx(TxType.EXPENSE, 100, LocalDate.of(2026, 11, 1)),
            tx(TxType.INCOME, 300, LocalDate.of(2026, 11, 2)),
            tx(TxType.EXPENSE, 50, LocalDate.of(2026, 11, 3)),
        )

        val stats = buildStats(txs, emptyList(), period)

        assertEquals(0L, stats.cumulative.first())
        assertEquals(150L, stats.cumulative.last())
        assertEquals(4, stats.cumulative.size)
    }

    @Test
    fun `max expense day picks the biggest natural day`() {
        val period = statsPeriod(
            StatsMode.RANGE,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 11, 22),
        )
        val txs = listOf(
            tx(TxType.EXPENSE, 100, LocalDate.of(2026, 11, 1)),
            tx(TxType.EXPENSE, 200, LocalDate.of(2026, 11, 1)),
            tx(TxType.EXPENSE, 250, LocalDate.of(2026, 11, 2)),
        )

        val stats = buildStats(txs, emptyList(), period)

        assertEquals(LocalDate.of(2026, 11, 1), stats.maxExpenseDayDate)
        assertEquals(300L, stats.maxExpenseDayAmount)
        assertTrue(stats.averageExpense > 0)
    }
}

