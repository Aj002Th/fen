package app.fen.mcp.protocol

import app.fen.mcp.RequestContext
import app.fen.mcp.RpcHandler
import app.fen.mcp.RpcOutcome
import app.fen.mcp.tools.ToolDescriptor
import app.fen.mcp.tools.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object McpInstructions {
    const val TEXT =
        "Fen 是用户手机上的本地记账账本。金额以分为整数存储，响应同时给 amount（元，两位小数字符串）。" +
            "日期为本地时区。建议先调用 get_overview 了解数据范围和当前月份，再用 get_summary 聚合、" +
            "list_transactions 查明细、list_categories 拿分类 id。不要臆造分类 id。" +
            "写入默认关闭；如果工具列表里没有写工具，说明用户没有开启写入，不要重试。"
}

class ProtocolDispatcher(
    private val tools: () -> List<ToolDescriptor>,
    private val callTool: suspend (name: String, args: JsonObject) -> ToolResult,
    private val sessions: SessionStore,
    private val serverVersion: String,
) : RpcHandler {

    override suspend fun handle(call: RpcCall, context: RequestContext): RpcOutcome = when (context.era) {
        ProtocolEra.MODERN_2026_07_28 -> handleModern(call)
        ProtocolEra.LEGACY_2025_11_25, ProtocolEra.LEGACY_2025_06_18 -> handleLegacy(call, context)
    }

    fun endSession(id: String): Boolean {
        val existed = sessions.get(id) != null
        sessions.delete(id)
        return existed
    }

    private suspend fun handleModern(call: RpcCall): RpcOutcome {
        if (call.isNotification) return RpcOutcome.Accepted
        return when (call.method) {
            "server/discover" -> RpcOutcome.Json(modernResult(call.id, discoverPayload(), serverVersion).toString())
            "tools/list" -> RpcOutcome.Json(
                modernListResult(call.id, buildJsonObject { put("tools", toolArray()) }, serverVersion)
                    .toString()
            )
            "tools/call" -> RpcOutcome.Json(
                modernResult(call.id, callToolPayload(call.params), serverVersion).toString()
            )
            else -> RpcOutcome.HttpError(
                404,
                rpcError(call.id, RpcCode.METHOD_NOT_FOUND, "Method not found").toString(),
            )
        }
    }

    private suspend fun handleLegacy(call: RpcCall, context: RequestContext): RpcOutcome {
        if (call.method == "initialize") {
            val requested = call.params.stringOrNull("protocolVersion") ?: LEGACY_PROTOCOL_VERSION
            val version = if (requested == LEGACY_PROTOCOL_VERSION || requested == LEGACY_PROTOCOL_VERSION_06) {
                requested
            } else {
                LEGACY_PROTOCOL_VERSION
            }
            val client = (call.params["clientInfo"] as? JsonObject)?.let {
                ClientInfo(name = it.stringOrNull("name"), version = it.stringOrNull("version"))
            }
            val session = sessions.create(version, client)
            val payload = buildJsonObject {
                put("protocolVersion", version)
                put(
                    "capabilities",
                    buildJsonObject {
                        put("tools", buildJsonObject { put("listChanged", false) })
                    },
                )
                put("serverInfo", serverInfoJson(serverVersion))
                put("instructions", McpInstructions.TEXT)
            }
            return RpcOutcome.Json(
                body = legacyResult(call.id, payload).toString(),
                headers = mapOf("Mcp-Session-Id" to session.id),
            )
        }
        if (call.method.startsWith("notifications/")) return RpcOutcome.Accepted

        val sessionId = context.sessionId
        if (sessionId.isNullOrBlank() || sessions.get(sessionId) == null) {
            return RpcOutcome.HttpError(
                404,
                rpcError(call.id, RpcCode.INVALID_REQUEST, "Session not found").toString(),
            )
        }

        return when (call.method) {
            "ping" -> RpcOutcome.Json(legacyResult(call.id, JsonObject(emptyMap())).toString())
            "tools/list" -> RpcOutcome.Json(
                legacyResult(call.id, buildJsonObject { put("tools", toolArray()) }).toString()
            )
            "tools/call" -> RpcOutcome.Json(
                legacyResult(call.id, callToolPayload(call.params)).toString()
            )
            else -> RpcOutcome.Json(
                rpcError(call.id, RpcCode.METHOD_NOT_FOUND, "Method not found").toString()
            )
        }
    }

    private fun discoverPayload(): JsonObject = buildJsonObject {
        put(
            "supportedVersions",
            buildJsonArray { SUPPORTED_PROTOCOL_VERSIONS.forEach { add(JsonPrimitive(it)) } },
        )
        put(
            "capabilities",
            buildJsonObject {
                put("tools", buildJsonObject { put("listChanged", false) })
            },
        )
        put("instructions", McpInstructions.TEXT)
        put("ttlMs", 3_600_000)
        put("cacheScope", "private")
    }

    private fun toolArray(): JsonArray = buildJsonArray {
        tools().forEach { tool ->
            add(
                buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("inputSchema", tool.inputSchema)
                },
            )
        }
    }

    private suspend fun callToolPayload(params: JsonObject): JsonObject {
        val name = params.stringOrNull("name")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val result = if (name == null) {
            ToolResult.Error("VALIDATION", "Missing tool name")
        } else {
            try {
                callTool(name, args)
            } catch (e: Exception) {
                ToolResult.Error("INTERNAL", e.message ?: "Tool failed")
            }
        }
        val isError = result is ToolResult.Error
        val text = when (result) {
            is ToolResult.Ok -> result.payload.toString()
            is ToolResult.Error -> buildJsonObject {
                put("code", result.code)
                put("message", result.message)
                result.details?.let { put("details", it) }
            }.toString()
        }
        return buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", text)
                        },
                    )
                },
            )
            put("isError", isError)
        }
    }
}
