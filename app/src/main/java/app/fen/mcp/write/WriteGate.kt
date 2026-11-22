package app.fen.mcp.write

class WriteGate(private val clock: () -> Long) {

    fun isEnabled(writeUntil: Long?): Boolean = writeUntil != null && writeUntil > clock()

    fun remainingMs(writeUntil: Long?): Long {
        val until = writeUntil ?: return 0
        return (until - clock()).coerceAtLeast(0)
    }
}
