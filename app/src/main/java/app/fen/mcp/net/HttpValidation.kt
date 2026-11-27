package app.fen.mcp.net

import app.fen.mcp.auth.TokenAuth
import app.fen.mcp.protocol.RpcCall
import app.fen.mcp.protocol.RpcCode
import app.fen.mcp.protocol.RequestHeaders
import app.fen.mcp.protocol.parseModernMeta

data class Rejection(
    val status: Int,
    val code: Int? = null,
    val message: String,
)

fun validateHeaders(headers: RequestHeaders, allowedHosts: Set<String>): Rejection? {
    if (!headers.origin.isNullOrBlank()) {
        return Rejection(403, message = "Origin not allowed")
    }
    val host = headers.host
        ?: return Rejection(400, RpcCode.HEADER_MISMATCH, "Missing Host")
    if (allowedHosts.none { it.equals(host, ignoreCase = true) }) {
        return Rejection(403, message = "Host not allowed")
    }
    return null
}

fun validateModernHeaders(headers: RequestHeaders, call: RpcCall): Rejection? {
    val meta = parseModernMeta(call.params)
        ?: return Rejection(400, RpcCode.HEADER_MISMATCH, "Missing _meta protocol fields")
    val headerVersion = headers.protocolVersion
        ?: return Rejection(400, RpcCode.HEADER_MISMATCH, "Missing MCP-Protocol-Version")
    if (headerVersion != meta.protocolVersion) {
        return Rejection(400, RpcCode.HEADER_MISMATCH, "Protocol version header/body mismatch")
    }
    val headerMethod = headers.mcpMethod
        ?: return Rejection(400, RpcCode.HEADER_MISMATCH, "Missing Mcp-Method")
    if (headerMethod != call.method) {
        return Rejection(400, RpcCode.HEADER_MISMATCH, "Mcp-Method header/body mismatch")
    }
    if (call.method == "tools/call") {
        val headerName = headers.mcpName
            ?: return Rejection(400, RpcCode.HEADER_MISMATCH, "Missing Mcp-Name")
        val decoded = TokenAuth.decodeHeaderValue(headerName)
            ?: return Rejection(400, RpcCode.HEADER_MISMATCH, "Invalid Mcp-Name encoding")
        val bodyName = (call.params["name"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
        if (bodyName == null || decoded != bodyName) {
            return Rejection(400, RpcCode.HEADER_MISMATCH, "Mcp-Name header/body mismatch")
        }
    }
    return null
}
