package app.fen.mcp.tools

import kotlinx.serialization.json.JsonObject

class ToolRegistry(
    private val readTools: ReadTools,
    private val writeTools: WriteTools,
    private val writeEnabled: () -> Boolean = { false },
) {

    fun tools(writeEnabled: Boolean = this.writeEnabled()): List<ToolDescriptor> =
        readTools.descriptors() + if (writeEnabled) writeTools.descriptors() else emptyList()

    suspend fun call(
        name: String,
        args: JsonObject,
        writeEnabled: Boolean = this.writeEnabled(),
    ): ToolResult {
        val tool = tools(writeEnabled).firstOrNull { it.name == name }
        if (tool != null) return tool.handler(args)

        val isWriteTool = writeTools.descriptors().any { it.name == name }
        return if (isWriteTool && !writeEnabled) {
            ToolResult.Error("WRITE_DISABLED", "User has not enabled write access on the phone")
        } else {
            ToolResult.Error("NOT_FOUND", "Unknown tool: $name")
        }
    }
}
