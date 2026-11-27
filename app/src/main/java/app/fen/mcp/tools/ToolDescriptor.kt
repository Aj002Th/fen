package app.fen.mcp.tools

import kotlinx.serialization.json.JsonObject

enum class ToolKind { READ, WRITE }

data class ToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val kind: ToolKind,
    val handler: suspend (JsonObject) -> ToolResult,
)
