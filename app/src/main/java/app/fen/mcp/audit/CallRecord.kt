package app.fen.mcp.audit

data class CallRecord(
    val tsMs: Long,
    val clientIp: String,
    val clientName: String?,
    val clientVersion: String?,
    val era: String,
    val protocolVersion: String?,
    val method: String,
    val tool: String?,
    val argsSummary: String?,
    val ok: Boolean,
    val httpStatus: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val durationMs: Long,
    val requestId: String?,
    val batchId: String?,
    val tokenFp: String?,
    val bytesIn: Int,
    val bytesOut: Int,
)
