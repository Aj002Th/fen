package app.fen.autobook.pipeline

import app.fen.autobook.model.BillConfidence
import app.fen.autobook.model.ExtractorMode
import app.fen.autobook.model.OcrLine
import app.fen.autobook.model.OcrResult
import app.fen.autobook.model.ParsedBill
import app.fen.autobook.parse.GenericBillParser
import app.fen.autobook.parse.TextNormalizer
import app.fen.autobook.source.SourceDetection
import java.time.ZoneId

class RecognitionEngine(
    private val detection: SourceDetection,
    private val parser: GenericBillParser,
) {
    fun recognize(lines: List<OcrLine>, nowMillis: Long, zone: ZoneId): ParsedBill {
        val rawText = OcrResult(
            lines.map { it.copy(text = TextNormalizer.normalize(it.text)) }
        ).fullText
        val match = detection.detect(rawText)
        val parsed = parser.parse(lines, nowMillis, zone, match.imageHints)
        val bill = parsed.copy(
            sourceId = match.sourceId,
            sourcePage = match.page,
            extractor = ExtractorMode.IMAGE,
        )
        return bill.copy(confidence = BillConfidence.of(bill))
    }
}
