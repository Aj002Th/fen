package app.fen.data

import java.time.LocalDate
import java.time.YearMonth

fun Account.balance(transactions: List<Tx>): Long {
    var total = initialBalance
    for (tx in transactions) {
        when (tx.type) {
            TxType.EXPENSE -> if (tx.accountId == id) total -= tx.amount
            TxType.INCOME -> if (tx.accountId == id) total += tx.amount
            TxType.TRANSFER -> {
                if (tx.accountId == id) total -= tx.amount
                if (tx.toAccountId == id) total += tx.amount
            }
        }
    }
    return total
}

fun List<Tx>.inMonth(month: YearMonth): List<Tx> {
    return filter { YearMonth.from(it.occurredAt.toDate()) == month }
}

data class MonthTotals(val expense: Long, val income: Long) {
    val net: Long get() = income - expense
}

fun List<Tx>.totals(): MonthTotals {
    var expense = 0L
    var income = 0L
    for (tx in this) {
        when (tx.type) {
            TxType.EXPENSE -> expense += tx.amount
            TxType.INCOME -> income += tx.amount
            TxType.TRANSFER -> Unit
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
        val key = tx.categoryId ?: "unknown"
        totals[key] = (totals[key] ?: 0L) + tx.amount
    }
    return totals
        .mapNotNull { (id, amount) ->
            val category = byId[id] ?: return@mapNotNull null
            CategoryTotal(category, amount)
        }
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

fun Long.toDate(): LocalDate = java.time.Instant.ofEpochMilli(this)
    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
