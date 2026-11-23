package app.fen.mcp.config

import org.junit.Assert.assertTrue
import org.junit.Test

class McpSetupTextTest {

    @Test
    fun containsHttpAndStdioFallback() {
        val text = setupInstruction("192.168.1.2:8067", "token-123")

        assertTrue(text.contains("http://192.168.1.2:8067/mcp"))
        assertTrue(text.contains("Streamable HTTP"))
        assertTrue(text.contains("Authorization: Bearer token-123"))
        assertTrue(text.contains("mcp-remote"))
        assertTrue(text.contains("Authorization:Bearer token-123"))
        assertTrue(text.contains("get_overview"))
    }
}
