package app.fen.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 目前数据量很小，整库加载进内存 + 写后刷新即可。
 * 等账单量级上来后再换成增量查询。
 */
class FenRepository(private val db: FenDb) {

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
}
