package app.fen.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class MonthTotals(val expense: Long, val income: Long) {
    val net: Long get() = income - expense
}

fun List<Tx>.inMonth(month: YearMonth): List<Tx> =
    filter { YearMonth.from(it.occurredAt.toDate()) == month }

fun List<Tx>.totals(): MonthTotals {
    var expense = 0L
    var income = 0L
    for (tx in this) {
        when (tx.type) {
            TxType.EXPENSE -> expense += tx.amount
            TxType.INCOME -> income += tx.amount
        }
    }
    return MonthTotals(expense, income)
}

data class CategoryTotal(val category: Category, val amount: Long)

fun List<Tx>.expenseByCategory(categories: List<Category>): List<CategoryTotal> {
    val byId = categories.associateBy { it.id }
    val totals = linkedMapOf<String, Long>()
    for (tx in this) {
        if (tx.type != TxType.EXPENSE) continue
        val category = tx.categoryId?.let { byId[it] } ?: continue
        totals[category.id] = (totals[category.id] ?: 0L) + tx.amount
    }
    return totals
        .map { (id, amount) -> CategoryTotal(byId.getValue(id), amount) }
        .sortedByDescending { it.amount }
}

data class DayGroup(val date: LocalDate, val items: List<Tx>, val totals: MonthTotals)

fun List<Tx>.groupByDay(): List<DayGroup> = groupBy { it.occurredAt.toDate() }
    .toSortedMap(compareByDescending { it })
    .map { (date, items) -> DayGroup(date, items.sortedByDescending { it.occurredAt }, items.totals()) }

/** 近 n 天的支出合计，用于趋势图 */
fun List<Tx>.dailyExpense(days: List<LocalDate>): List<Long> {
    val byDay = filter { it.type == TxType.EXPENSE }.groupBy { it.occurredAt.toDate() }
    return days.map { day -> byDay[day]?.sumOf { it.amount } ?: 0L }
}

fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault()).toLocalDate()

/* ------------------------------- 统计看板 ------------------------------- */

enum class StatsMode { WEEK, MONTH, YEAR, ALL, RANGE }

enum class BucketUnit { DAY, MONTH, YEAR }

/**
 * 统计区间。起止日均包含。
 * previous 为上一周期（同长度）；全部范围没有上一周期。
 */
data class StatsPeriod(
    val mode: StatsMode,
    val start: LocalDate,
    val end: LocalDate,
    val previousStart: LocalDate?,
    val previousEnd: LocalDate?,
) {
    val days: Int get() = (ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)
    val months: Int get() = ((end.year - start.year) * 12 + (end.monthValue - start.monthValue) + 1).coerceAtLeast(1)
    val years: Int get() = (end.year - start.year + 1).coerceAtLeast(1)
}

private fun minDate(a: LocalDate, b: LocalDate): LocalDate = if (a <= b) a else b
private fun maxDate(a: LocalDate, b: LocalDate): LocalDate = if (a >= b) a else b

fun statsPeriod(
    mode: StatsMode,
    anchor: LocalDate,
    customStart: LocalDate?,
    customEnd: LocalDate?,
    today: LocalDate = LocalDate.now(),
    earliest: LocalDate? = null,
): StatsPeriod {
    val start: LocalDate
    val end: LocalDate
    when (mode) {
        StatsMode.WEEK -> {
            start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            end = maxDate(minDate(start.plusDays(6), today), start)
        }
        StatsMode.MONTH -> {
            start = anchor.withDayOfMonth(1)
            end = maxDate(minDate(anchor.with(TemporalAdjusters.lastDayOfMonth()), today), start)
        }
        StatsMode.YEAR -> {
            start = LocalDate.of(anchor.year, 1, 1)
            end = maxDate(minDate(LocalDate.of(anchor.year, 12, 31), today), start)
        }
        StatsMode.ALL -> {
            start = earliest ?: today
            end = maxDate(today, start)
        }
        StatsMode.RANGE -> {
            val a = minDate(customStart ?: today.minusDays(29), today)
            val b = minDate(customEnd ?: today, today)
            start = minDate(a, b)
            end = maxDate(a, b)
        }
    }
    val previous = previousPeriod(mode, start, end)
    return StatsPeriod(mode, start, end, previous?.first, previous?.second)
}

