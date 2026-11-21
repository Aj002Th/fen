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
            CREATE TABLE account(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                initial_balance INTEGER NOT NULL DEFAULT 0,
                color INTEGER NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE category(
                id TEXT PRIMARY KEY,
                parent_id TEXT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                icon TEXT NOT NULL DEFAULT 'other',
                emoji TEXT NOT NULL DEFAULT '',
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
                account_id TEXT NOT NULL,
                to_account_id TEXT,
                category_id TEXT,
                note TEXT NOT NULL DEFAULT '',
                occurred_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tx_occurred ON tx(occurred_at)")
        db.execSQL("CREATE INDEX idx_tx_account ON tx(account_id)")
        db.execSQL("CREATE TABLE budget(period TEXT PRIMARY KEY, amount INTEGER NOT NULL)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE category ADD COLUMN icon TEXT NOT NULL DEFAULT 'other'")
            // 老数据按名称回填图标 key
            nameToIcon.forEach { (name, icon) ->
                db.execSQL("UPDATE category SET icon = ? WHERE name = ?", arrayOf(icon, name))
            }
        }
    }

    private fun seed(db: SQLiteDatabase) {
        listOf(
            SeedAccount("现金", AccountType.CASH, 0xFF5F7A61),
            SeedAccount("招商银行", AccountType.DEBIT, 0xFF35618E),
            SeedAccount("支付宝", AccountType.VIRTUAL, 0xFF2A8FD8),
            SeedAccount("信用卡", AccountType.CREDIT, 0xFF8B5E83),
        ).forEachIndexed { index, a ->
            db.insert("account", null, ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("name", a.name)
                put("type", a.type.name)
                put("initial_balance", 0L)
                put("color", a.color)
                put("sort_order", index)
                put("archived", 0)
            })
        }

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
            Triple("其他", "other", emptyList<String>()),
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
            Triple("其他", "other_income", emptyList<String>()),
        )
        incomeTree.forEachIndexed { index, (name, icon, children) ->
            val color = IncomePaletteColors[index % IncomePaletteColors.size]
            val parentId = addCategory(name, TxType.INCOME, icon, color)
            children.forEach { child ->
                addCategory(child, TxType.INCOME, icon, color, parentId)
            }
        }
    }

    fun accounts(): List<Account> = readableDatabase.query(
        "account",
        arrayOf("id", "name", "type", "initial_balance", "color", "sort_order", "archived"),
        "archived = 0", null, null, null, "sort_order",
    ).use { c -> c.mapAll { it.toAccount() } }

    fun categories(): List<Category> = readableDatabase.query(
        "category",
        arrayOf("id", "parent_id", "name", "type", "icon", "color", "sort_order"),
        null, null, null, null, "sort_order",
    ).use { c -> c.mapAll { it.toCategory() } }

    fun transactions(): List<Tx> = readableDatabase.query(
        "tx",
        arrayOf("id", "type", "amount", "account_id", "to_account_id", "category_id", "note", "occurred_at"),
        null, null, null, null, "occurred_at DESC",
    ).use { c -> c.mapAll { it.toTx() } }

    fun budgets(): Map<String, Long> = readableDatabase.query(
        "budget", null, null, null, null, null, null
    ).use { c ->
        buildMap {
            while (c.moveToNext()) put(c.getString(0), c.getLong(1))
        }
    }

    fun insertTx(tx: Tx) {
        writableDatabase.insertOrThrow("tx", null, ContentValues().apply {
            put("id", tx.id)
            put("type", tx.type.name)
            put("amount", tx.amount)
            put("account_id", tx.accountId)
            put("to_account_id", tx.toAccountId)
            put("category_id", tx.categoryId)
            put("note", tx.note)
            put("occurred_at", tx.occurredAt)
        })
    }

    fun deleteTx(id: String) {
        writableDatabase.delete("tx", "id = ?", arrayOf(id))
    }

    fun putBudget(period: String, amount: Long) {
        writableDatabase.insertWithOnConflict(
            "budget", null, ContentValues().apply {
                put("period", period)
                put("amount", amount)
            }, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private data class SeedAccount(val name: String, val type: AccountType, val color: Long)

    private companion object {
        const val DB_VERSION = 2

        val CategoryPaletteColors = listOf(
            0xFFF2583E, 0xFF3E8BF2, 0xFF17A467, 0xFFF2A93E,
            0xFF8B6CF2, 0xFFE05C9A, 0xFF21B8C7, 0xFF7C8794,
        )
        val IncomePaletteColors = listOf(
            0xFF17A467, 0xFF3E8BF2, 0xFF8B6CF2, 0xFFF2A93E,
        )

        val nameToIcon = mapOf(
            "餐饮" to "food", "交通" to "transport", "购物" to "shopping", "娱乐" to "entertainment",
            "居住" to "housing", "医疗" to "medical", "学习" to "study", "其他" to "other",
            "工资" to "salary", "奖金" to "bonus", "理财" to "invest",
            "早餐" to "meal", "午餐" to "meal", "晚餐" to "meal", "外卖" to "meal", "咖啡" to "meal",
            "日用品" to "necessity", "服饰" to "clothes", "数码" to "digital", "美妆" to "beauty",
            "地铁" to "transport", "打车" to "car", "加油" to "fuel", "停车" to "car",
            "电影" to "movie", "游戏" to "entertainment", "旅行" to "flight",
            "房租" to "housing", "水电" to "utilities", "物业" to "housing",
            "门诊" to "medical", "药品" to "medical", "书籍" to "study", "课程" to "study",
        )
    }
}

private inline fun <T> Cursor.mapAll(block: (Cursor) -> T): List<T> = buildList {
    while (moveToNext()) add(block(this@mapAll))
}

private fun Cursor.toAccount() = Account(
    id = getString(0),
    name = getString(1),
    type = AccountType.valueOf(getString(2)),
    initialBalance = getLong(3),
    color = getLong(4),
    sortOrder = getInt(5),
    archived = getInt(6) != 0,
)

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
    accountId = getString(3),
    toAccountId = getString(4),
    categoryId = getString(5),
    note = getString(6),
    occurredAt = getLong(7),
)
