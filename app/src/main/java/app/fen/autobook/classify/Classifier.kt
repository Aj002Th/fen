package app.fen.autobook.classify

import app.fen.autobook.model.ParsedBill
import app.fen.data.Category
import app.fen.data.TxType

enum class ClassificationSource { MAPPING, RULE, AI, USER }

data class Classification(
    val categoryId: String?,
    val source: ClassificationSource,
    val confidence: Float,
)

class Classifier(
    private val maps: AutoMapStore,
    private val rules: RuleClassifier,
) {
    fun classify(bill: ParsedBill, categories: List<Category>): Classification {
        val candidates = bill.type
            ?.let { type -> categories.filter { it.type == type } }
            ?: categories

        val mappedKey = MerchantKey.of(bill.merchant, bill.type ?: TxType.EXPENSE)
        val mappedId = if (mappedKey.isBlank()) null else maps.find(mappedKey)
        val mapped = candidates.firstOrNull { it.id == mappedId }
        if (mapped != null) return Classification(mapped.id, ClassificationSource.MAPPING, 1f)

        val hintedName = SourceCategoryHints.categoryName(bill.sourceId, bill.sourcePage)
        val hinted = candidates.firstOrNull { it.name == hintedName }
        if (hinted != null) return Classification(hinted.id, ClassificationSource.RULE, 0.7f)

        val ruled = rules.match(bill.rawText.ifBlank { bill.merchant }, candidates)
        if (ruled != null) return Classification(ruled.id, ClassificationSource.RULE, 0.8f)

        return Classification(null, ClassificationSource.RULE, 0.3f)
    }

    fun apply(bill: ParsedBill, categories: List<Category>): ParsedBill {
        val result = classify(bill, categories)
        return bill.copy(categoryId = result.categoryId ?: bill.categoryId)
    }
}
