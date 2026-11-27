package app.fen.mcp.net

import app.fen.mcp.protocol.RpcCall
import app.fen.mcp.protocol.RpcCode
import app.fen.mcp.protocol.RequestHeaders
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpValidationTest {

    private val hosts = setOf("192.168.1.2:8067", "127.0.0.1:8067")

    private fun call(method: String, name: String? = null): RpcCall = RpcCall(
        id = JsonPrimitive(1),
        method = method,
        params = buildJsonObject {
            if (name != null) put("name", name)
            put(
                "_meta",
                buildJsonObject {
                    put("io.modelcontextprotocol/protocolVersion", "2026-07-28")
                    put("io.modelcontextprotocol/clientCapabilities", buildJsonObject { })
                },
            )
        },
    )

    private fun modernHeaders(
        version: String? = "2026-07-28",
        method: String? = "tools/call",
        name: String? = "get_overview",
    ) = RequestHeaders(
        host = "192.168.1.2:8067",
        protocolVersion = version,
        mcpMethod = method,
        mcpName = name,
    )

    @Test
    fun rejectsAnyOrigin() {
        val rejection = validateHeaders(
            RequestHeaders(host = "192.168.1.2:8067", origin = "http://evil.example"),
            hosts,
        )
        assertEquals(403, rejection?.status)
    }

    @Test
    fun rejectsMissingOrUnknownHost() {
        assertEquals(400, validateHeaders(RequestHeaders(), hosts)?.status)
        assertEquals(403, validateHeaders(RequestHeaders(host = "10.0.0.9:8067"), hosts)?.status)
    }

    @Test
    fun hostIsCaseInsensitive() {
        assertNull(validateHeaders(RequestHeaders(host = "LOCALHOST:8067"), setOf("localhost:8067")))
    }

    @Test
    fun acceptsMatchingModernHeaders() {
        assertNull(validateModernHeaders(modernHeaders(), call("tools/call", "get_overview")))
    }

    @Test
    fun rejectsMissingProtocolVersion() {
        assertEquals(
            RpcCode.HEADER_MISMATCH,
            validateModernHeaders(modernHeaders(version = null), call("tools/call", "get_overview"))?.code,
        )
    }

    @Test
    fun rejectsProtocolVersionMismatch() {
        assertEquals(
            400,
            validateModernHeaders(modernHeaders(version = "2025-11-25"), call("tools/call", "get_overview"))?.status,
        )
    }

    @Test
    fun rejectsMissingOrWrongMethodHeader() {
        assertEquals(
            400,
            validateModernHeaders(modernHeaders(method = null), call("tools/call", "get_overview"))?.status,
        )
        assertEquals(
            400,
            validateModernHeaders(modernHeaders(method = "tools/list"), call("tools/call", "get_overview"))?.status,
        )
    }

    @Test
    fun rejectsMissingOrWrongToolName() {
        assertEquals(
            400,
            validateModernHeaders(modernHeaders(name = null), call("tools/call", "get_overview"))?.status,
        )
        assertEquals(
            400,
            validateModernHeaders(modernHeaders(name = "list_transactions"), call("tools/call", "get_overview"))?.status,
        )
    }

    @Test
    fun decodesBase64ToolName() {
        val encoded = "=?base64?Z2V0X292ZXJ2aWV3?="
        assertNull(validateModernHeaders(modernHeaders(name = encoded), call("tools/call", "get_overview")))
    }

    @Test
    fun toolsListNeedsNoName() {
        assertNull(validateModernHeaders(modernHeaders(method = "tools/list", name = null), call("tools/list")))
    }
}
