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
    /** 导入批次的 id；手工记录为 null。用于整批撤销。 */
    val importBatch: String? = null,
)

/** 一次批量导入（iCost / CSV）的记录，用于「导入记录」与整批撤销。 */
data class ImportBatch(
    val id: String,
    val source: String,
    val label: String,
    val createdAt: Long,
    val rows: Int,
    /** 当前仍属于这个批次的流水数（编辑/转移分类不会减少；删除流水或删除分类会）。 */
    val remaining: Int = rows,
    /** 导入文件的 SHA-256；批次还在时才用于「这个文件之前导入过」的提示。 */
    val fileHash: String? = null,
)

data class Snapshot(
    val categories: List<Category> = emptyList(),
    val transactions: List<Tx> = emptyList(),
)
