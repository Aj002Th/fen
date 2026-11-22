package app.fen.mcp.audit

import app.fen.mcp.RequestContext
import app.fen.mcp.RpcOutcome
import app.fen.mcp.protocol.RpcCall
import kotlinx.serialization.json.JsonPrimitive

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
            errorCode = body?.let { CODE.find(it)?.groupValues?.getOrNull(1) } ?: if (failure != null) "INTERNAL" else null,
            errorMessage = failure?.message ?: body?.takeIf { !ok }?.take(512),
            durationMs = clock() - started,
            requestId = call.id?.toString(),
            batchId = body?.let { BATCH.find(it)?.groupValues?.getOrNull(1) },
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

    private companion object {
        val CODE = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"")
        val BATCH = Regex("\"batch_id\"\\s*:\\s*\"([^\"]+)\"")
    }
}
