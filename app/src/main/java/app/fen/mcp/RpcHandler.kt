package app.fen.mcp

import app.fen.mcp.protocol.ModernMeta
import app.fen.mcp.protocol.ProtocolEra
import app.fen.mcp.protocol.RequestHeaders
import app.fen.mcp.protocol.RpcCall

data class RequestContext(
    val headers: RequestHeaders,
    val clientIp: String,
    val era: ProtocolEra,
    val meta: ModernMeta?,
    val sessionId: String?,
)

sealed interface RpcOutcome {
    data class Json(
        val body: String,
        val status: Int = 200,
        val headers: Map<String, String> = emptyMap(),
    ) : RpcOutcome

    data object Accepted : RpcOutcome

    data class HttpError(val status: Int, val body: String) : RpcOutcome
}

fun interface RpcHandler {
    suspend fun handle(call: RpcCall, context: RequestContext): RpcOutcome
}

sealed interface McpServerState {
    data object Stopped : McpServerState
    data object Starting : McpServerState
    data class Running(
        val port: Int,
        val addresses: List<String>,
        val writeUntil: Long? = null,
    ) : McpServerState

    data class Error(val message: String) : McpServerState
}

data class SelfTestResult(
    val modernOk: Boolean,
    val legacyOk: Boolean,
    val toolCount: Int,
    val latencyMs: Long,
    val error: String? = null,
)
