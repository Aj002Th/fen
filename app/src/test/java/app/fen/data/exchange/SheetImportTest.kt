package app.fen.data.exchange

import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SheetImportTest {

    @Test
    fun icostExportLayout() {
        val table = Table(
            listOf(
                listOf("日期", "类型", "金额", "一级分类", "二级分类", "账户1", "账户2", "备注", "货币", "标签", "账本"),
                listOf("2024/01/01 00:06", "收入", "4.19", "微信红包", "", "微信", "", "", "CNY", "", "zzj"),
                listOf("2024/01/01 13:06", "支出", "-21", "商户消费", "", "微信", "", "美团收银", "CNY", "", "zzj"),
                listOf("2024/07/11 20:35", "支出", "0", "交通出行", "", "", "", "哈啰单车卡抵扣骑行费用", "CNY", "", "zzj"),
                listOf("2024/07/12 10:00", "支出", "-12.5", "餐饮", "三餐", "", "", "", "", "CNY", "", "zzj"),
            )
        )
        val result = SheetImport.parse(table)
        assertEquals(4, result.records.size)
        assertEquals(0, result.skipped.size)

        val income = result.records[0]
        assertEquals(TxType.INCOME, income.type)
        assertEquals(419L, income.amount)
        assertEquals("微信红包", income.sourceCategory)

        val expense = result.records[1]
        assertEquals(TxType.EXPENSE, expense.type)
        assertEquals(2100L, expense.amount)
        assertEquals("美团收银", expense.note)

        // 0 元保留
        assertEquals(0L, result.records[2].amount)
        assertFalse(result.records[2].isTransfer)

        // 二级分类不参与映射
        assertEquals("餐饮", result.records[3].sourceCategory)
    }

    @Test
    fun icostTemplateWithTransfers() {
        val table = Table(
            listOf(
                listOf("日期", "类型", "金额", "一级分类", "二级分类", "账户1", "账户2", "备注", "货币", "标签"),
                listOf("2011年01月11日 12:00:00", "转账", "1000", "其他", "", "工资卡", "储蓄卡", "工资转入", "CNY", "#标签1"),
                listOf("2011年01月12日 12:00:00", "转账", "-200", "其他", "", "储蓄卡", "工资卡", "转出", "CNY", ""),
                listOf("2011年01月13日 12:00:00", "支出", "40", "餐饮", "三餐", "支付宝", "", "早餐", "USD", ""),
            )
        )
        val result = SheetImport.parse(table)
        assertEquals(2, result.records.size)
        assertTrue(result.records[0].isTransfer)
        assertEquals(TxType.INCOME, result.records[0].type)
        assertTrue(result.records[1].isTransfer)
        assertEquals(TxType.EXPENSE, result.records[1].type)
        // 非 CNY 被跳过并给出提示
        assertEquals(1, result.skipped.size)
        assertTrue(result.warnings.any { it.contains("CNY") })
    }

    @Test
    fun wechatRawCsvWithIncomeExpenseColumn() {
        val table = Table(
            listOf(
                listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式"),
                listOf("2024-01-01 10:00:00", "商户消费", "某商户", "咖啡", "支出", "18.00", "零钱"),
                listOf("2024-01-02 10:00:00", "转账", "某人", "转账", "收入", "30.00", "零钱"),
            )
        )
        val result = SheetImport.parse(table)
        assertEquals(2, result.records.size)
        assertEquals(TxType.EXPENSE, result.records[0].type)
        assertEquals(1800L, result.records[0].amount)
        assertEquals("咖啡", result.records[0].note)
        assertEquals(TxType.INCOME, result.records[1].type)
    }

    @Test
    fun unknownLayoutProducesWarning() {
        val result = SheetImport.parse(Table(listOf(listOf("a", "b"), listOf("1", "2"))))
        assertTrue(result.records.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun duplicatesAreMarkedButKept() {
        val table = Table(
            listOf(
                listOf("日期", "类型", "金额", "一级分类", "备注"),
                listOf("2024/01/03 22:08", "收入", "0.03", "其他", ""),
                listOf("2024/01/03 22:08", "收入", "0.03", "其他", ""),
            )
        )
        val result = SheetImport.parse(table)
        assertEquals(2, result.records.size)
        assertEquals(1, result.records.count { it.suspectDuplicate })
    }

    /**
     * 真实 iCost 导出回归测试。样本不提交到仓库，用环境变量指定：
     *   $env:ICOST_SAMPLE="C:\path\to\iCost.xlsx"
     */
    @Test
    fun realIcostExportIfPresent() {
        val path = System.getenv("ICOST_SAMPLE") ?: return
        val file = File(path)
        if (!file.exists()) return

        val parsed = FileDecode.parse(file.name, file.readBytes())
        assertTrue(parsed is ParsedFile.Sheet)
        val result = (parsed as ParsedFile.Sheet).result

        assertEquals(3588, result.records.size)
        assertEquals(0, result.skipped.size)
        assertEquals(3294, result.records.count { it.type == TxType.EXPENSE })
        assertEquals(294, result.records.count { it.type == TxType.INCOME })
        assertEquals(49, result.records.count { it.amount == 0L })
        // 这份导出里「转账」是分类而不是类型，方向已由支出/收入列给出，不需要再套转账策略
        assertEquals(0, result.records.count { it.isTransfer })
        assertEquals(400, result.records.count { it.sourceCategory == "转账" })
        assertEquals(13, result.records.count { it.suspectDuplicate })
        assertEquals(16_095_092L, result.records.filter { it.type == TxType.EXPENSE }.sumOf { it.amount })
        assertEquals(5_130_006L, result.records.filter { it.type == TxType.INCOME }.sumOf { it.amount })

        // 微信交易类型与支付宝/手工分类在账户维度上完全分开
        assertEquals(
            2731,
            result.records.count {
                it.sourceCategory == "商户消费" || it.sourceCategory == "转账" ||
                    it.sourceCategory == "群收款" || it.sourceCategory == "扫二维码付款"
            },
        )
        val categories = aggregateSourceCategories(result.records)
        assertTrue(categories.any { it.name == "商户消费" && it.count == 1709 })
        assertTrue(categories.any { it.name == "餐饮美食" && it.count == 191 })
    }
}
