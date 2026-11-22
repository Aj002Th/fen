package app.fen.autobook.classify

class InMemoryAutoMapStore : AutoMapStore {
    private val map = LinkedHashMap<String, String>()

    override fun find(key: String): String? = map[key]

    override fun upsert(key: String, categoryId: String) {
        map[key] = categoryId
    }

    override fun deleteForCategory(categoryId: String) {
        map.entries.removeAll { it.value == categoryId }
    }

    override fun all(): Map<String, String> = map.toMap()
}
