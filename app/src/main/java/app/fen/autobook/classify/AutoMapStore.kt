package app.fen.autobook.classify

interface AutoMapStore {
    fun find(key: String): String?
    fun upsert(key: String, categoryId: String)
    fun deleteForCategory(categoryId: String)
    fun updateCategory(fromId: String, toId: String)
    fun all(): Map<String, String>
}
