package app.fen.autobook.source

data class ImageHints(
    val amountAnchors: List<String>,
    val merchantAnchors: List<String>,
    val timeAnchors: List<String>,
    val directionAnchors: List<String>,
    val excludePatterns: List<String>,
    val preferLargestAmount: Boolean = true,
)
