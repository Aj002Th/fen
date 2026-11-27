package app.fen.mcp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class RequestHeaders(
    val host: String? = null,
    val origin: String? = null,
    val authorization: String? = null,
    val contentType: String? = null,
    val accept: String? = null,
    val protocolVersion: String? = null,
    val mcpMethod: String? = null,
    val mcpName: String? = null,
    val sessionId: String? = null,
)

data class ClientInfo(val name: String?, val version: String?)

data class ModernMeta(
    val protocolVersion: String,
    val clientInfo: ClientInfo?,
    val capabilities: JsonObject,
)

data class RpcCall(
    val id: JsonElement?,
    val method: String,
    val params: JsonObject,
) {
    val isNotification: Boolean get() = id == null
}

sealed interface RpcParse {
    data class Ok(val call: RpcCall) : RpcParse
    data class Error(val code: Int, val message: String) : RpcParse
}

object RpcCode {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL = -32603
    const val HEADER_MISMATCH = -32020
    const val UNSUPPORTED_VERSION = -32022
}

fun parseRpc(body: String): RpcParse {
    val element = try {
        Json.parseToJsonElement(body)
    } catch (_: Exception) {
        return RpcParse.Error(RpcCode.PARSE_ERROR, "Parse error")
    }
    if (element is JsonArray) {
        return RpcParse.Error(RpcCode.INVALID_REQUEST, "Batch requests are not supported")
    }
    if (element !is JsonObject) {
        return RpcParse.Error(RpcCode.INVALID_REQUEST, "Invalid Request")
    }
    val method = element.stringOrNull("method")
        ?: return RpcParse.Error(RpcCode.INVALID_REQUEST, "Missing method")
    val paramsElement = element["params"]
    if (paramsElement != null && paramsElement !is JsonObject) {
        return RpcParse.Error(RpcCode.INVALID_REQUEST, "params must be an object")
    }
    val id = element["id"]?.takeIf { it !is JsonNull }
    return RpcParse.Ok(
        RpcCall(
            id = id,
            method = method,
            params = if (paramsElement is JsonObject) paramsElement else JsonObject(emptyMap()),
        )
    )
}

fun parseModernMeta(params: JsonObject): ModernMeta? {
    val meta = params["_meta"] as? JsonObject ?: return null
    val version = meta.stringOrNull("io.modelcontextprotocol/protocolVersion") ?: return null
    val client = (meta["io.modelcontextprotocol/clientInfo"] as? JsonObject)?.let {
        ClientInfo(
            name = it.stringOrNull("name"),
            version = it.stringOrNull("version"),
        )
    }
    val capabilities = meta["io.modelcontextprotocol/clientCapabilities"] as? JsonObject
        ?: JsonObject(emptyMap())
    return ModernMeta(
        protocolVersion = version,
        clientInfo = client,
        capabilities = capabilities,
    )
}

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
