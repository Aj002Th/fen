package app.fen.data

/** 只保留支出 / 收入。转账、退款等不在范围内。 */
enum class TxType { EXPENSE, INCOME }

/**
 * 分类采用两级结构：parentId == null 为一级分类，其余挂在父类下。
 * icon / color 只存 key 和 ARGB，换图标集时不用改数据。
 */
data class Category(
    val id: String,
    val parentId: String?,
    val name: String,
    val type: TxType,
    val icon: String,
    val color: Long,
    val sortOrder: Int,
) {
    val isParent: Boolean get() = parentId == null
}

data class Tx(
    val id: String,
    val type: TxType,
    val amount: Long,
    val categoryId: String?,
    val note: String,
    val occurredAt: Long,
)

data class Snapshot(
    val categories: List<Category> = emptyList(),
    val transactions: List<Tx> = emptyList(),
)
