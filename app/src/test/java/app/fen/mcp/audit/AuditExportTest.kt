package app.fen.mcp.audit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditExportTest {

    private val record = CallRecord(
        tsMs = 1_000,
        clientIp = "192.168.1.9",
        clientName = "Claude Code",
        clientVersion = "2.1",
        era = "MODERN_2026_07_28",
        protocolVersion = "2026-07-28",
        method = "tools/call",
        tool = "get_overview",
        argsSummary = "{}",
        ok = true,
        httpStatus = 200,
        errorCode = null,
        errorMessage = null,
        durationMs = 12,
        requestId = "1",
        batchId = null,
        tokenFp = "abcd1234",
        bytesIn = 2,
        bytesOut = 20,
    )

    @Test
    fun exportsOneJsonObjectPerLine() {
        val lines = auditJsonl(listOf(record, record)).lines()
        assertEquals(2, lines.size)
        val parsed = Json.parseToJsonElement(lines[0]).jsonObject
        assertEquals("get_overview", parsed["tool"]!!.jsonPrimitive.content)
        assertFalse(lines[0].contains("token="))
    }

    @Test
    fun emptyListIsEmptyString() {
        assertTrue(auditJsonl(emptyList()).isEmpty())
    }
}
