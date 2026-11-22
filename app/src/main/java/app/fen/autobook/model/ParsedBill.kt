package app.fen.autobook.model

import app.fen.data.TxType

enum class ExtractorMode { IMAGE, NODE }

data class ParsedBill(
    val amountMinor: Long,
    val type: TxType?,
    val categoryId: String?,
    val merchant: String,
    val occurredAt: Long,
    val rawText: String,
    val sourceId: String?,
    val sourcePage: String?,
    val extractor: ExtractorMode,
    val confidence: Float,
    val evidence: List<String>,
    val timeGuessed: Boolean = false,
)
