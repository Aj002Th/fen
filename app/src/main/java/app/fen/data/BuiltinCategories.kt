package app.fen.data

import app.fen.data.exchange.UNCATEGORIZED_NAME

/** 一个默认分类。新库种子和旧库升级共用，避免两处各写一份名字和图标。 */
data class BuiltinCategory(
    val name: String,
    val type: TxType,
    val icon: String,
    val color: Long,
)

/** 升级排序时只需要的三列，不带 id、图标和颜色。 */
data class CategorySlot(
    val name: String,
    val type: TxType,
    val sortOrder: Int,
)

/** 升级时一行已有分类要改成的新顺序。 */
data class SortUpdate(
    val name: String,
    val type: TxType,
    val sortOrder: Int,
)

/** 一次升级要插入的分类，以及因此被往后挪的已有分类。 */
data class V9Plan(
    val inserts: List<Pair<BuiltinCategory, Int>>,
    val sortUpdates: List<SortUpdate>,
)

object BuiltinCategories {

    val expense: List<BuiltinCategory> = listOf(
        BuiltinCategory("餐饮", TxType.EXPENSE, "food", 0xFFF2583E),
        BuiltinCategory("购物", TxType.EXPENSE, "shopping", 0xFF3E8BF2),
        BuiltinCategory("交通", TxType.EXPENSE, "transport", 0xFF17A467),
        BuiltinCategory("娱乐", TxType.EXPENSE, "entertainment", 0xFFF2A93E),
        BuiltinCategory("居住", TxType.EXPENSE, "housing", 0xFF8B6CF2),
        BuiltinCategory("医疗", TxType.EXPENSE, "medical", 0xFFE05C9A),
        BuiltinCategory("学习", TxType.EXPENSE, "study", 0xFF21B8C7),
        BuiltinCategory("转账", TxType.EXPENSE, "saving", 0xFF5C6B8A),
    )

    val income: List<BuiltinCategory> = listOf(
        BuiltinCategory("工资", TxType.INCOME, "salary", 0xFF17A467),
        BuiltinCategory("奖金", TxType.INCOME, "bonus", 0xFF3E8BF2),
        BuiltinCategory("理财", TxType.INCOME, "invest", 0xFF8B6CF2),
        BuiltinCategory("转账", TxType.INCOME, "saving", 0xFF5C6B8A),
        BuiltinCategory("退款", TxType.INCOME, "other_income", 0xFFE07A3E),
    )

    /** v9 才补上的三个。前面的七个支出、三个收入老库里已经有。 */
    val v9: List<BuiltinCategory> = listOf(expense.last(), income[3], income[4])
}

/**
 * 算出 v9 要补哪些分类、插在哪。
 * 同名同方向已经有就跳过；该方向有「未分类」就插在它前面，没有就追加到末尾。
 */
fun planV9(existing: List<CategorySlot>): V9Plan {
    val orders = existing.associate { (it.type to it.name) to it.sortOrder }.toMutableMap()
    val original = orders.toMap()
    val inserts = mutableListOf<Pair<BuiltinCategory, Int>>()

    for (category in BuiltinCategories.v9) {
        val key = category.type to category.name
        if (orders.containsKey(key)) continue

        val uncategorized = orders[category.type to UNCATEGORIZED_NAME]
        val order = if (uncategorized != null) {
            orders.keys
                .filter { it.first == category.type && orders.getValue(it) >= uncategorized }
                .forEach { orders[it] = orders.getValue(it) + 1 }
            uncategorized
        } else {
            (orders.values.maxOrNull() ?: -1) + 1
        }
        orders[key] = order
        inserts.add(category to order)
    }

    val sortUpdates = original
        .filter { (key, order) -> orders.getValue(key) != order }
        .map { (key, _) -> SortUpdate(key.second, key.first, orders.getValue(key)) }
    return V9Plan(inserts, sortUpdates)
}
