package app.fen.autobook.model

data class BoundingBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

data class OcrLine(val text: String, val box: BoundingBox, val confidence: Float?)

data class OcrResult(val lines: List<OcrLine>) {
    val fullText: String
        get() = lines.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}
