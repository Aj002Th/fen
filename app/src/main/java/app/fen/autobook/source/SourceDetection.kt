package app.fen.autobook.source

data class SourceMatch(
    val sourceId: String,
    val page: String?,
    val score: Float,
    val imageHints: ImageHints? = null,
)

class SourceDetection(
    private val recognizers: List<SourceRecognizer>,
    private val minScore: Float = 0.6f,
    private val minGap: Float = 0.15f,
) {
    fun detect(text: String): SourceMatch {
        val scored = recognizers
            .map { it to it.detect(text) }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return SourceMatch("generic", null, 0.5f)
        val secondScore = scored.getOrNull(1)?.second ?: 0f

        if (best.first.id == "generic") return SourceMatch("generic", null, best.second)

        val accepted = best.second >= minScore && best.second - secondScore >= minGap
        return if (accepted) {
            SourceMatch(best.first.id, best.first.pageHint(text), best.second, best.first.imageHints)
        } else {
            SourceMatch("generic", null, best.second)
        }
    }
}

internal fun scoreSource(
    text: String,
    titles: List<String>,
    pages: List<String>,
    aux: List<String>,
): Float {
    var score = 0f
    if (titles.any { text.contains(it) }) score += 0.4f
    if (pages.any { text.contains(it) }) score += 0.3f
    if (aux.any { text.contains(it) }) score += 0.1f
    return score.coerceAtMost(1f)
}
