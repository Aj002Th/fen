package app.fen.data.query

import app.fen.data.Category
import app.fen.data.Tx
import app.fen.data.TxType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.round

enum class GroupBy { CATEGORY, DAY, MONTH, TYPE }

enum class SummaryType { EXPENSE, INCOME, ALL }

data class MoneyTotals(val expense: Long, val income: Long) {
    val net: Long get() = income - expense
}

data class GroupTotal(
    val key: String?,
    val label: String,
    val type: TxType,
    val amount: Long,
    val count: Int,
    val sharePercent: Double,
)

data class Summary(
    val totals: MoneyTotals,
    val count: Int,
    val previous: MoneyTotals?,
    val previousCount: Int?,
    val expenseChangePercent: Double?,
    val incomeChangePercent: Double?,
    val groups: List<GroupTotal>,
)

/**
 * totals 永远包含收支两侧，不受 [type] 影响；[type] 只过滤 groups。
 * previous 由调用方给区间（month 请求传上一个自然月），否则按等长区间向前推。
 */
fun summarize(
    txs: List<Tx>,
    categories: List<Category>,
    fromMillis: Long,
    toMillis: Long,
    groupBy: GroupBy,
    type: SummaryType,
    zone: ZoneId = ZoneId.systemDefault(),
    previousFromMillis: Long? = null,
    previousToMillis: Long? = null,
): Summary {
    val current = txs.filter { it.occurredAt in fromMillis..toMillis }
    val totals = current.totals()
    val count = current.size

    val prevRange = previousRange(fromMillis, toMillis, previousFromMillis, previousToMillis)
    val previousTxs = prevRange?.let { (from, to) -> txs.filter { it.occurredAt in from..to } }
    val previous = previousTxs?.totals()
    val previousCount = previousTxs?.size

    val groups = buildGroups(current, categories, groupBy, type, zone)

    return Summary(
        totals = totals,
        count = count,
        previous = previous,
        previousCount = previousCount,
        expenseChangePercent = changePercent(totals.expense, previous?.expense),
        incomeChangePercent = changePercent(totals.income, previous?.income),
        groups = groups,
    )
}

private fun List<Tx>.totals(): MoneyTotals {
    var expense = 0L
    var income = 0L
    for (tx in this) {
        when (tx.type) {
            TxType.EXPENSE -> expense += tx.amount
            TxType.INCOME -> income += tx.amount
        }
    }
    return MoneyTotals(expense, income)
}

private fun previousRange(
    from: Long,
    to: Long,
    explicitFrom: Long?,
    explicitTo: Long?,
): Pair<Long, Long>? {
    if (explicitFrom != null && explicitTo != null) return explicitFrom to explicitTo
    if (to < from) return null
    val length = to - from + 1
    val prevTo = from - 1
    return (prevTo - length + 1) to prevTo
}

private fun changePercent(current: Long, previous: Long?): Double? {
    if (previous == null || previous == 0L) return null
    return round((current - previous) * 1000.0 / previous) / 10.0
}

private fun buildGroups(
    txs: List<Tx>,
    categories: List<Category>,
    groupBy: GroupBy,
    type: SummaryType,
    zone: ZoneId,
): List<GroupTotal> {
    val selected = txs.filter {
        when (type) {
            SummaryType.EXPENSE -> it.type == TxType.EXPENSE
            SummaryType.INCOME -> it.type == TxType.INCOME
            SummaryType.ALL -> true
        }
    }
    if (selected.isEmpty()) return emptyList()

    val categoryMap = categories.associateBy { it.id }
    val buckets = linkedMapOf<Pair<String?, TxType>, MutableAmount>()
    for (tx in selected) {
        val key: String? = when (groupBy) {
            GroupBy.CATEGORY -> tx.categoryId
            GroupBy.DAY -> Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalDate().toString()
            GroupBy.MONTH -> YearMonth.from(
                Instant.ofEpochMilli(tx.occurredAt).atZone(zone)
            ).toString()
            GroupBy.TYPE -> tx.type.name.lowercase()
        }
        val bucket = buckets.getOrPut(key to tx.type) { MutableAmount() }
        bucket.amount += tx.amount
        bucket.count += 1
    }

    val typeTotals = selected.groupBy { it.type }.mapValues { (_, list) -> list.sumOf { it.amount } }
    val result = buckets.map { (keyAndType, amount) ->
        val (key, txType) = keyAndType
        val label = when (groupBy) {
            GroupBy.CATEGORY -> key?.let { categoryMap[it]?.name } ?: "未分类"
            GroupBy.DAY -> key ?: "未知日期"
            GroupBy.MONTH -> key ?: "未知月份"
            GroupBy.TYPE -> if (txType == TxType.EXPENSE) "支出" else "收入"
        }
        GroupTotal(
            key = key,
            label = label,
            type = txType,
            amount = amount.amount,
            count = amount.count,
            sharePercent = share(amount.amount, typeTotals[txType] ?: 0L),
        )
    }

    return when (groupBy) {
        GroupBy.CATEGORY -> result.sortedWith(
            compareByDescending<GroupTotal> { it.amount }.thenBy { it.type }
        )
        GroupBy.DAY, GroupBy.MONTH -> result.sortedWith(
            compareBy<GroupTotal> { it.key.orEmpty() }.thenBy { it.type }
        )
        GroupBy.TYPE -> result.sortedBy { it.type }
    }
}

private fun share(amount: Long, total: Long): Double {
    if (total == 0L) return 0.0
    return round(amount * 1000.0 / total) / 10.0
}

private class MutableAmount {
    var amount: Long = 0
    var count: Int = 0
}
