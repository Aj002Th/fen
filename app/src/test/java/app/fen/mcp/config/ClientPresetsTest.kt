package app.fen.mcp.config

import app.fen.mcp.tools.ToolDescriptor
import app.fen.mcp.tools.ToolKind
import app.fen.mcp.tools.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientPresetsTest {

    private val address = "192.168.1.2:18067"
    private val token = "test-token"

    @Test
    fun allPresetsContainEndpointAndToken() {
        ClientKind.values().forEach { kind ->
            val text = configText(kind, address, token)
            assertTrue("$kind missing url", text.contains("http://$address/mcp"))
            assertTrue("$kind missing token", text.contains(token))
        }
    }

    @Test
    fun desktopUsesMcpRemote() {
        assertTrue(configText(ClientKind.CLAUDE_DESKTOP, address, token).contains("mcp-remote"))
    }

    @Test
    fun genericJsonIsValid() {
        val json = Json.parseToJsonElement(configText(ClientKind.GENERIC_JSON, address, token)).jsonObject
        assertTrue(json.containsKey("mcpServers"))
    }

    @Test
    fun promptContainsVerificationStep() {
        val prompt = aiSetupPrompt(ClientKind.CLAUDE_CODE, address, token)
        assertTrue(prompt.contains("get_overview"))
        assertTrue(prompt.contains(address))
    }

    @Test
    fun toolDocsListToolsAndWriteRule() {
        val tools = listOf(
            ToolDescriptor("get_overview", "概览", buildJsonObject { }, ToolKind.READ) {
                ToolResult.Error("NONE", "")
            },
            ToolDescriptor("add_transactions", "新增", buildJsonObject { }, ToolKind.WRITE) {
                ToolResult.Error("NONE", "")
            },
        )

        val docs = toolDocsMarkdown(tools)

        assertTrue(docs.contains("get_overview"))
        assertTrue(docs.contains("add_transactions"))
        assertTrue(docs.contains("写入默认关闭"))
    }
}
