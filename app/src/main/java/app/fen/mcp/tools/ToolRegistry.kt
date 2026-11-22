package app.fen.mcp.tools

import kotlinx.serialization.json.JsonObject

class ToolRegistry(private val readTools: ReadTools) {

    fun tools(writeEnabled: Boolean): List<ToolDescriptor> = readTools.descriptors()

    suspend fun call(name: String, args: JsonObject, writeEnabled: Boolean): ToolResult {
        val tool = tools(writeEnabled).firstOrNull { it.name == name }
            ?: return ToolResult.Error("NOT_FOUND", "Unknown tool: $name")
        return tool.handler(args)
    }
}
