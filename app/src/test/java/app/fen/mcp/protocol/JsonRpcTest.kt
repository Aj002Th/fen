package app.fen.mcp.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRpcTest {

    private fun ok(body: String): RpcCall =
        (parseRpc(body) as RpcParse.Ok).call

    @Test
    fun keepsStringAndNumberIds() {
        assertEquals("abc", (ok("""{"jsonrpc":"2.0","id":"abc","method":"tools/list"}""").id as JsonPrimitive).content)
        assertEquals("7", (ok("""{"jsonrpc":"2.0","id":7,"method":"tools/list"}""").id as JsonPrimitive).content)
    }

    @Test
    fun notificationHasNoId() {
        val call = ok("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertNull(call.id)
        assertTrue(call.isNotification)
    }

    @Test
    fun rejectsBatchArray() {
        val result = parseRpc("""[{"jsonrpc":"2.0","id":1,"method":"tools/list"}]""")
        assertEquals(RpcCode.INVALID_REQUEST, (result as RpcParse.Error).code)
    }

    @Test
    fun rejectsMalformedJson() {
        val result = parseRpc("{")
        assertEquals(RpcCode.PARSE_ERROR, (result as RpcParse.Error).code)
    }

    @Test
    fun rejectsMissingMethod() {
        val result = parseRpc("""{"jsonrpc":"2.0","id":1}""")
        assertEquals(RpcCode.INVALID_REQUEST, (result as RpcParse.Error).code)
    }

    @Test
    fun parsesModernMeta() {
        val call = ok(
            """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
              "_meta":{
                "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                "io.modelcontextprotocol/clientInfo":{"name":"Claude Code","version":"2.1"},
                "io.modelcontextprotocol/clientCapabilities":{}
              }
            }}
            """.trimIndent()
        )

        val meta = parseModernMeta(call.params)

        assertEquals(MODERN_PROTOCOL_VERSION, meta?.protocolVersion)
        assertEquals("Claude Code", meta?.clientInfo?.name)
        assertEquals("2.1", meta?.clientInfo?.version)
        assertTrue(meta?.capabilities != null)
    }

    @Test
    fun modernMetaRequiresProtocolVersion() {
        val call = ok("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{}}}""")
        assertNull(parseModernMeta(call.params))
    }

    @Test
    fun doesNotTreatPlainParamsAsModern() {
        val call = ok("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
        assertFalse(call.isNotification)
        assertNull(parseModernMeta(call.params))
    }
}
