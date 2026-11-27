package app.fen.mcp.audit

interface CallLogStore {
    fun insert(record: CallRecord)
    fun recent(limit: Int): List<CallRecord>
    fun clear()
}
