package app.fen.mcp.tools

import kotlinx.serialization.json.JsonObject

sealed interface ToolResult {
    data class Ok(val payload: JsonObject) : ToolResult
    data class Error(
        val code: String,
        val message: String,
        val details: JsonObject? = null,
    ) : ToolResult
}
