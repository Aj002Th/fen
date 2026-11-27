package app.fen.mcp.audit

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class McpAuditDb(context: Context) :
    SQLiteOpenHelper(context, "mcp_audit.db", null, VERSION),
    CallLogStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE call_log(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              ts_ms INTEGER NOT NULL,
              client_ip TEXT NOT NULL,
              client_name TEXT,
              client_version TEXT,
              era TEXT NOT NULL,
              protocol_version TEXT,
              method TEXT NOT NULL,
              tool TEXT,
              args_summary TEXT,
              ok INTEGER NOT NULL,
              http_status INTEGER,
              error_code TEXT,
              error_message TEXT,
              duration_ms INTEGER,
              request_id TEXT,
              batch_id TEXT,
              token_fp TEXT,
              bytes_in INTEGER,
              bytes_out INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_call_ts ON call_log(ts_ms DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun insert(record: CallRecord) {
        writableDatabase.insertOrThrow(
            "call_log",
            null,
            ContentValues().apply {
                put("ts_ms", record.tsMs)
                put("client_ip", record.clientIp)
                put("client_name", record.clientName)
                put("client_version", record.clientVersion)
                put("era", record.era)
                put("protocol_version", record.protocolVersion)
                put("method", record.method)
                put("tool", record.tool)
                put("args_summary", record.argsSummary)
                put("ok", if (record.ok) 1 else 0)
                put("http_status", record.httpStatus)
                put("error_code", record.errorCode)
                put("error_message", record.errorMessage)
                put("duration_ms", record.durationMs)
                put("request_id", record.requestId)
                put("batch_id", record.batchId)
                put("token_fp", record.tokenFp)
                put("bytes_in", record.bytesIn)
                put("bytes_out", record.bytesOut)
            },
        )
    }

    override fun recent(limit: Int): List<CallRecord> =
        readableDatabase.rawQuery(
            "SELECT * FROM call_log ORDER BY ts_ms DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 500).toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CallRecord(
                            tsMs = cursor.long("ts_ms"),
                            clientIp = cursor.string("client_ip"),
                            clientName = cursor.stringOrNull("client_name"),
                            clientVersion = cursor.stringOrNull("client_version"),
                            era = cursor.string("era"),
                            protocolVersion = cursor.stringOrNull("protocol_version"),
                            method = cursor.string("method"),
                            tool = cursor.stringOrNull("tool"),
                            argsSummary = cursor.stringOrNull("args_summary"),
                            ok = cursor.getInt(cursor.getColumnIndexOrThrow("ok")) == 1,
                            httpStatus = cursor.getInt(cursor.getColumnIndexOrThrow("http_status")),
                            errorCode = cursor.stringOrNull("error_code"),
                            errorMessage = cursor.stringOrNull("error_message"),
                            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")),
                            requestId = cursor.stringOrNull("request_id"),
                            batchId = cursor.stringOrNull("batch_id"),
                            tokenFp = cursor.stringOrNull("token_fp"),
                            bytesIn = cursor.getInt(cursor.getColumnIndexOrThrow("bytes_in")),
                            bytesOut = cursor.getInt(cursor.getColumnIndexOrThrow("bytes_out")),
                        )
                    )
                }
            }
        }

    override fun clear() {
        writableDatabase.delete("call_log", null, null)
    }

    fun prune(now: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        db.delete("call_log", "ts_ms < ?", arrayOf((now - 30L * 24 * 60 * 60 * 1000).toString()))
        db.execSQL(
            "DELETE FROM call_log WHERE id NOT IN (SELECT id FROM call_log ORDER BY id DESC LIMIT 50000)"
        )
    }

    private fun android.database.Cursor.long(column: String): Long =
        getLong(getColumnIndexOrThrow(column))

    private fun android.database.Cursor.string(column: String): String =
        getString(getColumnIndexOrThrow(column))

    private fun android.database.Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private companion object {
        const val VERSION = 1
    }
}
