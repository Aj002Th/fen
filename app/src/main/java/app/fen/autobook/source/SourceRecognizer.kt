package app.fen.autobook.source

interface SourceRecognizer {
    val id: String
    fun detect(text: String): Float
    fun pageHint(text: String): String?
    val billTypeHints: Map<String, String>
    val imageHints: ImageHints?
        get() = null
}
