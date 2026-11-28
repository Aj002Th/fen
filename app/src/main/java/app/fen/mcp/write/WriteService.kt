package app.fen.mcp.write

import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.data.ledger.AddCommit
import app.fen.data.ledger.BatchMeta
import app.fen.data.ledger.DeleteCommit
import app.fen.data.ledger.LedgerReader
import app.fen.data.ledger.LedgerWriter
import app.fen.data.ledger.UpdateBatchCommit
import app.fen.data.ledger.UpdateCommit
import app.fen.data.ledger.UpdateEntry
import app.fen.data.ledger.WriteFailure
import app.fen.mcp.tools.Money
import app.fen.mcp.tools.ToolResult
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class WriteService(
    private val reader: LedgerReader,
    private val writer: LedgerWriter,
    private val clock: () -> Long,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val batchLimit: () -> Int = { 20 },
) {

    fun add(args: JsonObject): ToolResult {
        val key = requestKey(args) ?: return validation("client_request_id must be 8..64 characters")
        val transactions = args["transactions"] as? JsonArray
            ?: return validation("transactions must be an array")
        if (transactions.isEmpty()) return validation("transactions must not be empty")
        if (transactions.size > batchLimit()) {
            return limit("At most ${batchLimit()} transactions per batch")
        }

        val hash = argsHash(args)
        replay("add_transactions", key, hash)?.let { return it }

        val items = ArrayList<Tx>(transactions.size)
        transactions.forEachIndexed { index, element ->
            val obj = element as? JsonObject ?: return validation("transactions[$index] must be an object")
            val tx = parseTx(obj, index) ?: return validation("Invalid transaction at index $index")
            items += tx
        }
        val categoryError = validateCategoryTypes(items)
        if (categoryError != null) return categoryError

        val batchId = newId()
        val response = buildJsonObject {
            put("batch_id", batchId)
            put(
                "created",
                buildJsonArray {
                    items.forEachIndexed { index, tx ->
                        add(buildJsonObject {
                            put("id", tx.id)
                            put("index", index)
                        })
                    }
                },
            )
            put("replayed", false)
        }
        val meta = BatchMeta(
            batchId = batchId,
            tool = "add_transactions",
            idempotencyKey = key,
            argsHash = hash,
            clientName = null,
            responseJson = response.toString(),
        )
        return commit {
            val result = writer.commitAdd(AddCommit(items, meta))
            val body = if (result.replayed) {
                (replayResponse(key) as? ToolResult.Ok)?.payload ?: response
            } else {
                response
            }
            ToolResult.Ok(body)
        }
    }

    fun update(args: JsonObject): ToolResult {
        val key = requestKey(args) ?: return validation("client_request_id must be 8..64 characters")
        val id = args.stringOrNull("id") ?: return validation("id is required")
        val patch = args["patch"] as? JsonObject ?: return validation("patch must be an object")
        if (patch.isEmpty()) return validation("patch must contain at least one field")

        val hash = argsHash(args)
        replay("update_transaction", key, hash)?.let { return it }

        val current = reader.readSnapshot().transactions.firstOrNull { it.id == id }
            ?: return notFound("Transaction not found: $id")
        val after = applyPatch(current, patch) ?: return validation("Invalid patch")
        validateCategoryTypes(listOf(after))?.let { return it }

        val batchId = newId()
        val response = buildJsonObject {
            put("batch_id", batchId)
            put("updated", id)
        }
        val meta = BatchMeta(
            batchId = batchId,
            tool = "update_transaction",
            idempotencyKey = key,
            argsHash = hash,
            clientName = null,
            responseJson = response.toString(),
        )
        return commit {
            val result = writer.commitUpdate(UpdateCommit(current, after, meta))
            val body = if (result.replayed) {
                (replayResponse(key) as? ToolResult.Ok)?.payload ?: response
            } else {
                response
            }
            ToolResult.Ok(body)
        }
    }

    fun updateMany(args: JsonObject): ToolResult {
        val key = requestKey(args) ?: return validation("client_request_id must be 8..64 characters")
        val updates = args["updates"] as? JsonArray
            ?: return validation("updates must be an array")
        if (updates.isEmpty()) return validation("updates must not be empty")
        if (updates.size > batchLimit()) {
            return limit("At most ${batchLimit()} transactions per batch")
        }

        val hash = argsHash(args)
        replay("update_transactions", key, hash)?.let { return it }

        val snapshot = reader.readSnapshot()
        val seen = HashSet<String>(updates.size)
        val entries = ArrayList<UpdateEntry>(updates.size)
        updates.forEachIndexed { index, element ->
            val obj = element as? JsonObject ?: return validation("updates[$index] must be an object")
            val id = obj.stringOrNull("id") ?: return validation("updates[$index].id is required")
            val patch = obj["patch"] as? JsonObject
                ?: return validation("updates[$index].patch must be an object")
            if (patch.isEmpty()) return validation("updates[$index].patch must not be empty")
            if (!seen.add(id)) return validation("duplicate transaction id: $id")
            val current = snapshot.transactions.firstOrNull { it.id == id }
                ?: return notFound("Transaction not found: $id")
            val after = applyPatch(current, patch)
                ?: return validation("Invalid patch at updates[$index]")
            entries += UpdateEntry(current, after)
        }
        validateCategoryTypes(entries.map { it.after })?.let { return it }

        val batchId = newId()
        val response = buildJsonObject {
            put("batch_id", batchId)
            put("updated", buildJsonArray { entries.forEach { add(JsonPrimitive(it.after.id)) } })
        }
        val meta = BatchMeta(
            batchId = batchId,
            tool = "update_transactions",
            idempotencyKey = key,
            argsHash = hash,
            clientName = null,
            responseJson = response.toString(),
        )
        return commit {
            val result = writer.commitUpdates(UpdateBatchCommit(entries, meta))
            val body = if (result.replayed) {
                (replayResponse(key) as? ToolResult.Ok)?.payload ?: response
            } else {
                response
            }
            ToolResult.Ok(body)
        }
    }

    fun delete(args: JsonObject): ToolResult {
        val key = requestKey(args) ?: return validation("client_request_id must be 8..64 characters")
        val ids = (args["ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            ?: return validation("ids must be an array of strings")
        if (ids.isEmpty()) return validation("ids must not be empty")
        if (ids.size > batchLimit()) {
            return limit("At most ${batchLimit()} transactions per batch")
        }

        val hash = argsHash(args)
        replay("delete_transactions", key, hash)?.let { return it }

        val snapshot = reader.readSnapshot()
        val before = ids.mapNotNull { id -> snapshot.transactions.firstOrNull { it.id == id } }
        val missing = ids.filter { id -> before.none { it.id == id } }
        if (missing.isNotEmpty()) {
            return notFound("Transaction not found", buildJsonArray { missing.forEach { add(JsonPrimitive(it)) } })
        }

        val batchId = newId()
        val response = buildJsonObject {
            put("batch_id", batchId)
            put("deleted", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
        }
        val meta = BatchMeta(
            batchId = batchId,
            tool = "delete_transactions",
            idempotencyKey = key,
            argsHash = hash,
            clientName = null,
            responseJson = response.toString(),
        )
        return commit {
            val result = writer.commitDelete(DeleteCommit(before, meta))
            val body = if (result.replayed) {
                (replayResponse(key) as? ToolResult.Ok)?.payload ?: response
            } else {
                response
            }
            ToolResult.Ok(body)
        }
    }

    private fun parseTx(obj: JsonObject, index: Int): Tx? {
        val type = when (obj.stringOrNull("type")) {
            "expense" -> TxType.EXPENSE
            "income" -> TxType.INCOME
            else -> return null
        }
        val amount = obj.stringOrNull("amount")?.let { Money.parseYuanToMinor(it) } ?: return null
        val categoryId = obj.stringOrNull("category_id")?.takeIf { it.isNotBlank() }
        val note = obj.stringOrNull("note") ?: ""
        if (note.length > 200) return null
        val occurredAt = parseInstant(obj.stringOrNull("occurred_at")) ?: return null
        if (occurredAt !in MIN_TIME..MAX_TIME) return null
        return Tx(
            id = newId(),
            type = type,
            amount = amount,
            categoryId = categoryId,
            note = note,
            occurredAt = occurredAt,
        )
    }

    private fun applyPatch(tx: Tx, patch: JsonObject): Tx? {
        var type = tx.type
        var amount = tx.amount
        var categoryId = tx.categoryId
        var note = tx.note
        var occurredAt = tx.occurredAt

        if (patch.containsKey("type")) {
            type = when (patch.stringOrNull("type")) {
                "expense" -> TxType.EXPENSE
                "income" -> TxType.INCOME
                else -> return null
            }
        }
        if (patch.containsKey("amount")) {
            amount = patch.stringOrNull("amount")?.let { Money.parseYuanToMinor(it) } ?: return null
        }
        if (patch.containsKey("category_id")) {
            val value = patch["category_id"]
            categoryId = when (value) {
                null, JsonNull -> null
                is JsonPrimitive -> value.content.takeIf { it.isNotBlank() }
                else -> return null
            }
        }
        if (patch.containsKey("note")) {
            note = patch.stringOrNull("note") ?: return null
            if (note.length > 200) return null
        }
        if (patch.containsKey("occurred_at")) {
            occurredAt = parseInstant(patch.stringOrNull("occurred_at")) ?: return null
            if (occurredAt !in MIN_TIME..MAX_TIME) return null
        }
        return tx.copy(
            type = type,
            amount = amount,
            categoryId = categoryId,
            note = note,
            occurredAt = occurredAt,
        )
    }

    private fun validateCategoryTypes(items: List<Tx>): ToolResult.Error? {
        val categories = reader.readSnapshot().categories.associateBy { it.id }
        items.forEach { tx ->
            val categoryId = tx.categoryId ?: return@forEach
            val category = categories[categoryId]
                ?: return ToolResult.Error("VALIDATION", "Category not found: $categoryId")
            if (category.type != tx.type) {
                return ToolResult.Error("CATEGORY_TYPE_MISMATCH", "Category type does not match transaction type")
            }
        }
        return null
    }

    private fun replay(tool: String, key: String, hash: String): ToolResult? {
        val existing = writer.idempotency(key) ?: return null
        if (existing.invalidatedAt != null) {
            return ToolResult.Error("IDEMPOTENCY_INVALIDATED", "This request was undone")
        }
        if (existing.tool != tool || existing.argsHash != hash) {
            return ToolResult.Error("IDEMPOTENCY_CONFLICT", "client_request_id reused with different arguments")
        }
        return replayResponse(key)
    }

    private fun replayResponse(key: String): ToolResult? {
        val existing = writer.idempotency(key) ?: return null
        return try {
            ToolResult.Ok(Json.parseToJsonElement(existing.responseJson).jsonObject)
        } catch (_: Exception) {
            ToolResult.Error("INTERNAL", "Stored response is unreadable")
        }
    }

    private inline fun commit(block: () -> ToolResult): ToolResult = try {
        block()
    } catch (e: WriteFailure.IdempotencyConflict) {
        ToolResult.Error("IDEMPOTENCY_CONFLICT", e.message ?: "Idempotency conflict")
    } catch (e: WriteFailure.IdempotencyInvalidated) {
        ToolResult.Error("IDEMPOTENCY_INVALIDATED", e.message ?: "Request was undone")
    } catch (e: WriteFailure.WriteConflict) {
        ToolResult.Error("CONFLICT", e.message ?: "Write conflict")
    } catch (e: WriteFailure.Validation) {
        ToolResult.Error("VALIDATION", e.message ?: "Validation failed")
    } catch (e: Exception) {
        ToolResult.Error("INTERNAL", e.message ?: "Write failed")
    }

    private fun requestKey(args: JsonObject): String? =
        args.stringOrNull("client_request_id")?.takeIf { it.length in 8..64 }

    private fun argsHash(args: JsonObject): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonical(args).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun canonical(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries.sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") {
                "${JsonPrimitive(it.key)}:${canonical(it.value)}"
            }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",") { canonical(it) }
        else -> element.toString()
    }

    private fun parseInstant(raw: String?): Long? {
        if (raw == null) return clock()
        return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(raw).toEpochMilli() }
            .getOrNull()
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun validation(message: String) = ToolResult.Error("VALIDATION", message)

    private fun limit(message: String) = ToolResult.Error("LIMIT_EXCEEDED", message)

    private fun notFound(message: String, details: JsonElement? = null) =
        ToolResult.Error("NOT_FOUND", message, details as? JsonObject)

    private companion object {
        const val MIN_TIME = 946_684_800_000L // 2000-01-01
        const val MAX_TIME = 4_102_444_800_000L // 2100-01-01
    }
}
