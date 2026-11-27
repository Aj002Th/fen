package app.fen.autobook.classify

import app.fen.data.Category

class RuleClassifier(private val rules: Map<String, String>) {
    fun match(text: String, categories: List<Category>): Category? {
        for ((keyword, name) in rules) {
            if (text.contains(keyword)) {
                categories.firstOrNull { it.name == name }?.let { return it }
            }
        }
        return null
    }
}
