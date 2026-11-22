package app.fen.data

import app.fen.data.ledger.AddCommit
import app.fen.data.ledger.BatchSummary
import app.fen.data.ledger.CommitResult
import app.fen.data.ledger.DeleteCommit
import app.fen.data.ledger.IdempotencyRecord
import app.fen.data.ledger.LedgerReader
import app.fen.data.ledger.LedgerWriter
import app.fen.data.ledger.UndoResult
import app.fen.data.ledger.UpdateCommit
import app.fen.data.ledger.WriteFailure
import app.fen.data.ledger.toLedgerJson
import app.fen.data.ledger.txFromLedgerJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 目前数据量很小，整库加载进内存 + 写后刷新即可。
 * 等账单量级上来后再换成增量查询。
 *
 * MCP 的写入、撤销前像和幂等结果都在 fen.db 的同一个事务里提交，
 * 由 [writeLock] 和 UI 写入串行化。
 */
class FenRepository(private val db: FenDb) : LedgerReader, LedgerWriter {

    private val writeLock = Any()
    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    fun refresh() {
        _snapshot.value = load()
    }

    override fun readSnapshot(): Snapshot = snapshot.value

    fun addTransaction(
        type: TxType,
        amountMinor: Long,
        categoryId: String?,
        note: String,
        occurredAt: Long,
    ) {
        synchronized(writeLock) {
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
    }

    fun updateTransaction(tx: Tx) {
        synchronized(writeLock) {
            db.updateTx(tx)
            refresh()
        }
    }

    fun deleteTransaction(id: String) {
        synchronized(writeLock) {
            db.deleteTx(id)
            refresh()
        }
    }

    fun addCategory(name: String, type: TxType, icon: String, color: Long) {
        synchronized(writeLock) {
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
    }

    fun updateCategory(category: Category) {
        synchronized(writeLock) {
            db.updateCategory(category)
            refresh()
        }
    }

    fun deleteCategory(id: String) {
        synchronized(writeLock) {
            db.deleteCategory(id)
            refresh()
        }
    }

    /** 转移流水并删除分类 */
    fun mergeCategory(fromId: String, intoId: String) {
        synchronized(writeLock) {
            db.reassignCategory(fromId, intoId)
            db.deleteCategory(fromId)
            refresh()
        }
    }

    /** 删除分类以及它名下的所有流水 */
    fun deleteCategoryWithTransactions(id: String) {
        synchronized(writeLock) {
            db.deleteCategoryWithTransactions(id)
            refresh()
        }
    }

    fun transactionCount(categoryId: String): Int = db.countTransactions(categoryId)

    /** 开发/验收用：生成演示数据 */
    fun seedDemoData(count: Int = 400) {
        synchronized(writeLock) {
            db.seedDemoData(count)
            refresh()
        }
    }

    /** 开发/验收用：清空流水（保留分类） */
    fun clearTransactions() {
        synchronized(writeLock) {
            db.clearTransactions()
            refresh()
        }
    }

    override fun idempotency(key: String): IdempotencyRecord? = db.idempotency(key)

    override fun commitAdd(commit: AddCommit): CommitResult = synchronized(writeLock) {
        replayOrNull(commit.meta.idempotencyKey, commit.meta.tool, commit.meta.argsHash)?.let { return it }

        val now = System.currentTimeMillis()
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            validateCategories(commit.items)
            commit.items.forEach { db.insertTx(it) }
            db.insertMcpBatch(commit.meta, now)
            commit.items.forEach {
                db.insertMcpBatchItem(commit.meta.batchId, it.id, null, it.toLedgerJson())
            }
            db.insertIdempotency(commit.meta, now)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        refresh()
        CommitResult(commit.items.map { it.id })
    }

    override fun commitUpdate(commit: UpdateCommit): CommitResult = synchronized(writeLock) {
        replayOrNull(commit.meta.idempotencyKey, commit.meta.tool, commit.meta.argsHash)?.let { return it }

        val now = System.currentTimeMillis()
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            val current = db.txById(commit.before.id)
                ?: throw WriteFailure.WriteConflict("Transaction no longer exists")
            if (current != commit.before) {
                throw WriteFailure.WriteConflict("Transaction changed since it was read")
            }
            validateCategories(listOf(commit.after))
            db.updateTx(commit.after)
            db.insertMcpBatch(commit.meta, now)
            db.insertMcpBatchItem(
                commit.meta.batchId,
                commit.after.id,
                commit.before.toLedgerJson(),
                commit.after.toLedgerJson(),
            )
            db.insertIdempotency(commit.meta, now)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        refresh()
        CommitResult(listOf(commit.after.id))
    }

    override fun commitDelete(commit: DeleteCommit): CommitResult = synchronized(writeLock) {
        replayOrNull(commit.meta.idempotencyKey, commit.meta.tool, commit.meta.argsHash)?.let { return it }

        val now = System.currentTimeMillis()
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            commit.before.forEach { before ->
                val current = db.txById(before.id)
                    ?: throw WriteFailure.WriteConflict("Transaction no longer exists: ${before.id}")
                if (current != before) {
                    throw WriteFailure.WriteConflict("Transaction changed since it was read: ${before.id}")
                }
            }
            commit.before.forEach {
                db.deleteTx(it.id)
                db.insertMcpBatchItem(commit.meta.batchId, it.id, it.toLedgerJson(), null)
            }
            db.insertMcpBatch(commit.meta, now)
            db.insertIdempotency(commit.meta, now)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        refresh()
        CommitResult(commit.before.map { it.id })
    }

    override fun undoBatch(batchId: String): UndoResult = synchronized(writeLock) {
        val now = System.currentTimeMillis()
        val batch = db.mcpBatch(batchId) ?: return UndoResult.NotFound(batchId)
        if (batch.undoneAt != null) return UndoResult.Conflict("Batch already undone")
        if (now - batch.createdAt > UNDO_WINDOW_MS) return UndoResult.Conflict("Undo window expired")

        val items = db.mcpBatchItems(batchId)
        val database = db.writableDatabase
        var result: UndoResult = UndoResult.Ok(batchId)
        database.beginTransaction()
        try {
            items.forEach { item ->
                val current = db.txById(item.txId)
                val after = item.afterJson?.let { txFromLedgerJson(it) }
                if (after != null && current != after) {
                    throw WriteFailure.WriteConflict("Transaction changed after the batch: ${item.txId}")
                }
                val before = item.beforeJson?.let { txFromLedgerJson(it) }
                when {
                    before == null -> db.deleteTx(item.txId)
                    current == null -> db.insertTx(before)
                    else -> db.updateTx(before)
                }
            }
            db.markBatchUndone(batchId, now)
            batch.idempotencyKey?.let { db.invalidateIdempotency(it, now) }
            database.setTransactionSuccessful()
        } catch (e: WriteFailure) {
            result = UndoResult.Conflict(e.message ?: "Undo conflict")
        } finally {
            database.endTransaction()
        }
        if (result is UndoResult.Ok) refresh()
        return result
    }

    override fun batch(batchId: String): BatchSummary? = db.mcpBatch(batchId)

    private fun replayOrNull(key: String, tool: String, argsHash: String): CommitResult? {
        val existing = db.idempotency(key) ?: return null
        if (existing.invalidatedAt != null) {
            throw WriteFailure.IdempotencyInvalidated("Request key was undone")
        }
        if (existing.tool != tool || existing.argsHash != argsHash) {
            throw WriteFailure.IdempotencyConflict("Request key reused with different arguments")
        }
        return CommitResult(emptyList(), replayed = true)
    }

    private fun validateCategories(items: List<Tx>) {
        if (items.none { it.categoryId != null }) return
        val categories = db.categories().associateBy { it.id }
        items.forEach { tx ->
            val categoryId = tx.categoryId ?: return@forEach
            val category = categories[categoryId]
                ?: throw WriteFailure.Validation("Category not found: $categoryId")
            if (category.type != tx.type) {
                throw WriteFailure.Validation("Category type does not match transaction type")
            }
        }
    }

    private fun load(): Snapshot = Snapshot(
        categories = db.categories(),
        transactions = db.transactions(),
    )

    private companion object {
        const val UNDO_WINDOW_MS = 24L * 60 * 60 * 1000
    }
}
