package app.fen.autobook.diagnostics

class RecognitionLog(private val maxEntries: Int = 200) {
    private val entries = ArrayDeque<String>()

    fun add(entry: String) {
        if (entry.isBlank()) return
        entries.addLast(entry)
        while (entries.size > maxEntries) {
            entries.removeFirst()
        }
    }

    fun snapshot(): List<String> = entries.toList()

    fun clear() {
        entries.clear()
    }
}
