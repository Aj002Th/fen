package app.fen.mcp.audit

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun auditJsonl(records: List<CallRecord>): String = records.joinToString("\n") { record ->
    buildJsonObject {
        put("ts_ms", record.tsMs)
        put("client_ip", record.clientIp)
        put("client_name", record.clientName)
        put("client_version", record.clientVersion)
        put("era", record.era)
        put("protocol_version", record.protocolVersion)
        put("method", record.method)
        put("tool", record.tool)
        put("ok", record.ok)
        put("http_status", record.httpStatus)
        put("error_code", record.errorCode)
        put("duration_ms", record.durationMs)
        put("batch_id", record.batchId)
        put("token_fp", record.tokenFp)
    }.toString()
}
