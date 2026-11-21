package app.fen.data

/** 只保留支出 / 收入。 */
enum class TxType { EXPENSE, INCOME }

/**
 * 单层分类：不做父子层级，靠用户自己维护一小组常用分类。
 * icon / color 只存 key 和 ARGB，换图标集时不用改数据。
 */
data class Category(
    val id: String,
    val name: String,
    val type: TxType,
    val icon: String,
    val color: Long,
    val sortOrder: Int,
)

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
