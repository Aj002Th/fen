package app.fen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuiltinCategoriesTest {

    @Test
    fun `default expense categories end with transfer`() {
        assertEquals(
            listOf("餐饮", "购物", "交通", "娱乐", "居住", "医疗", "学习", "转账"),
            BuiltinCategories.expense.map { it.name },
        )
        val transfer = BuiltinCategories.expense.last()
        assertEquals("saving", transfer.icon)
        assertEquals(0xFF5C6B8A, transfer.color)
        assertEquals(TxType.EXPENSE, transfer.type)
    }

    @Test
    fun `default income categories end with transfer and refund`() {
        assertEquals(
            listOf("工资", "奖金", "理财", "转账", "退款"),
            BuiltinCategories.income.map { it.name },
        )
        val transfer = BuiltinCategories.income.first { it.name == "转账" }
        val refund = BuiltinCategories.income.first { it.name == "退款" }
        assertEquals("saving", transfer.icon)
        assertEquals("other_income", refund.icon)
        assertEquals(0xFF5C6B8A, transfer.color)
        assertEquals(0xFFE07A3E, refund.color)
        assertEquals(TxType.INCOME, transfer.type)
        assertEquals(TxType.INCOME, refund.type)
    }

    @Test
    fun `v9 only adds the three new categories`() {
        assertEquals(
            listOf(
                "转账" to TxType.EXPENSE,
                "转账" to TxType.INCOME,
                "退款" to TxType.INCOME,
            ),
            BuiltinCategories.v9.map { it.name to it.type },
        )
    }

    @Test
    fun `upgrade inserts the three categories in front of uncategorized`() {
        val existing = buildList {
            listOf("餐饮", "购物", "交通", "娱乐", "居住", "医疗", "学习").forEachIndexed { index, name ->
                add(slot(name, TxType.EXPENSE, index))
            }
            listOf("工资", "奖金", "理财").forEachIndexed { index, name ->
                add(slot(name, TxType.INCOME, index + 7))
            }
            add(slot("未分类", TxType.EXPENSE, 10))
            add(slot("未分类", TxType.INCOME, 11))
        }

        val plan = planV9(existing)

        assertEquals(
            listOf(
                "转账" to TxType.EXPENSE to 10,
                "转账" to TxType.INCOME to 11,
                "退款" to TxType.INCOME to 12,
            ),
            plan.inserts.map { (category, order) -> category.name to category.type to order },
        )
        assertEquals(11, orderOf(plan, "未分类", TxType.EXPENSE))
        assertEquals(13, orderOf(plan, "未分类", TxType.INCOME))
        assertFalse(plan.sortUpdates.any { it.name == "餐饮" || it.name == "工资" })
    }

    @Test
    fun `upgrade keeps an existing transfer and does not move it`() {
        val existing = listOf(
            slot("转账", TxType.EXPENSE, 4),
            slot("工资", TxType.INCOME, 7),
            slot("未分类", TxType.EXPENSE, 10),
            slot("未分类", TxType.INCOME, 11),
        )

        val plan = planV9(existing)

        assertEquals(
            listOf(
                "转账" to TxType.INCOME to 11,
                "退款" to TxType.INCOME to 12,
            ),
            plan.inserts.map { (category, order) -> category.name to category.type to order },
        )
        assertFalse(plan.sortUpdates.any { it.type == TxType.EXPENSE })
        assertEquals(13, orderOf(plan, "未分类", TxType.INCOME))
    }

    @Test
    fun `upgrade appends income categories when that side has no uncategorized`() {
        val existing = listOf(
            slot("转账", TxType.EXPENSE, 4),
            slot("未分类", TxType.EXPENSE, 10),
        )

        val plan = planV9(existing)

        assertEquals(
            listOf(
                "转账" to TxType.INCOME to 11,
                "退款" to TxType.INCOME to 12,
            ),
            plan.inserts.map { (category, order) -> category.name to category.type to order },
        )
        assertEquals(emptyList<SortUpdate>(), plan.sortUpdates)
    }

    private fun slot(name: String, type: TxType, sortOrder: Int) =
        CategorySlot(name, type, sortOrder)

    private fun orderOf(plan: V9Plan, name: String, type: TxType): Int =
        plan.sortUpdates.single { it.name == name && it.type == type }.sortOrder
}
