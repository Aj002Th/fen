package app.fen.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Fen 自己的无损备份格式（JSON），用于导出与恢复。
 *
 * 与 CSV 的分工：
 * - CSV 是给人看/改的，分类按名字走，导入时重新映射；
 * - JSON 保留 id、图标、颜色、精确时间，恢复后与导出前完全一致。
 */
data class FenBackup(
    val version: Int,
    val exportedAt: Long,
    val categories: List<Category>,
    val transactions: List<Tx>,
)

object FenBackupCodec {

    const val CURRENT_VERSION = 1

    fun encode(categories: List<Category>, transactions: List<Tx>, exportedAt: Long): String {
        val root = JSONObject()
        root.put("app", "fen")
        root.put("kind", "backup")
        root.put("version", CURRENT_VERSION)
        root.put("exportedAt", exportedAt)

        val categoryArray = JSONArray()
        categories.forEach { category ->
            categoryArray.put(JSONObject().apply {
                put("id", category.id)
                put("name", category.name)
                put("type", category.type.name)
                put("icon", category.icon)
                put("color", category.color)
                put("sortOrder", category.sortOrder)
            })
        }
        root.put("categories", categoryArray)

        val transactionArray = JSONArray()
        transactions.forEach { tx ->
            transactionArray.put(JSONObject().apply {
                put("id", tx.id)
                put("type", tx.type.name)
                put("amount", tx.amount)
                put("categoryId", tx.categoryId ?: JSONObject.NULL)
                put("note", tx.note)
                put("occurredAt", tx.occurredAt)
            })
        }
        root.put("transactions", transactionArray)
        return root.toString(2)
    }

    /** 不是 Fen 备份（或版本过高）时返回 null。 */
    fun decode(text: String): FenBackup? {
        val clean = text.removePrefix("\uFEFF").trim()
        if (clean.isEmpty() || !clean.startsWith("{")) return null
        val root = runCatching { JSONObject(clean) }.getOrNull() ?: return null
        if (root.optString("app") != "fen") return null
        if (root.optString("kind") != "backup") return null
        val version = root.optInt("version", 0)
        if (version <= 0 || version > CURRENT_VERSION) return null

        val categoryArray = root.optJSONArray("categories") ?: JSONArray()
        val categories = buildList {
            for (index in 0 until minOf(categoryArray.length(), 10_000)) {
                val item = categoryArray.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                add(
                    Category(
                        id = id,
                        name = name,
                        type = parseType(item.optString("type")) ?: TxType.EXPENSE,
                        icon = item.optString("icon").ifBlank { "other" },
                        color = item.optLong("color", 0L).takeIf { it != 0L } ?: 0xFF7C8794,
                        sortOrder = item.optInt("sortOrder", 0),
                    )
                )
            }
        }.distinctBy { it.id }

        val validCategoryIds = categories.map { it.id }.toHashSet()
        val transactionArray = root.optJSONArray("transactions") ?: JSONArray()
        val transactions = buildList {
            for (index in 0 until minOf(transactionArray.length(), 500_000)) {
                val item = transactionArray.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isEmpty()) continue
                val type = parseType(item.optString("type")) ?: continue
                val amount = kotlin.math.abs(item.optLong("amount", 0L))
                val occurredAt = item.optLong("occurredAt", 0L)
                if (occurredAt <= 0L) continue
                val categoryId = item.optString("categoryId").takeIf { it.isNotBlank() }
                    ?.takeIf { validCategoryIds.contains(it) }
                add(
                    Tx(
                        id = id,
                        type = type,
                        amount = amount,
                        categoryId = categoryId,
                        note = item.optString("note"),
                        occurredAt = occurredAt,
                    )
                )
            }
        }.distinctBy { it.id }

        return FenBackup(
            version = version,
            exportedAt = root.optLong("exportedAt", 0L),
            categories = categories,
            transactions = transactions,
        )
    }

    private fun parseType(raw: String): TxType? = when (raw.trim().uppercase()) {
        "EXPENSE", "支出" -> TxType.EXPENSE
        "INCOME", "收入" -> TxType.INCOME
        else -> null
    }
}
