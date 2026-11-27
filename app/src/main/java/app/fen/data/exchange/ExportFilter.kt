package app.fen.data.exchange

import app.fen.data.Tx
import app.fen.data.TxType
import java.time.LocalDate
import java.time.ZoneId

/** 导出页的收支范围。账本没有账户，范围指收支类型。 */
enum class ExportScope { ALL, EXPENSE, INCOME }

/**
 * 导出明细的过滤条件。
 *
 * [from] / [to] 为 null 表示不限起始或结束日期（含当天）。
 * [categoryIds] 为 null 表示不按分类过滤；空集合表示一笔都不要。
 * 未分类流水（categoryId == null）只在 [categoryIds] 为 null 时保留。
 */
data class ExportFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val scope: ExportScope = ExportScope.ALL,
    val categoryIds: Set<String>? = null,
)

fun List<Tx>.filterForExport(
    filter: ExportFilter,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Tx> {
    val start = filter.from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    val endExclusive = filter.to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    return filter { tx ->
        if (start != null && tx.occurredAt < start) return@filter false
        if (endExclusive != null && tx.occurredAt >= endExclusive) return@filter false
        when (filter.scope) {
            ExportScope.ALL -> Unit
            ExportScope.EXPENSE -> if (tx.type != TxType.EXPENSE) return@filter false
            ExportScope.INCOME -> if (tx.type != TxType.INCOME) return@filter false
        }
        val ids = filter.categoryIds
        if (ids != null && (tx.categoryId == null || tx.categoryId !in ids)) return@filter false
        true
    }
}
