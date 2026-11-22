package app.fen.autobook.parse

import app.fen.autobook.model.BillConfidence
import app.fen.autobook.model.ExtractorMode
import app.fen.autobook.model.OcrLine
import app.fen.autobook.model.OcrResult
import app.fen.autobook.model.ParsedBill
import java.time.ZoneId

class GenericBillParser {
    private val amountAnchors = listOf("实付", "合计", "金额", "支付金额")

    fun parse(lines: List<OcrLine>, nowMillis: Long, zone: ZoneId): ParsedBill {
        val normalized = lines.map { it.copy(text = TextNormalizer.normalize(it.text)) }
        val amountLine = selectAmountLine(normalized)
        val amount = amountLine?.let { AmountParser.parse(it.text) } ?: 0L
        val occurredAt = normalized.firstNotNullOfOrNull {
            DateTimeParser.parse(it.text, nowMillis, zone)
        } ?: nowMillis
        val type = DirectionParser.parse(normalized.joinToString(" ") { it.text })
        val merchant = MerchantExtractor.extract(normalized)
        val rawText = OcrResult(normalized).fullText

        val bill = ParsedBill(
            amountMinor = amount,
            type = type,
            categoryId = null,
            merchant = merchant,
            occurredAt = occurredAt,
            rawText = rawText,
            sourceId = null,
            sourcePage = null,
            extractor = ExtractorMode.IMAGE,
            confidence = 0f,
            evidence = listOfNotNull(amountLine?.text).filter { it.isNotBlank() },
        )
        return bill.copy(confidence = BillConfidence.of(bill))
    }

    private fun selectAmountLine(lines: List<OcrLine>): OcrLine? {
        val anchored = lines
            .filter { line -> amountAnchors.any { line.text.contains(it) } }
            .mapNotNull { line -> AmountParser.parse(line.text)?.let { line to it } }
            .maxByOrNull { it.second }
        if (anchored != null) return anchored.first

        return lines
            .mapNotNull { line -> AmountParser.parse(line.text)?.let { line to it } }
            .maxByOrNull { it.first.box.height }
            ?.first
    }
}
