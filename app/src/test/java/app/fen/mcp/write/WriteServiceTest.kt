package app.fen.mcp.write

import app.fen.data.Category
import app.fen.data.Snapshot
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.data.ledger.AddCommit
import app.fen.data.ledger.BatchSummary
import app.fen.data.ledger.CommitResult
import app.fen.data.ledger.DeleteCommit
import app.fen.data.ledger.IdempotencyRecord
import app.fen.data.ledger.LedgerReader
import app.fen.data.ledger.LedgerWriter
import app.fen.data.ledger.UndoResult
import app.fen.data.ledger.UpdateCommit
import app.fen.mcp.tools.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteServiceTest {

    private val food = Category("c-food", "餐饮", TxType.EXPENSE, "food", 0L, 0)
    private val salary = Category("c-salary", "工资", TxType.INCOME, "salary", 0L, 1)
    private val categories = listOf(food, salary)
    private val ledger = FakeLedger(categories)
    private var now = 1_700_000_000_000L
    private var idSeq = 0
    private val service = WriteService(ledger, ledger, { now }, { "id-${++idSeq}" })

    private fun tx(
        id: String = "tx-1",
        type: TxType = TxType.EXPENSE,
        amount: Long = 3_200,
        categoryId: String? = food.id,
    ) = Tx(id, type, amount, categoryId, "", now)

    private fun addArgs(
        key: String = "request-1",
        type: String = "expense",
        amount: String = "32.00",
        categoryId: String? = food.id,
    ) = buildJsonObject {
        put("client_request_id", key)
        put(
            "transactions",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", type)
                        put("amount", amount)
                        if (categoryId != null) put("category_id", categoryId)
                        put("occurred_at", "2026-11-20T12:00:00+08:00")
                    }
                )
            },
        )
    }

    @Test
    fun addsTransaction() {
        val result = service.add(addArgs())

        assertTrue(result is ToolResult.Ok)
        val payload = (result as ToolResult.Ok).payload
        assertTrue(payload["batch_id"]!!.toString().isNotBlank())
        assertEquals(1, ledger.txs.size)
    }

    @Test
    fun rejectsMoreThanTwenty() {
        val txs = buildJsonArray {
            repeat(21) {
                add(buildJsonObject {
                    put("type", "expense")
                    put("amount", "1")
                })
            }
        }
        val result = service.add(
            buildJsonObject {
                put("client_request_id", "request-1")
                put("transactions", txs)
            }
        )

        assertEquals("LIMIT_EXCEEDED", (result as ToolResult.Error).code)
    }

    @Test
    fun rejectsUnknownCategoryAndTypeMismatch() {
        val unknown = service.add(addArgs(categoryId = "missing"))
        assertEquals("VALIDATION", (unknown as ToolResult.Error).code)

        val mismatch = service.add(addArgs(key = "request-2", type = "income"))
        assertEquals("CATEGORY_TYPE_MISMATCH", (mismatch as ToolResult.Error).code)
    }

    @Test
    fun replaysSameRequestAndRejectsDifferentArguments() {
        val first = service.add(addArgs()) as ToolResult.Ok
        val replay = service.add(addArgs()) as ToolResult.Ok
        assertEquals(first.payload["batch_id"], replay.payload["batch_id"])

        val conflict = service.add(addArgs(amount = "33.00"))
        assertEquals("IDEMPOTENCY_CONFLICT", (conflict as ToolResult.Error).code)
    }

    @Test
    fun rejectsInvalidatedKey() {
        service.add(addArgs())
        val record = ledger.idem["request-1"]!!
        ledger.idem["request-1"] = record.copy(invalidatedAt = now)

        val result = service.add(addArgs())

        assertEquals("IDEMPOTENCY_INVALIDATED", (result as ToolResult.Error).code)
    }

    @Test
    fun updateRejectsTypeChangeWithoutNewCategory() {
        ledger.txs += tx()
        val args = buildJsonObject {
            put("client_request_id", "request-update")
            put("id", "tx-1")
            put(
                "patch",
                buildJsonObject { put("type", "income") },
            )
        }

        val result = service.update(args)

        assertEquals("CATEGORY_TYPE_MISMATCH", (result as ToolResult.Error).code)
    }

    @Test
    fun updateAcceptsTypeChangeWithNullCategory() {
        ledger.txs += tx()
        val args = buildJsonObject {
            put("client_request_id", "request-update")
            put("id", "tx-1")
            put(
                "patch",
                buildJsonObject {
                    put("type", "income")
                    put("category_id", kotlinx.serialization.json.JsonNull)
                },
            )
        }

        val result = service.update(args)

        assertTrue(result is ToolResult.Ok)
        assertEquals(TxType.INCOME, ledger.txs.single().type)
    }

    @Test
    fun deleteRejectsMissingRows() {
        ledger.txs += tx()
        val args = buildJsonObject {
            put("client_request_id", "request-delete")
            put("ids", buildJsonArray { add(JsonPrimitive("missing")) })
        }

        val result = service.delete(args)

        assertEquals("NOT_FOUND", (result as ToolResult.Error).code)
        assertEquals(1, ledger.txs.size)
    }

    @Test
    fun rejectsOutOfRangeOccurredAt() {
        val args = buildJsonObject {
            put("client_request_id", "request-1")
            put(
                "transactions",
                buildJsonArray {
                    add(buildJsonObject {
                        put("type", "expense")
                        put("amount", "1")
                        put("occurred_at", "1999-12-31T00:00:00+08:00")
                    })
                },
            )
        }

        assertEquals("VALIDATION", (service.add(args) as ToolResult.Error).code)
    }

    private class FakeLedger(private val categories: List<Category>) : LedgerReader, LedgerWriter {
        val txs = mutableListOf<Tx>()
        val idem = mutableMapOf<String, IdempotencyRecord>()

        override fun readSnapshot() = Snapshot(categories, txs.toList())

        override fun idempotency(key: String): IdempotencyRecord? = idem[key]

        override fun commitAdd(commit: AddCommit): CommitResult {
            idem[commit.meta.idempotencyKey] = IdempotencyRecord(
                commit.meta.idempotencyKey,
                commit.meta.tool,
                commit.meta.argsHash,
                commit.meta.responseJson,
                0,
                null,
            )
            txs += commit.items
            return CommitResult(commit.items.map { it.id })
        }

        override fun commitUpdate(commit: UpdateCommit): CommitResult {
            val index = txs.indexOfFirst { it.id == commit.before.id }
            if (index >= 0) txs[index] = commit.after
            idem[commit.meta.idempotencyKey] = IdempotencyRecord(
                commit.meta.idempotencyKey,
                commit.meta.tool,
                commit.meta.argsHash,
                commit.meta.responseJson,
                0,
                null,
            )
            return CommitResult(listOf(commit.after.id))
        }

        override fun commitDelete(commit: DeleteCommit): CommitResult {
            txs.removeAll { tx -> commit.before.any { it.id == tx.id } }
            idem[commit.meta.idempotencyKey] = IdempotencyRecord(
                commit.meta.idempotencyKey,
                commit.meta.tool,
                commit.meta.argsHash,
                commit.meta.responseJson,
                0,
                null,
            )
            return CommitResult(commit.before.map { it.id })
        }

        override fun undoBatch(batchId: String): UndoResult = UndoResult.Ok(batchId)

        override fun batch(batchId: String): BatchSummary? = null
    }
}
