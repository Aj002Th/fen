package app.fen.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.fen.data.exchange.UNCATEGORIZED_NAME
import java.util.UUID

/**
 * 极简本地存储：手写 SQLite，避免在 AGP 9 / Kotlin 2.4 上引入注解处理器。
 * 金额统一使用「分」为单位的 Long，避免浮点误差。
 */
class FenDb(context: Context) : SQLiteOpenHelper(context, "fen.db", null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE category(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                icon TEXT NOT NULL DEFAULT 'other',
                color INTEGER NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE tx(
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                amount INTEGER NOT NULL,
                category_id TEXT,
                note TEXT NOT NULL DEFAULT '',
                occurred_at INTEGER NOT NULL,
                import_batch TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE import_batch(
                id TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                rows INTEGER NOT NULL,
                file_hash TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tx_occurred ON tx(occurred_at)")
        db.execSQL("CREATE INDEX idx_tx_batch ON tx(import_batch)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS tx")
            db.execSQL("DROP TABLE IF EXISTS category")
            db.execSQL("DROP TABLE IF EXISTS account")
            db.execSQL("DROP TABLE IF EXISTS budget")
            onCreate(db)
            return
        }
        if (oldVersion < 4) {
            db.execSQL("UPDATE tx SET category_id = NULL WHERE category_id IN (SELECT id FROM category WHERE name = '其他')")
            db.execSQL("DELETE FROM category WHERE name = '其他'")
        }
        if (oldVersion < 5) {
            // 拍平成单层：子分类的流水归到父分类，然后删掉子分类列
            db.execSQL(
                """
                UPDATE tx SET category_id = (
                    SELECT parent_id FROM category c WHERE c.id = tx.category_id
                ) WHERE category_id IN (SELECT id FROM category WHERE parent_id IS NOT NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE category_new(
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    icon TEXT NOT NULL DEFAULT 'other',
                    color INTEGER NOT NULL,
                    sort_order INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO category_new(id, name, type, icon, color, sort_order)
                SELECT id, name, type, icon, color, sort_order FROM category WHERE parent_id IS NULL
                """.trimIndent()
            )
            db.execSQL("DROP TABLE category")
            db.execSQL("ALTER TABLE category_new RENAME TO category")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE tx ADD COLUMN import_batch TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS import_batch(
                    id TEXT PRIMARY KEY,
                    source TEXT NOT NULL,
                    label TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    rows INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_batch ON tx(import_batch)")
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE import_batch ADD COLUMN file_hash TEXT")
        }
        if (oldVersion < 8) {
            // 「未分类」成为真实分类；NULL / 已删除分类的流水一并归进去
            for (type in TxType.entries) {
                val id = ensureUncategorized(db, type)
                db.execSQL(
                    """
                    UPDATE tx SET category_id = ?
                    WHERE type = ? AND (category_id IS NULL OR category_id NOT IN (SELECT id FROM category))
                    """.trimIndent(),
                    arrayOf(id, type.name),
                )
            }
        }
    }

    private fun seed(db: SQLiteDatabase) {
        var order = 0
        fun add(name: String, type: TxType, icon: String, color: Long) {
            db.insert("category", null, ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("name", name)
                put("type", type.name)
                put("icon", icon)
                put("color", color)
                put("sort_order", order++)
            })
        }

        val expense = listOf(
            "餐饮" to "food", "购物" to "shopping", "交通" to "transport",
            "娱乐" to "entertainment", "居住" to "housing", "医疗" to "medical",
            "学习" to "study",
        )
        expense.forEachIndexed { index, (name, icon) ->
            add(name, TxType.EXPENSE, icon, CategoryPaletteColors[index % CategoryPaletteColors.size])
        }

        val income = listOf("工资" to "salary", "奖金" to "bonus", "理财" to "invest")
        income.forEachIndexed { index, (name, icon) ->
            add(name, TxType.INCOME, icon, IncomePaletteColors[index % IncomePaletteColors.size])
        }

        // 「未分类」是一个真实分类，手工记账时也能直接选
        ensureUncategorized(db, TxType.EXPENSE)
        ensureUncategorized(db, TxType.INCOME)
    }

    fun categories(): List<Category> = readableDatabase.query(
        "category",
        arrayOf("id", "name", "type", "icon", "color", "sort_order"),
        null, null, null, null, "sort_order",
    ).use { c -> c.mapAll { it.toCategory() } }

    fun transactions(): List<Tx> = readableDatabase.query(
        "tx",
        arrayOf("id", "type", "amount", "category_id", "note", "occurred_at", "import_batch"),
        null, null, null, null, "occurred_at DESC",
    ).use { c -> c.mapAll { it.toTx() } }

    fun insertTx(tx: Tx) {
        writableDatabase.insertOrThrow("tx", null, tx.toValues())
    }

    fun updateTx(tx: Tx) {
        writableDatabase.update("tx", tx.toValues(), "id = ?", arrayOf(tx.id))
    }

    fun deleteTx(id: String) {
        writableDatabase.delete("tx", "id = ?", arrayOf(id))
    }

    fun insertCategory(category: Category) {
        writableDatabase.insertOrThrow("category", null, category.toValues())
    }

    fun updateCategory(category: Category) {
        writableDatabase.update("category", category.toValues(), "id = ?", arrayOf(category.id))
    }

    fun deleteCategory(id: String) {
        writableDatabase.delete("category", "id = ?", arrayOf(id))
    }

    /** 把某个分类下的所有流水转移到另一个分类 */
    fun reassignCategory(fromId: String, toId: String) {
        writableDatabase.update(
            "tx",
            ContentValues().apply { put("category_id", toId) },
            "category_id = ?",
            arrayOf(fromId),
        )
    }

    /** 删除分类以及它名下的所有流水 */
    fun deleteCategoryWithTransactions(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("tx", "category_id = ?", arrayOf(id))
            db.delete("category", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun countTransactions(categoryId: String): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM tx WHERE category_id = ?", arrayOf(categoryId)).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun nextCategoryOrder(): Int {
        readableDatabase.rawQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM category", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /**
     * 生成演示数据，仅用于开发与验收。
     * 最近 [daysBack] 天，约 [count] 条，时间分布在 07:00–22:59。
     */
    fun seedDemoData(count: Int = 400, daysBack: Int = 180) {
        // 演示数据里额外准备几个"用户自定义"分类，方便验收分类管理后的效果
        val extras = listOf(
            Triple("咖啡", "meal", 0xFFF2A93E),
            Triple("宠物", "pet", 0xFF8B6CF2),
            Triple("订阅", "digital", 0xFF21B8C7),
            Triple("健身", "sport", 0xFFE05C9A),
        )
        val existing = categories()
        extras.forEach { (name, icon, color) ->
            if (existing.none { it.name == name }) {
                insertCategory(
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        type = TxType.EXPENSE,
                        icon = icon,
                        color = color,
                        sortOrder = nextCategoryOrder(),
                    )
                )
            }
        }
        val all = categories()
        val expensePool = all.filter { it.type == TxType.EXPENSE }
        val incomePool = all.filter { it.type == TxType.INCOME }
        if (expensePool.isEmpty()) return

        val random = java.util.Random(20261121L)
        val notesByCategory = mapOf(
            "餐饮" to listOf("午饭", "外卖", "咖啡", "聚餐"),
            "购物" to listOf("超市", "衣服", "快递"),
            "交通" to listOf("地铁", "打车", "加油"),
            "娱乐" to listOf("电影", "游戏", "周末"),
            "居住" to listOf("房租", "水电", "物业"),
            "医疗" to listOf("药品", "门诊"),
            "学习" to listOf("买书", "课程"),
            "工资" to listOf("月薪"),
            "奖金" to listOf("年终奖"),
            "理财" to listOf("基金"),
            "咖啡" to listOf("瑞幸", "星巴克"),
            "宠物" to listOf("猫粮", "驱虫"),
            "订阅" to listOf("会员", "云盘"),
            "健身" to listOf("私教", "月卡"),
        )
        val db = writableDatabase
        db.beginTransaction()
        try {
            repeat(count) {
                val isIncome = incomePool.isNotEmpty() && random.nextInt(100) < 8
                val category = if (isIncome) {
                    incomePool[random.nextInt(incomePool.size)]
                } else {
                    expensePool[random.nextInt(expensePool.size)]
                }
                val amount: Long = if (isIncome) {
                    if (category.name == "工资") (8000 + random.nextInt(7000)) * 100L
                    else (200 + random.nextInt(2800)) * 100L
                } else if (random.nextInt(100) < 3) {
                    (1000 + random.nextInt(4000)) * 100L
                } else {
                    (8 + random.nextInt(313)) * 100L
                }
                val notePool = notesByCategory[category.name] ?: emptyList()
                val note = if (notePool.isEmpty() || random.nextInt(100) < 25) "" else {
                    notePool[random.nextInt(notePool.size)]
                }
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    add(java.util.Calendar.DAY_OF_YEAR, -random.nextInt(daysBack + 1))
                    set(java.util.Calendar.HOUR_OF_DAY, 7 + random.nextInt(16))
                    set(java.util.Calendar.MINUTE, random.nextInt(60))
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                insertTx(
                    Tx(
                        id = UUID.randomUUID().toString(),
                        type = if (isIncome) TxType.INCOME else TxType.EXPENSE,
                        amount = amount,
                        categoryId = category.id,
                        note = note,
                        occurredAt = calendar.timeInMillis,
                    )
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearTransactions() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("tx", null, null)
            db.delete("import_batch", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 单事务写入一次导入：先补分类，再写流水和批次记录。
     * 任何一行失败都整体回滚，不会留下半批数据。
     */
    fun insertImport(newCategories: List<Category>, txs: List<Tx>, batch: ImportBatch) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            newCategories.forEach { db.insertOrThrow("category", null, it.toValues()) }
            txs.forEach { db.insertOrThrow("tx", null, it.toValues()) }
            db.insertOrThrow("import_batch", null, ContentValues().apply {
                put("id", batch.id)
                put("source", batch.source)
                put("label", batch.label)
                put("created_at", batch.createdAt)
                put("rows", batch.rows)
                put("file_hash", batch.fileHash)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun importBatches(): List<ImportBatch> = readableDatabase.rawQuery(
        """
        SELECT b.id, b.source, b.label, b.created_at, b.rows,
               (SELECT COUNT(*) FROM tx t WHERE t.import_batch = b.id) AS remaining,
               b.file_hash
        FROM import_batch b
        ORDER BY b.created_at DESC
        """.trimIndent(),
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    ImportBatch(
                        id = c.getString(0),
                        source = c.getString(1),
                        label = c.getString(2),
                        createdAt = c.getLong(3),
                        rows = c.getInt(4),
                        remaining = c.getInt(5),
                        fileHash = c.getString(6),
                    )
                )
            }
        }
    }

    /** 是否还有未删除的批次导入过同一个文件。 */
    fun hasImportHash(hash: String): Boolean {
        if (hash.isBlank()) return false
        readableDatabase.rawQuery(
            "SELECT 1 FROM import_batch WHERE file_hash = ? LIMIT 1",
            arrayOf(hash),
        ).use { return it.moveToFirst() }
    }

    /** 删除一次导入以及它写入的全部流水 */
    fun deleteImportBatch(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("tx", "import_batch = ?", arrayOf(id))
            db.delete("import_batch", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 备份恢复：整体替换分类与流水（批次记录一并清空） */
    fun replaceAll(categories: List<Category>, txs: List<Tx>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("tx", null, null)
            db.delete("category", null, null)
            db.delete("import_batch", null, null)
            categories.forEach { db.insertOrThrow("category", null, it.toValues()) }
            txs.forEach { db.insertOrThrow("tx", null, it.toValues()) }
            // 备份里如果还有 category_id = NULL 的流水，也归到真实的「未分类」
            for (type in TxType.entries) {
                val hasNull = db.rawQuery(
                    "SELECT 1 FROM tx WHERE type = ? AND category_id IS NULL LIMIT 1",
                    arrayOf(type.name),
                ).use { it.moveToFirst() }
                if (hasNull) {
                    val id = ensureUncategorized(db, type)
                    db.execSQL(
                        "UPDATE tx SET category_id = ? WHERE type = ? AND category_id IS NULL",
                        arrayOf(id, type.name),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 确保「未分类」是一个真实分类，返回它的 id。 */
    fun ensureUncategorized(type: TxType): String = ensureUncategorized(writableDatabase, type)

    private fun ensureUncategorized(db: SQLiteDatabase, type: TxType): String {
        db.rawQuery(
            "SELECT id FROM category WHERE name = ? AND type = ? LIMIT 1",
            arrayOf(UNCATEGORIZED_NAME, type.name),
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        val id = UUID.randomUUID().toString()
        val order = db.rawQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM category", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        db.insertOrThrow(
            "category",
            null,
            Category(
                id = id,
                name = UNCATEGORIZED_NAME,
                type = type,
                icon = if (type == TxType.INCOME) "other_income" else "other",
                color = 0xFF7C8794,
                sortOrder = order,
            ).toValues(),
        )
        return id
    }

    private fun Tx.toValues() = ContentValues().apply {
        put("id", id)
        put("type", type.name)
        put("amount", amount)
        put("category_id", categoryId)
        put("note", note)
        put("occurred_at", occurredAt)
        put("import_batch", importBatch)
    }

    private fun Category.toValues() = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("icon", icon)
        put("color", color)
        put("sort_order", sortOrder)
    }

    private companion object {
        const val DB_VERSION = 8

        val CategoryPaletteColors = listOf(
            0xFFF2583E, 0xFF3E8BF2, 0xFF17A467, 0xFFF2A93E,
            0xFF8B6CF2, 0xFFE05C9A, 0xFF21B8C7, 0xFF7C8794,
        )
        val IncomePaletteColors = listOf(
            0xFF17A467, 0xFF3E8BF2, 0xFF8B6CF2, 0xFFF2A93E,
        )
    }
}

private inline fun <T> Cursor.mapAll(block: (Cursor) -> T): List<T> = buildList {
    while (moveToNext()) add(block(this@mapAll))
}

private fun Cursor.toCategory() = Category(
    id = getString(0),
    name = getString(1),
    type = TxType.valueOf(getString(2)),
    icon = getString(3),
    color = getLong(4),
    sortOrder = getInt(5),
)

private fun Cursor.toTx() = Tx(
    id = getString(0),
    type = TxType.valueOf(getString(1)),
    amount = getLong(2),
    categoryId = getString(3),
    note = getString(4),
    occurredAt = getLong(5),
    importBatch = getString(6),
)
