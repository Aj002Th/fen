package app.fen.data.query

import app.fen.data.Category
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.util.formatMoney

enum class Sort { TIME_DESC, AMOUNT_DESC }

/**
 * 时间边界用毫秒，和 UI 现有的筛选完全一致：
 * fromMillis 含、toMillis 含。
 */
data class TxFilter(
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val type: TxType? = null,
    val categoryId: String? = null,
    val keyword: String = "",
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
)

/**
 * UI 搜索和 MCP 工具共用的过滤口径。
 * keyword 保持 SearchScreen 现在的语义：备注、分类名、formatMoney 后的金额。
 */
fun List<Tx>.query(
    filter: TxFilter,
    categories: Map<String, Category>,
    sort: Sort = Sort.TIME_DESC,
): List<Tx> {
    val keyword = filter.keyword.trim().lowercase()
    val filtered = asSequence()
        .filter { filter.type == null || it.type == filter.type }
        .filter { filter.fromMillis == null || it.occurredAt >= filter.fromMillis }
        .filter { filter.toMillis == null || it.occurredAt <= filter.toMillis }
        .filter { filter.categoryId == null || it.categoryId == filter.categoryId }
        .filter { filter.minMinor == null || it.amount >= filter.minMinor }
        .filter { filter.maxMinor == null || it.amount <= filter.maxMinor }
        .filter { tx ->
            if (keyword.isEmpty()) {
                true
            } else {
                val categoryName = tx.categoryId?.let { categories[it] }?.name.orEmpty()
                tx.note.lowercase().contains(keyword) ||
                    categoryName.lowercase().contains(keyword) ||
                    formatMoney(tx.amount).lowercase().contains(keyword)
            }
        }
        .toList()

    return when (sort) {
        Sort.TIME_DESC -> filtered.sortedByDescending { it.occurredAt }
        Sort.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
    }
}
