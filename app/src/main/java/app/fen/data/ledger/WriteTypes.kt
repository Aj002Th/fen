package app.fen.data.ledger

import app.fen.data.Tx
import app.fen.data.TxType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class IdempotencyRecord(
    val key: String,
    val tool: String,
    val argsHash: String,
    val responseJson: String,
    val createdAt: Long,
    val invalidatedAt: Long?,
)

data class BatchMeta(
    val batchId: String,
    val tool: String,
    val idempotencyKey: String,
    val argsHash: String,
    val clientName: String?,
    val responseJson: String,
)

data class AddCommit(val items: List<Tx>, val meta: BatchMeta)

data class UpdateCommit(val before: Tx, val after: Tx, val meta: BatchMeta)

data class DeleteCommit(val before: List<Tx>, val meta: BatchMeta)

data class CommitResult(val createdIds: List<String>, val replayed: Boolean = false)

data class BatchSummary(
    val id: String,
    val tool: String,
    val createdAt: Long,
    val undoneAt: Long?,
    val clientName: String?,
    val itemCount: Int,
    val idempotencyKey: String?,
)

data class BatchItem(
    val txId: String,
    val beforeJson: String?,
    val afterJson: String?,
)

sealed interface UndoResult {
    data class Ok(val batchId: String) : UndoResult
    data class Conflict(val message: String) : UndoResult
    data class NotFound(val batchId: String) : UndoResult
}

sealed class WriteFailure(message: String) : Exception(message) {
    class IdempotencyConflict(message: String) : WriteFailure(message)
    class IdempotencyInvalidated(message: String) : WriteFailure(message)
    class WriteConflict(message: String) : WriteFailure(message)
    class Validation(message: String) : WriteFailure(message)
}

interface LedgerWriter {
    fun idempotency(key: String): IdempotencyRecord?
    fun commitAdd(commit: AddCommit): CommitResult
    fun commitUpdate(commit: UpdateCommit): CommitResult
    fun commitDelete(commit: DeleteCommit): CommitResult
    fun undoBatch(batchId: String): UndoResult
    fun batch(batchId: String): BatchSummary?
}

fun Tx.toLedgerJson(): String = buildJsonObject {
    put("id", id)
    put("type", type.name)
    put("amount", amount)
    put("categoryId", categoryId ?: "")
    put("note", note)
    put("occurredAt", occurredAt)
}.toString()

fun txFromLedgerJson(raw: String): Tx {
    val json: JsonObject = Json.parseToJsonElement(raw).jsonObject
    return Tx(
        id = json["id"]!!.jsonPrimitive.content,
        type = TxType.valueOf(json["type"]!!.jsonPrimitive.content),
        amount = json["amount"]!!.jsonPrimitive.content.toLong(),
        categoryId = json["categoryId"]!!.jsonPrimitive.content.takeIf { it.isNotEmpty() },
        note = json["note"]!!.jsonPrimitive.content,
        occurredAt = json["occurredAt"]!!.jsonPrimitive.content.toLong(),
    )
}
