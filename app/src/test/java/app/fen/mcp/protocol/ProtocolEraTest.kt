package app.fen.mcp.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolEraTest {

    private fun call(method: String, params: String = "{}"): RpcCall =
        (parseRpc("""{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}""") as RpcParse.Ok).call

    @Test
    fun initializeIsLegacy() {
        assertEquals(
            ProtocolEra.LEGACY_2025_11_25,
            detectEra(call("initialize"), RequestHeaders()),
        )
    }

    @Test
    fun modernMetaWins() {
        val params = """{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}"""
        assertEquals(
            ProtocolEra.MODERN_2026_07_28,
            detectEra(call("tools/list", params), RequestHeaders(sessionId = "legacy-session")),
        )
    }

    @Test
    fun sessionIdWithoutMetaIsLegacy() {
        assertEquals(
            ProtocolEra.LEGACY_2025_11_25,
            detectEra(call("tools/list"), RequestHeaders(sessionId = "s-1")),
        )
    }

    @Test
    fun noMetaAndNoSessionIsUnknown() {
        assertNull(detectEra(call("tools/list"), RequestHeaders()))
    }

    @Test
    fun modernResultCarriesResultTypeAndServerInfo() {
        val payload = buildJsonObject { put("tools", JsonPrimitive("x")) }

        val result = modernResult(JsonPrimitive(1), payload, "0.8.0")

        val body = result["result"]!!.jsonObject
        assertEquals("complete", body["resultType"]!!.jsonPrimitive.content)
        assertEquals(
            "fen",
            body["_meta"]!!.jsonObject["io.modelcontextprotocol/serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun legacyResultHasNoResultType() {
        val result = legacyResult(JsonPrimitive(1), buildJsonObject { put("tools", JsonPrimitive("x")) })

        val body = result["result"]!!.jsonObject
        assertNull(body["resultType"])
    }

    @Test
    fun modernListResultIsImmediatelyStaleAndPrivate() {
        val result = modernListResult(JsonPrimitive(1), buildJsonObject { put("tools", JsonPrimitive("x")) }, "0.8.0")

        val body = result["result"]!!.jsonObject
        assertEquals("0", body["ttlMs"]!!.jsonPrimitive.content)
        assertEquals("private", body["cacheScope"]!!.jsonPrimitive.content)
    }

    @Test
    fun errorHasJsonRpcShape() {
        val error = rpcError(JsonPrimitive(3), RpcCode.HEADER_MISMATCH, "Header mismatch")

        assertEquals("2.0", error["jsonrpc"]!!.jsonPrimitive.content)
        assertEquals("-32020", error["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }
}
