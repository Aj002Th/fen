package app.fen.data

import app.fen.autobook.classify.AutoMapStore
import app.fen.data.exchange.ImportPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 目前数据量很小，整库加载进内存 + 写后刷新即可。
 * 等账单量级上来后再换成增量查询。
 */
class FenRepository(
    private val db: FenDb,
    private val autoMap: AutoMapStore,
) {

    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private fun load(): Snapshot = Snapshot(
        categories = db.categories(),
        transactions = db.transactions(),
    )

    fun refresh() {
        _snapshot.value = load()
    }

    fun addTransaction(
        type: TxType,
        amountMinor: Long,
        categoryId: String?,
        note: String,
        occurredAt: Long,
    ) {
        db.insertTx(
            Tx(
                id = UUID.randomUUID().toString(),
                type = type,
                amount = amountMinor,
                categoryId = categoryId,
                note = note.trim(),
                occurredAt = occurredAt,
            )
        )
        refresh()
    }

    fun updateTransaction(tx: Tx) {
        db.updateTx(tx)
        refresh()
    }

    fun deleteTransaction(id: String) {
        db.deleteTx(id)
        refresh()
    }

    fun addCategory(name: String, type: TxType, icon: String, color: Long) {
        db.insertCategory(
            Category(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                type = type,
                icon = icon,
                color = color,
                sortOrder = db.nextCategoryOrder(),
            )
        )
        refresh()
    }

    fun updateCategory(category: Category) {
        db.updateCategory(category)
        refresh()
    }

    /** 记账页读的就是这个顺序，写完立刻生效。 */
    fun reorderCategories(type: TxType, orderedIds: List<String>) {
        db.reorderCategories(type, orderedIds)
        refresh()
    }

    fun deleteCategory(id: String) {
        autoMap.deleteForCategory(id)
        db.deleteCategory(id)
        refresh()
    }

    /** 转移流水并删除分类 */
    fun mergeCategory(fromId: String, intoId: String) {
        db.reassignCategory(fromId, intoId)
        autoMap.updateCategory(fromId, intoId)
        db.deleteCategory(fromId)
        refresh()
    }

    /** 删除分类以及它名下的所有流水 */
    fun deleteCategoryWithTransactions(id: String) {
        autoMap.deleteForCategory(id)
        db.deleteCategoryWithTransactions(id)
        refresh()
    }

    fun transactionCount(categoryId: String): Int = db.countTransactions(categoryId)

    fun nextCategoryOrder(): Int = db.nextCategoryOrder()

    /** 写入一次导入（分类 + 流水 + 批次），单事务完成。 */
    fun applyImport(plan: ImportPlan, batch: ImportBatch) {
        if (plan.transactions.isEmpty()) return
        db.insertImport(plan.newCategories, plan.transactions, batch)
        refresh()
    }

    /** 恢复 Fen JSON 备份：整体替换（覆盖）现有分类与流水。 */
    fun restoreBackup(backup: FenBackup) {
        db.replaceAll(backup.categories, backup.transactions)
        refresh()
    }

    fun importBatches(): List<ImportBatch> = db.importBatches()

    fun hasImportHash(hash: String): Boolean = db.hasImportHash(hash)

    fun ensureUncategorized(type: TxType): String = db.ensureUncategorized(type)

    /** 撤销一次导入：删除该批次写入的流水与批次记录（保留导入时新建的空分类）。 */
    fun deleteImportBatch(id: String) {
        db.deleteImportBatch(id)
        refresh()
    }

    /** 开发/验收用：生成演示数据 */
    fun seedDemoData(count: Int = 400, daysBack: Int = 180) {
        db.seedDemoData(count, daysBack)
        refresh()
    }

    /** 开发/验收用：清空流水（保留分类） */
    fun clearTransactions() {
        db.clearTransactions()
        refresh()
    }
}
