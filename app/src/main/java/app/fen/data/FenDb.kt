package app.fen.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
                parent_id TEXT,
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
        seed(db)
    }

    /**
     * v3：移除账户、转账、预算，流水不再关联账户。
     * 数据结构变化较大，直接重建（原型阶段可接受）。
     */
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
            // 「其他」分类没有语义，删掉；挂在上面的流水变成未分类
            db.execSQL("UPDATE tx SET category_id = NULL WHERE category_id IN (SELECT id FROM category WHERE name = '其他')")
            db.execSQL("DELETE FROM category WHERE name = '其他'")
        }
    }

    private fun seed(db: SQLiteDatabase) {
        var order = 0
        fun addCategory(
            name: String,
            type: TxType,
            icon: String,
            color: Long,
            parentId: String? = null,
        ): String {
            val id = UUID.randomUUID().toString()
            db.insert("category", null, ContentValues().apply {
                put("id", id)
                put("parent_id", parentId)
                put("name", name)
                put("type", type.name)
                put("icon", icon)
                put("color", color)
                put("sort_order", order++)
            })
            return id
        }

        val expenseTree = listOf(
            Triple("餐饮", "food", listOf("早餐", "午餐", "晚餐", "外卖", "咖啡")),
            Triple("购物", "shopping", listOf("日用品", "服饰", "数码", "美妆")),
            Triple("交通", "transport", listOf("地铁", "打车", "加油", "停车")),
            Triple("娱乐", "entertainment", listOf("电影", "游戏", "旅行")),
            Triple("居住", "housing", listOf("房租", "水电", "物业")),
            Triple("医疗", "medical", listOf("门诊", "药品")),
            Triple("学习", "study", listOf("书籍", "课程")),
        )
        expenseTree.forEachIndexed { index, (name, icon, children) ->
            val color = CategoryPaletteColors[index % CategoryPaletteColors.size]
            val parentId = addCategory(name, TxType.EXPENSE, icon, color)
            children.forEach { child ->
                addCategory(child, TxType.EXPENSE, icon, color, parentId)
            }
        }

        val incomeTree = listOf(
            Triple("工资", "salary", emptyList<String>()),
            Triple("奖金", "bonus", emptyList<String>()),
            Triple("理财", "invest", emptyList<String>()),
        )
        incomeTree.forEachIndexed { index, (name, icon, children) ->
            val color = IncomePaletteColors[index % IncomePaletteColors.size]
            val parentId = addCategory(name, TxType.INCOME, icon, color)
            children.forEach { child ->
                addCategory(child, TxType.INCOME, icon, color, parentId)
            }
        }
    }

    fun categories(): List<Category> = readableDatabase.query(
        "category",
        arrayOf("id", "parent_id", "name", "type", "icon", "color", "sort_order"),
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

    /**
     * 生成演示数据，仅用于开发与验收。
     * 最近 [daysBack] 天，约 [count] 条，时间分布在 07:00–22:59。
     */
    fun seedDemoData(count: Int = 400, daysBack: Int = 180) {
        val all = categories()
        val expenseLeaves = all.filter { it.type == TxType.EXPENSE && it.parentId != null }
        val expenseParents = all.filter { it.type == TxType.EXPENSE && it.isParent }
        val incomeParents = all.filter { it.type == TxType.INCOME && it.isParent }
        val expensePool = expenseLeaves.ifEmpty { expenseParents }
        if (expensePool.isEmpty()) return

        val random = java.util.Random(20261121L)
        val notesByCategory = mapOf(
            "早餐" to listOf("豆浆油条", "包子"), "午餐" to listOf("食堂", "简餐"),
            "晚餐" to listOf("做饭", "外卖"), "外卖" to listOf("美团", "饿了么"), "咖啡" to listOf("瑞幸", "星巴克"),
            "地铁" to listOf("通勤"), "打车" to listOf("滴滴"), "加油" to listOf("中石化"), "停车" to listOf("停车费"),
            "日用品" to listOf("超市"), "服饰" to listOf("衣服"), "数码" to listOf("配件"), "美妆" to listOf("护肤"),
            "电影" to listOf("电影票"), "游戏" to listOf("充值"), "旅行" to listOf("机票"),
            "房租" to listOf("房租"), "水电" to listOf("水电费"), "物业" to listOf("物业费"),
            "门诊" to listOf("挂号"), "药品" to listOf("买药"),
            "书籍" to listOf("买书"), "课程" to listOf("网课"),
            "工资" to listOf("月薪"), "奖金" to listOf("年终奖"), "理财" to listOf("基金"),
        )
        val db = writableDatabase
        db.beginTransaction()
        try {
            repeat(count) {
                val isIncome = incomeParents.isNotEmpty() && random.nextInt(100) < 8
                val category = if (isIncome) {
                    incomeParents[random.nextInt(incomeParents.size)]
                } else {
                    expensePool[random.nextInt(expensePool.size)]
                }
                val amount: Long = if (isIncome) {
                    if (category.name == "工资") {
                        (8000 + random.nextInt(7000)) * 100L
                    } else {
                        (200 + random.nextInt(2800)) * 100L
                    }
                } else if (random.nextInt(100) < 3) {
                    (1000 + random.nextInt(4000)) * 100L
                } else {
                    (8 + random.nextInt(313)) * 100L
                }
                val dayOffset = random.nextInt(daysBack + 1)
                val notePool = notesByCategory[category.name] ?: emptyList()
                val note = if (notePool.isEmpty() || random.nextInt(100) < 25) "" else {
                    notePool[random.nextInt(notePool.size)]
                }
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    add(java.util.Calendar.DAY_OF_YEAR, -dayOffset)
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

    private companion object {
        const val DB_VERSION = 4

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
    parentId = getString(1),
    name = getString(2),
    type = TxType.valueOf(getString(3)),
    icon = getString(4),
    color = getLong(5),
    sortOrder = getInt(6),
)

private fun Cursor.toTx() = Tx(
    id = getString(0),
    type = TxType.valueOf(getString(1)),
    amount = getLong(2),
    categoryId = getString(3),
    note = getString(4),
    occurredAt = getLong(5),
)
