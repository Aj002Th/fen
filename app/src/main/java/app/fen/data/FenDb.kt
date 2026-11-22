package app.fen.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.fen.data.ledger.BatchItem
import app.fen.data.ledger.BatchMeta
import app.fen.data.ledger.BatchSummary
import app.fen.data.ledger.IdempotencyRecord
import java.util.UUID

/**
 * 极简本地存储：手写 SQLite，避免在 AGP 9 / Kotlin 2.4 上引入注解处理器。
 * 金额统一使用「分」为单位的 Long，避免浮点误差。
 */
class FenDb(context: Context, name: String = "fen.db") : SQLiteOpenHelper(context, name, null, DB_VERSION) {

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
                occurred_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tx_occurred ON tx(occurred_at)")
        createMcpTables(db)
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
            createMcpTables(db)
        }
    }

    private fun createMcpTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mcp_batch(
              id TEXT PRIMARY KEY,
              tool TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              undone_at INTEGER,
              client_name TEXT,
              args_summary TEXT,
              idempotency_key TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mcp_batch_item(
              batch_id TEXT NOT NULL,
              tx_id TEXT NOT NULL,
              before_json TEXT,
              after_json TEXT,
              PRIMARY KEY(batch_id, tx_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mcp_idempotency(
              key TEXT PRIMARY KEY,
              tool TEXT NOT NULL,
              args_hash TEXT NOT NULL,
              response_json TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              invalidated_at INTEGER
            )
            """.trimIndent()
        )
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
    }

    fun categories(): List<Category> = readableDatabase.query(
        "category",
        arrayOf("id", "name", "type", "icon", "color", "sort_order"),
        null, null, null, null, "sort_order",
    ).use { c -> c.mapAll { it.toCategory() } }

    fun transactions(): List<Tx> = readableDatabase.query(
        "tx",
        arrayOf("id", "type", "amount", "category_id", "note", "occurred_at"),
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

    fun txById(id: String): Tx? = readableDatabase.query(
        "tx",
        arrayOf("id", "type", "amount", "category_id", "note", "occurred_at"),
        "id = ?",
        arrayOf(id),
        null,
        null,
        null,
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toTx() else null }

    fun idempotency(key: String): IdempotencyRecord? = readableDatabase.query(
        "mcp_idempotency",
        arrayOf("key", "tool", "args_hash", "response_json", "created_at", "invalidated_at"),
        "key = ?",
        arrayOf(key),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        IdempotencyRecord(
            key = cursor.getString(0),
            tool = cursor.getString(1),
            argsHash = cursor.getString(2),
            responseJson = cursor.getString(3),
            createdAt = cursor.getLong(4),
            invalidatedAt = if (cursor.isNull(5)) null else cursor.getLong(5),
        )
    }

    fun insertIdempotency(meta: BatchMeta, createdAt: Long) {
        writableDatabase.insertOrThrow(
            "mcp_idempotency",
            null,
            ContentValues().apply {
                put("key", meta.idempotencyKey)
                put("tool", meta.tool)
                put("args_hash", meta.argsHash)
                put("response_json", meta.responseJson)
                put("created_at", createdAt)
            },
        )
    }

    fun invalidateIdempotency(key: String, at: Long) {
        writableDatabase.update(
            "mcp_idempotency",
            ContentValues().apply { put("invalidated_at", at) },
            "key = ?",
            arrayOf(key),
        )
    }

    fun insertMcpBatch(meta: BatchMeta, createdAt: Long) {
        writableDatabase.insertOrThrow(
            "mcp_batch",
            null,
            ContentValues().apply {
                put("id", meta.batchId)
                put("tool", meta.tool)
                put("created_at", createdAt)
                put("client_name", meta.clientName)
                put("idempotency_key", meta.idempotencyKey)
            },
        )
    }

    fun insertMcpBatchItem(
        batchId: String,
        txId: String,
        beforeJson: String?,
        afterJson: String?,
    ) {
        writableDatabase.insertOrThrow(
            "mcp_batch_item",
            null,
            ContentValues().apply {
                put("batch_id", batchId)
                put("tx_id", txId)
                put("before_json", beforeJson)
                put("after_json", afterJson)
            },
        )
    }

    fun mcpBatch(id: String): BatchSummary? = readableDatabase.rawQuery(
        """
        SELECT b.id, b.tool, b.created_at, b.undone_at, b.client_name,
               (SELECT COUNT(*) FROM mcp_batch_item i WHERE i.batch_id = b.id),
               b.idempotency_key
        FROM mcp_batch b WHERE b.id = ?
        """.trimIndent(),
        arrayOf(id),
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        BatchSummary(
            id = cursor.getString(0),
            tool = cursor.getString(1),
            createdAt = cursor.getLong(2),
            undoneAt = if (cursor.isNull(3)) null else cursor.getLong(3),
            clientName = if (cursor.isNull(4)) null else cursor.getString(4),
            itemCount = cursor.getInt(5),
            idempotencyKey = if (cursor.isNull(6)) null else cursor.getString(6),
        )
    }

    fun mcpBatchItems(batchId: String): List<BatchItem> = readableDatabase.query(
        "mcp_batch_item",
        arrayOf("tx_id", "before_json", "after_json"),
        "batch_id = ?",
        arrayOf(batchId),
        null,
        null,
        "tx_id",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    BatchItem(
                        txId = cursor.getString(0),
                        beforeJson = if (cursor.isNull(1)) null else cursor.getString(1),
                        afterJson = if (cursor.isNull(2)) null else cursor.getString(2),
                    )
                )
            }
        }
    }

    fun markBatchUndone(id: String, at: Long) {
        writableDatabase.update(
            "mcp_batch",
            ContentValues().apply { put("undone_at", at) },
            "id = ? AND undone_at IS NULL",
            arrayOf(id),
        )
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
        writableDatabase.delete("tx", null, null)
    }

    private fun Tx.toValues() = ContentValues().apply {
        put("id", id)
        put("type", type.name)
        put("amount", amount)
        put("category_id", categoryId)
        put("note", note)
        put("occurred_at", occurredAt)
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
        const val DB_VERSION = 6

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
)