private fun previousPeriod(mode: StatsMode, start: LocalDate, end: LocalDate): Pair<LocalDate, LocalDate>? {
    if (mode == StatsMode.ALL) return null
    val length = ChronoUnit.DAYS.between(start, end) + 1
    val prevStart = when (mode) {
        StatsMode.WEEK -> start.minusWeeks(1)
        StatsMode.MONTH -> start.minusMonths(1)
        StatsMode.YEAR -> start.minusYears(1)
        StatsMode.RANGE -> start.minusDays(length)
        StatsMode.ALL -> return null
    }
    val naturalEnd = when (mode) {
        StatsMode.WEEK -> prevStart.plusDays(6)
        StatsMode.MONTH -> prevStart.with(TemporalAdjusters.lastDayOfMonth())
        StatsMode.YEAR -> prevStart.with(TemporalAdjusters.lastDayOfYear())
        StatsMode.RANGE -> start.minusDays(1)
        StatsMode.ALL -> return null
    }
    val prevEnd = minDate(prevStart.plusDays(length - 1), naturalEnd)
    return prevStart to prevEnd
}

fun List<Tx>.inPeriod(period: StatsPeriod): List<Tx> = filter {
    val date = it.occurredAt.toDate()
    date >= period.start && date <= period.end
}

data class StatsTotals(
    val expense: Long,
    val income: Long,
    val count: Int,
    val expenseCount: Int,
    val incomeCount: Int,
) {
    val net: Long get() = income - expense
}

fun List<Tx>.statsTotals(): StatsTotals {
    var expense = 0L
    var income = 0L
    var expenseCount = 0
    var incomeCount = 0
    for (tx in this) {
        when (tx.type) {
            TxType.EXPENSE -> {
                expense += tx.amount
                expenseCount += 1
            }
            TxType.INCOME -> {
                income += tx.amount
                incomeCount += 1
            }
        }
    }
    return StatsTotals(expense, income, expenseCount + incomeCount, expenseCount, incomeCount)
}

fun StatsPeriod.bucketUnit(): BucketUnit = when (mode) {
    StatsMode.WEEK, StatsMode.MONTH -> BucketUnit.DAY
    StatsMode.YEAR -> BucketUnit.MONTH
    StatsMode.ALL -> when {
        days <= 62 -> BucketUnit.DAY
        months <= 24 -> BucketUnit.MONTH
        else -> BucketUnit.YEAR
    }
    StatsMode.RANGE -> when {
        days <= 62 -> BucketUnit.DAY
        months <= 24 -> BucketUnit.MONTH
        else -> BucketUnit.YEAR
    }
}

/** 趋势图比汇总表更细：全部/长范围优先按月，避免只有两三根柱子。 */
fun StatsPeriod.trendBucketUnit(): BucketUnit = when (mode) {
    StatsMode.WEEK, StatsMode.MONTH -> BucketUnit.DAY
    StatsMode.YEAR -> BucketUnit.MONTH
    StatsMode.ALL -> when {
        days <= 62 -> BucketUnit.DAY
        months <= 36 -> BucketUnit.MONTH
        else -> BucketUnit.YEAR
    }
    StatsMode.RANGE -> when {
        days <= 62 -> BucketUnit.DAY
        months <= 36 -> BucketUnit.MONTH
        else -> BucketUnit.YEAR
    }
}

data class StatsBucket(
    val start: LocalDate,
    val end: LocalDate,
    val label: String,
    val tableLabel: String,
    val expense: Long,
    val income: Long,
    val count: Int,
) {
    val net: Long get() = income - expense
}

