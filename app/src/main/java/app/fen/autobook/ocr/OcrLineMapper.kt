package app.fen.autobook.ocr

import app.fen.autobook.model.BoundingBox
import app.fen.autobook.model.OcrLine
import app.fen.autobook.model.OcrResult

data class RawOcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float?,
)

object OcrLineMapper {
    fun map(raw: List<RawOcrLine>): OcrResult = OcrResult(
        raw.map {
            OcrLine(
                text = it.text,
                box = BoundingBox(it.left, it.top, it.right, it.bottom),
                confidence = it.confidence,
            )
        }
    )
}
