package app.fen.autobook.parse

import app.fen.autobook.model.BillConfidence
import app.fen.autobook.model.ExtractorMode
import app.fen.autobook.model.OcrLine
import app.fen.autobook.model.OcrResult
import app.fen.autobook.model.ParsedBill
import app.fen.autobook.source.ImageHints
import java.time.ZoneId

class GenericBillParser {
    private val defaultAmountAnchors = listOf("实付", "合计", "金额", "支付金额")
    private val defaultExcludePatterns = listOf("订单号", "尾号", "卡号", "余额", "优惠", "红包", "积分")

    fun parse(
        lines: List<OcrLine>,
        nowMillis: Long,
        zone: ZoneId,
        hints: ImageHints? = null,
    ): ParsedBill {
        val normalized = lines.map { it.copy(text = TextNormalizer.normalize(it.text)) }
        val amountLine = selectAmountLine(normalized, hints)
        val amount = amountLine?.let { AmountParser.parse(it.text) } ?: 0L
        val occurredAt = selectTime(normalized, hints, nowMillis, zone)
        val type = DirectionParser.parse(normalized.joinToString(" ") { it.text })
        val merchant = selectMerchant(normalized, hints)
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

    private fun selectAmountLine(lines: List<OcrLine>, hints: ImageHints?): OcrLine? {
        val exclusions = hints?.excludePatterns ?: defaultExcludePatterns
        val candidates = lines.filter { line ->
            exclusions.none { line.text.contains(it) }
        }
        val anchors = hints?.amountAnchors ?: defaultAmountAnchors

        val anchored = candidates
            .filter { line -> anchors.any { line.text.contains(it) } }
            .mapNotNull { line -> AmountParser.parse(line.text)?.let { line to it } }
        val chosen = if (hints?.preferLargestAmount == false) {
            anchored.firstOrNull()
        } else {
            anchored.maxByOrNull { it.second }
        }
        if (chosen != null) return chosen.first

        return candidates
            .mapNotNull { line -> AmountParser.parse(line.text)?.let { line to it } }
            .maxByOrNull { it.first.box.height }
            ?.first
    }

    private fun selectTime(
        lines: List<OcrLine>,
        hints: ImageHints?,
        nowMillis: Long,
        zone: ZoneId,
    ): Long {
        if (hints != null) {
            lines.firstNotNullOfOrNull { line ->
                if (hints.timeAnchors.any { line.text.contains(it) }) {
                    DateTimeParser.parse(line.text, nowMillis, zone)
                } else {
                    null
                }
            }?.let { return it }
        }
        return lines.firstNotNullOfOrNull { DateTimeParser.parse(it.text, nowMillis, zone) } ?: nowMillis
    }

    private fun selectMerchant(lines: List<OcrLine>, hints: ImageHints?): String {
        if (hints != null) {
            val hinted = MerchantExtractor.extract(lines, hints.merchantAnchors)
            if (hinted.isNotBlank()) return hinted
        }
        return MerchantExtractor.extract(lines)
    }
}
