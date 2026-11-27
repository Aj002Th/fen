package app.fen.autobook.source.recognizers

import app.fen.autobook.source.SourceRecognizer

object GenericRecognizer : SourceRecognizer {
    override val id = "generic"

    override fun detect(text: String): Float = 0.5f

    override fun pageHint(text: String): String? = null

    override val billTypeHints = emptyMap<String, String>()
}
