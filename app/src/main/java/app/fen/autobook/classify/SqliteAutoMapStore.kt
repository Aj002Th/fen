package app.fen.autobook.classify

import app.fen.data.FenDb

class SqliteAutoMapStore(private val db: FenDb) : AutoMapStore {
    override fun find(key: String): String? {
        val (merchant, type) = split(key)
        return db.findAutoMap(merchant, type)
    }

    override fun upsert(key: String, categoryId: String) {
        val (merchant, type) = split(key)
        db.upsertAutoMap(merchant, type, categoryId)
    }

    override fun deleteForCategory(categoryId: String) = db.deleteAutoMapForCategory(categoryId)

    override fun all(): Map<String, String> = db.allAutoMaps()

    private fun split(key: String): Pair<String, String> {
        val index = key.lastIndexOf('|')
        return if (index < 0) key to "" else key.substring(0, index) to key.substring(index + 1)
    }
}
