package app.fen.mcp.protocol

import app.fen.mcp.RequestContext
import app.fen.mcp.RpcOutcome
import app.fen.mcp.tools.ToolDescriptor
import app.fen.mcp.tools.ToolKind
import app.fen.mcp.tools.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolDispatcherTest {

    private val sessions = SessionStore({ 1_000_000L })
    private val echo = ToolDescriptor(
        name = "echo",
        description = "echo",
        inputSchema = buildJsonObject { },
        kind = ToolKind.READ,
        handler = { args -> ToolResult.Ok(buildJsonObject { put("echo", args["x"] ?: JsonNull) }) },
    )
    private val dispatcher = ProtocolDispatcher(
        tools = { listOf(echo) },
        callTool = { _, args -> echo.handler(args) },
        sessions = sessions,
        serverVersion = "0.9.0",
    )

    private fun call(
        method: String,
        params: JsonObject = buildJsonObject { },
        id: kotlinx.serialization.json.JsonElement? = JsonPrimitive(1),
    ) = RpcCall(id = id, method = method, params = params)

    private fun context(era: ProtocolEra, sessionId: String? = null) = RequestContext(
        headers = RequestHeaders(sessionId = sessionId),
        clientIp = "127.0.0.1",
        era = era,
        meta = null,
        sessionId = sessionId,
    )

    private fun ok(outcome: RpcOutcome): RpcOutcome.Json {
        assertTrue(outcome is RpcOutcome.Json)
        return outcome as RpcOutcome.Json
    }

    private fun body(outcome: RpcOutcome): JsonObject =
        Json.parseToJsonElement(ok(outcome).body).jsonObject

    @Test
    fun modernDiscoverAdvertisesVersions() = runTest {
        val result = body(dispatcher.handle(call("server/discover"), context(ProtocolEra.MODERN_2026_07_28)))["result"]!!.jsonObject

        assertEquals("complete", result["resultType"]!!.jsonPrimitive.content)
        assertEquals(3, result["supportedVersions"]!!.jsonArray.size)
        assertTrue(result["capabilities"]!!.jsonObject.containsKey("tools"))
        assertEquals("3600000", result["ttlMs"]!!.jsonPrimitive.content)
    }

    @Test
    fun modernToolsListIsImmediatelyStale() = runTest {
        val result = body(dispatcher.handle(call("tools/list"), context(ProtocolEra.MODERN_2026_07_28)))["result"]!!.jsonObject

        assertEquals(1, result["tools"]!!.jsonArray.size)
        assertEquals("0", result["ttlMs"]!!.jsonPrimitive.content)
        assertEquals("private", result["cacheScope"]!!.jsonPrimitive.content)
    }

    @Test
    fun toolCallWrapsContentAsText() = runTest {
        val params = buildJsonObject {
            put("name", "echo")
            put("arguments", buildJsonObject { put("x", "hi") })
        }

        val result = body(dispatcher.handle(call("tools/call", params), context(ProtocolEra.MODERN_2026_07_28)))["result"]!!.jsonObject
        val text = result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

        assertFalse(result["isError"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("hi", Json.parseToJsonElement(text).jsonObject["echo"]!!.jsonPrimitive.content)
    }

    @Test
    fun modernUnknownMethodIs404() = runTest {
        val outcome = dispatcher.handle(call("unknown/method"), context(ProtocolEra.MODERN_2026_07_28))

        assertTrue(outcome is RpcOutcome.HttpError)
        assertEquals(404, (outcome as RpcOutcome.HttpError).status)
    }

    @Test
    fun legacyInitializeReturnsSessionHeader() = runTest {
        val params = buildJsonObject {
            put("protocolVersion", LEGACY_PROTOCOL_VERSION)
            put("clientInfo", buildJsonObject { put("name", "test"); put("version", "1") })
        }

        val outcome = ok(dispatcher.handle(call("initialize", params), context(ProtocolEra.LEGACY_2025_11_25)))

        assertNotNull(outcome.headers["Mcp-Session-Id"])
        val result = Json.parseToJsonElement(outcome.body).jsonObject["result"]!!.jsonObject
        assertEquals(LEGACY_PROTOCOL_VERSION, result["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("fen", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun legacyRequestWithoutSessionIs404() = runTest {
        val outcome = dispatcher.handle(call("tools/list"), context(ProtocolEra.LEGACY_2025_11_25))

        assertTrue(outcome is RpcOutcome.HttpError)
        assertEquals(404, (outcome as RpcOutcome.HttpError).status)
    }

    @Test
    fun legacyToolsListHasNoResultType() = runTest {
        val init = ok(
            dispatcher.handle(
                call(
                    "initialize",
                    buildJsonObject { put("protocolVersion", LEGACY_PROTOCOL_VERSION) },
                ),
                context(ProtocolEra.LEGACY_2025_11_25),
            )
        )
        val sessionId = init.headers["Mcp-Session-Id"]!!

        val result = body(
            dispatcher.handle(call("tools/list"), context(ProtocolEra.LEGACY_2025_11_25, sessionId))
        )["result"]!!.jsonObject

        assertNull(result["resultType"])
        assertEquals(1, result["tools"]!!.jsonArray.size)
    }

    @Test
    fun legacyPingWorks() = runTest {
        val init = ok(
            dispatcher.handle(
                call("initialize", buildJsonObject { put("protocolVersion", LEGACY_PROTOCOL_VERSION) }),
                context(ProtocolEra.LEGACY_2025_11_25),
            )
        )
        val sessionId = init.headers["Mcp-Session-Id"]!!

        val result = body(
            dispatcher.handle(call("ping"), context(ProtocolEra.LEGACY_2025_11_25, sessionId))
        )["result"]!!.jsonObject

        assertTrue(result.isEmpty())
    }

    @Test
    fun notificationIsAccepted() = runTest {
        val outcome = dispatcher.handle(
            call("notifications/initialized", id = null),
            context(ProtocolEra.LEGACY_2025_11_25),
        )

        assertTrue(outcome is RpcOutcome.Accepted)
    }
}
