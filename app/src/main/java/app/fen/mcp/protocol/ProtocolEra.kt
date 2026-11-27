package app.fen.mcp.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ProtocolEra { MODERN_2026_07_28, LEGACY_2025_11_25, LEGACY_2025_06_18 }

const val MODERN_PROTOCOL_VERSION = "2026-07-28"
const val LEGACY_PROTOCOL_VERSION = "2025-11-25"
const val LEGACY_PROTOCOL_VERSION_06 = "2025-06-18"

val SUPPORTED_PROTOCOL_VERSIONS = listOf(
    MODERN_PROTOCOL_VERSION,
    LEGACY_PROTOCOL_VERSION,
    LEGACY_PROTOCOL_VERSION_06,
)

/**
 * modern 请求即使版本不受支持也要走 modern 分支，才能返回 -32022；
 * 因此只要带 _meta.protocolVersion 就判为 modern。
 */
fun detectEra(call: RpcCall, headers: RequestHeaders): ProtocolEra? {
    if (call.method == "initialize") return ProtocolEra.LEGACY_2025_11_25
    if (parseModernMeta(call.params) != null) return ProtocolEra.MODERN_2026_07_28
    if (headers.sessionId != null) return ProtocolEra.LEGACY_2025_11_25
    return null
}

fun serverInfoJson(serverVersion: String): JsonObject = buildJsonObject {
    put("name", "fen")
    put("version", serverVersion)
}

fun rpcError(
    id: JsonElement?,
    code: Int,
    message: String,
    data: JsonElement? = null,
): JsonObject = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", id ?: JsonNull)
    put(
        "error",
        buildJsonObject {
            put("code", code)
            put("message", message)
            if (data != null) put("data", data)
        },
    )
}

fun modernResult(
    id: JsonElement?,
    payload: JsonObject,
    serverVersion: String,
): JsonObject = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", id ?: JsonNull)
    put(
        "result",
        buildJsonObject {
            put("resultType", "complete")
            payload.forEach { (key, value) -> put(key, value) }
            put(
                "_meta",
                buildJsonObject {
                    put("io.modelcontextprotocol/serverInfo", serverInfoJson(serverVersion))
                },
            )
        },
    )
}

fun modernListResult(
    id: JsonElement?,
    payload: JsonObject,
    serverVersion: String,
    ttlMs: Long = 0,
    cacheScope: String = "private",
): JsonObject = modernResult(
    id = id,
    payload = buildJsonObject {
        payload.forEach { (key, value) -> put(key, value) }
        put("ttlMs", ttlMs)
        put("cacheScope", cacheScope)
    },
    serverVersion = serverVersion,
)

fun legacyResult(id: JsonElement?, payload: JsonObject): JsonObject = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", id ?: JsonNull)
    put("result", payload)
}