fun List<Tx>.statsBuckets(period: StatsPeriod, unit: BucketUnit): List<StatsBucket> {
    val expenseByDay = HashMap<LocalDate, Long>()
    val incomeByDay = HashMap<LocalDate, Long>()
    val countByDay = HashMap<LocalDate, Int>()
    for (tx in this) {
        val date = tx.occurredAt.toDate()
        if (date < period.start || date > period.end) continue
        countByDay[date] = (countByDay[date] ?: 0) + 1
        when (tx.type) {
            TxType.EXPENSE -> expenseByDay[date] = (expenseByDay[date] ?: 0L) + tx.amount
            TxType.INCOME -> incomeByDay[date] = (incomeByDay[date] ?: 0L) + tx.amount
        }
    }

    fun sumRange(from: LocalDate, to: LocalDate): Triple<Long, Long, Int> {
        var expense = 0L
        var income = 0L
        var count = 0
        var day = from
        while (!day.isAfter(to)) {
            expense += expenseByDay[day] ?: 0L
            income += incomeByDay[day] ?: 0L
            count += countByDay[day] ?: 0
            day = day.plusDays(1)
        }
        return Triple(expense, income, count)
    }

    val result = mutableListOf<StatsBucket>()
    when (unit) {
        BucketUnit.DAY -> {
            var day = period.start
            while (!day.isAfter(period.end)) {
                val expense = expenseByDay[day] ?: 0L
                val income = incomeByDay[day] ?: 0L
                val count = countByDay[day] ?: 0
                result += StatsBucket(
                    start = day,
                    end = day,
                    label = "${day.monthValue}/${day.dayOfMonth}",
                    tableLabel = "${day.monthValue}月${day.dayOfMonth}日",
                    expense = expense,
                    income = income,
                    count = count,
                )
                day = day.plusDays(1)
            }
        }
        BucketUnit.MONTH -> {
            var month = YearMonth.from(period.start)
            val lastMonth = YearMonth.from(period.end)
            while (!month.isAfter(lastMonth)) {
                val from = maxDate(month.atDay(1), period.start)
                val to = minDate(month.atEndOfMonth(), period.end)
                val (expense, income, count) = sumRange(from, to)
                result += StatsBucket(
                    start = from,
                    end = to,
                    label = if (period.start.year != period.end.year) {
                        "${month.year % 100}/${month.monthValue}"
                    } else {
                        "${month.monthValue}月"
                    },
                    tableLabel = "${month.year}年${month.monthValue}月",
                    expense = expense,
                    income = income,
                    count = count,
                )
                month = month.plusMonths(1)
            }
        }
        BucketUnit.YEAR -> {
            for (year in period.start.year..period.end.year) {
                val from = maxDate(LocalDate.of(year, 1, 1), period.start)
                val to = minDate(LocalDate.of(year, 12, 31), period.end)
                val (expense, income, count) = sumRange(from, to)
                result += StatsBucket(
                    start = from,
                    end = to,
                    label = "$year",
                    tableLabel = "${year}年",
                    expense = expense,
                    income = income,
                    count = count,
                )
            }
        }
    }
    return result
}

data class CategoryStat(
    val category: Category,
    val amount: Long,
    val count: Int,
    val share: Double,
    val previousAmount: Long,
) {
    val average: Long get() = if (count > 0) amount / count else 0L
    val delta: Long get() = amount - previousAmount
    val deltaRatio: Double? get() = if (previousAmount > 0) delta.toDouble() / previousAmount else null
}

private val UncategorizedExpense = Category(
    id = "__none_expense__",
    name = "未分类",
    type = TxType.EXPENSE,
    icon = "other",
    color = 0xFFB7BCC5,
    sortOrder = Int.MAX_VALUE,
)

private val UncategorizedIncome = Category(
    id = "__none_income__",
    name = "未分类",
    type = TxType.INCOME,
    icon = "other",
    color = 0xFFB7BCC5,
    sortOrder = Int.MAX_VALUE,
)

private fun uncategorized(type: TxType): Category =
    if (type == TxType.EXPENSE) UncategorizedExpense else UncategorizedIncome

fun List<Tx>.categoryStats(
    type: TxType,
    categories: List<Category>,
    previous: List<Tx> = emptyList(),
): List<CategoryStat> {
    val byId = categories.associateBy { it.id }
    val amounts = linkedMapOf<String, Long>()
    val counts = linkedMapOf<String, Int>()
    val resolved = linkedMapOf<String, Category>()

    for (tx in this) {
        if (tx.type != type) continue
        val category = tx.categoryId?.let { byId[it] } ?: uncategorized(type)
        resolved[category.id] = category
        amounts[category.id] = (amounts[category.id] ?: 0L) + tx.amount
        counts[category.id] = (counts[category.id] ?: 0) + 1
    }

    val previousAmounts = linkedMapOf<String, Long>()
    for (tx in previous) {
        if (tx.type != type) continue
        val category = tx.categoryId?.let { byId[it] } ?: uncategorized(type)
        previousAmounts[category.id] = (previousAmounts[category.id] ?: 0L) + tx.amount
    }

    val total = amounts.values.sum()
    return amounts.map { (id, amount) ->
        val category = resolved[id] ?: uncategorized(type)
        CategoryStat(
            category = category,
            amount = amount,
            count = counts[id] ?: 0,
            share = if (total > 0) amount.toDouble() / total else 0.0,
            previousAmount = previousAmounts[id] ?: 0L,
        )
    }.sortedByDescending { it.amount }
}

data class SummaryRow(
    val label: String,
    val expense: Long,
    val income: Long,
    val count: Int,
    val isTotal: Boolean = false,
    val isAverage: Boolean = false,
) {
    val net: Long get() = income - expense
}

