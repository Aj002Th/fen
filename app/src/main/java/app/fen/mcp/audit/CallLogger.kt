package app.fen.mcp.audit

import app.fen.mcp.RequestContext
import app.fen.mcp.RpcOutcome
import app.fen.mcp.protocol.RpcCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CallLogger(
    private val store: CallLogStore,
    private val clock: () -> Long,
    private val tokenFingerprint: String?,
    private val fallback: (CallRecord) -> Unit = {},
) {

    suspend fun around(
        context: RequestContext,
        call: RpcCall,
        block: suspend () -> RpcOutcome,
    ): RpcOutcome {
        val started = clock()
        return try {
            val outcome = block()
            record(context, call, outcome, started, null)
            outcome
        } catch (e: Exception) {
            record(context, call, null, started, e)
            throw e
        }
    }

    private fun record(
        context: RequestContext,
        call: RpcCall,
        outcome: RpcOutcome?,
        started: Long,
        failure: Exception?,
    ) {
        val status = when (outcome) {
            is RpcOutcome.Json -> outcome.status
            RpcOutcome.Accepted -> 202
            is RpcOutcome.HttpError -> outcome.status
            null -> 500
        }
        val body = (outcome as? RpcOutcome.Json)?.body
        val ok = failure == null && status in 200..299 && !body.orEmpty().contains("\"isError\":true")
        val tool = if (call.method == "tools/call") {
            (call.params["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        } else {
            null
        }
        val args = call.params.toString()
        val toolError = body?.takeIf { !ok }?.let { extractToolError(it) }
        val record = CallRecord(
            tsMs = started,
            clientIp = context.clientIp,
            clientName = context.meta?.clientInfo?.name,
            clientVersion = context.meta?.clientInfo?.version,
            era = context.era.name,
            protocolVersion = context.meta?.protocolVersion ?: context.headers.protocolVersion,
            method = call.method,
            tool = tool,
            argsSummary = args.take(if (tool == null) 1024 else 4096),
            ok = ok,
            httpStatus = status,
            errorCode = toolError?.first
                ?: body?.let { CODE.find(it)?.groupValues?.getOrNull(1) }
                ?: if (failure != null) "INTERNAL" else null,
            errorMessage = failure?.message ?: toolError?.second ?: body?.takeIf { !ok }?.take(512),
            durationMs = clock() - started,
            requestId = call.id?.toString(),
            batchId = body?.let { extractBatchId(it) },
            tokenFp = tokenFingerprint,
            bytesIn = args.toByteArray(Charsets.UTF_8).size,
            bytesOut = body?.toByteArray(Charsets.UTF_8)?.size ?: 0,
        )
        try {
            store.insert(record)
        } catch (_: Exception) {
            fallback(record)
        }
    }

    private fun extractBatchId(body: String): String? = runCatching {
        val root = Json.parseToJsonElement(body).jsonObject
        val text = root["result"]?.jsonObject
            ?.get("content")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: return@runCatching null
        Json.parseToJsonElement(text).jsonObject["batch_id"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun extractToolError(body: String): Pair<String?, String?>? = runCatching {
        val root = Json.parseToJsonElement(body).jsonObject
        val text = root["result"]?.jsonObject
            ?.get("content")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: return@runCatching null
        val error = Json.parseToJsonElement(text).jsonObject
        (error["code"]?.jsonPrimitive?.content) to (error["message"]?.jsonPrimitive?.content)
    }.getOrNull()

    private companion object {
        val CODE = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"")
    }
}
