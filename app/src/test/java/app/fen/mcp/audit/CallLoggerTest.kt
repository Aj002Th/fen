package app.fen.mcp.audit

import app.fen.mcp.RequestContext
import app.fen.mcp.RpcOutcome
import app.fen.mcp.protocol.ClientInfo
import app.fen.mcp.protocol.ProtocolEra
import app.fen.mcp.protocol.RequestHeaders
import app.fen.mcp.protocol.RpcCall
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLoggerTest {

    private val records = mutableListOf<CallRecord>()
    private val store = object : CallLogStore {
        override fun insert(record: CallRecord) {
            records += record
        }

        override fun recent(limit: Int) = records.take(limit)
        override fun clear() = records.clear()
    }

    private var now = 1_000L
    private val logger = CallLogger(store, { now }, "abcd1234")

    private fun context() = RequestContext(
        headers = RequestHeaders(protocolVersion = "2026-07-28"),
        clientIp = "192.168.1.9",
        era = ProtocolEra.MODERN_2026_07_28,
        meta = app.fen.mcp.protocol.ModernMeta(
            protocolVersion = "2026-07-28",
            clientInfo = ClientInfo("Claude Code", "2.1"),
            capabilities = buildJsonObject { },
        ),
        sessionId = null,
    )

    private fun call() = RpcCall(
        id = JsonPrimitive(1),
        method = "tools/call",
        params = buildJsonObject {
            put("name", "get_overview")
            put("arguments", buildJsonObject { put("x", "1") })
        },
    )

    @Test
    fun recordsSuccessfulCall() = runTest {
        val outcome = logger.around(context(), call()) {
            now += 37
            RpcOutcome.Json("""{"jsonrpc":"2.0","id":1,"result":{"isError":false}}""")
        }

        assertTrue(outcome is RpcOutcome.Json)
        val record = records.single()
        assertTrue(record.ok)
        assertEquals("get_overview", record.tool)
        assertEquals("Claude Code", record.clientName)
        assertEquals(37L, record.durationMs)
        assertEquals("abcd1234", record.tokenFp)
    }

    @Test
    fun recordsToolError() = runTest {
        logger.around(context(), call()) {
            RpcOutcome.Json("""{"jsonrpc":"2.0","id":1,"result":{"isError":true}}""")
        }

        assertFalse(records.single().ok)
    }

    @Test
    fun recordsNestedToolErrorCode() = runTest {
        logger.around(context(), call()) {
            RpcOutcome.Json(
                """{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"{\"code\":\"VALIDATION\",\"message\":\"bad amount\"}"}],"isError":true}}"""
            )
        }

        val record = records.single()
        assertFalse(record.ok)
        assertEquals("VALIDATION", record.errorCode)
        assertEquals("bad amount", record.errorMessage)
    }

    @Test
    fun fallbackWhenStoreFails() = runTest {
        val failed = mutableListOf<CallRecord>()
        val broken = CallLogger(
            store = object : CallLogStore {
                override fun insert(record: CallRecord) = throw IllegalStateException("disk full")
                override fun recent(limit: Int) = emptyList<CallRecord>()
                override fun clear() = Unit
            },
            clock = { 1L },
            tokenFingerprint = null,
            fallback = { failed += it },
        )

        broken.around(context(), call()) { RpcOutcome.Accepted }

        assertEquals(1, failed.size)
    }
}
