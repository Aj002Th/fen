package app.fen.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
        val raw = tx.categoryId?.let { byId[it] } ?: continue
        // 统计按一级分类聚合，避免叶子分类把图表切得过碎
        val category = raw.parentId?.let { byId[it] } ?: raw
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