data class StatsBundle(
    val period: StatsPeriod,
    val totals: StatsTotals,
    val previousTotals: StatsTotals?,
    val unit: BucketUnit,
    val buckets: List<StatsBucket>,
    val trendUnit: BucketUnit,
    val trendBuckets: List<StatsBucket>,
    val expenseCategories: List<CategoryStat>,
    val incomeCategories: List<CategoryStat>,
    val weekdayExpense: List<Long>,
    val weekdayCount: List<Int>,
    val summaryRows: List<SummaryRow>,
    val cumulative: List<Long>,
    val maxExpenseDayDate: LocalDate?,
    val maxExpenseDayAmount: Long,
    val averageLabel: String,
    val averageExpense: Long,
)

fun buildStats(
    txs: List<Tx>,
    categories: List<Category>,
    period: StatsPeriod,
    summaryShowEmpty: Boolean = false,
): StatsBundle {
    val current = txs.inPeriod(period)
    val previous = if (period.previousStart != null && period.previousEnd != null) {
        val start = period.previousStart
        val end = period.previousEnd
        txs.filter {
            val date = it.occurredAt.toDate()
            date >= start && date <= end
        }
    } else {
        emptyList()
    }

    val totals = current.statsTotals()
    val previousTotals = if (period.previousStart != null) previous.statsTotals() else null
    val unit = period.bucketUnit()
    val buckets = current.statsBuckets(period, unit)
    val trendUnit = period.trendBucketUnit()
    val trendBuckets = if (trendUnit == unit) buckets else current.statsBuckets(period, trendUnit)
    val expenseCategories = current.categoryStats(TxType.EXPENSE, categories, previous)
    val incomeCategories = current.categoryStats(TxType.INCOME, categories, previous)

    val weekdayExpense = LongArray(7)
    val weekdayCount = IntArray(7)
    val expenseByDay = LinkedHashMap<LocalDate, Long>()
    for (tx in current) {
        val date = tx.occurredAt.toDate()
        val weekday = date.dayOfWeek.value - 1
        if (weekday in 0..6) {
            weekdayCount[weekday] += 1
            if (tx.type == TxType.EXPENSE) weekdayExpense[weekday] += tx.amount
        }
        if (tx.type == TxType.EXPENSE) {
            expenseByDay[date] = (expenseByDay[date] ?: 0L) + tx.amount
        }
    }

    val rows = mutableListOf<SummaryRow>()
    val keepEmpty = summaryShowEmpty || (unit == BucketUnit.DAY && period.days <= 7)
    val visibleBuckets = if (keepEmpty) buckets else buckets.filter { it.count > 0 }
    visibleBuckets.forEach { bucket ->
        rows += SummaryRow(
            label = bucket.tableLabel,
            expense = bucket.expense,
            income = bucket.income,
            count = bucket.count,
        )
    }
    rows += SummaryRow(
        label = "总计",
        expense = totals.expense,
        income = totals.income,
        count = totals.count,
        isTotal = true,
    )
    val divisor = when (unit) {
        BucketUnit.DAY -> period.days
        BucketUnit.MONTH -> period.months
        BucketUnit.YEAR -> period.years
    }.coerceAtLeast(1)
    rows += SummaryRow(
        label = when (unit) {
            BucketUnit.DAY -> "日均"
            BucketUnit.MONTH -> "月均"
            BucketUnit.YEAR -> "年均"
        },
        expense = totals.expense / divisor,
        income = totals.income / divisor,
        count = totals.count / divisor,
        isAverage = true,
    )

    val cumulative = ArrayList<Long>(trendBuckets.size + 1)
    cumulative += 0L
    var running = 0L
    trendBuckets.forEach { bucket ->
        running += bucket.net
        cumulative += running
    }

    val maxDay = expenseByDay.maxByOrNull { it.value }
    val averageDivisor = (if (period.days <= 31) period.days else period.months).coerceAtLeast(1)

    return StatsBundle(
        period = period,
        totals = totals,
        previousTotals = previousTotals,
        unit = unit,
        buckets = buckets,
        trendUnit = trendUnit,
        trendBuckets = trendBuckets,
        expenseCategories = expenseCategories,
        incomeCategories = incomeCategories,
        weekdayExpense = weekdayExpense.toList(),
        weekdayCount = weekdayCount.toList(),
        summaryRows = rows,
        cumulative = cumulative,
        maxExpenseDayDate = maxDay?.key,
        maxExpenseDayAmount = maxDay?.value ?: 0L,
        averageLabel = if (period.days <= 31) "日均支出" else "月均支出",
        averageExpense = totals.expense / averageDivisor,
    )
}
