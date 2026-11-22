package app.fen.data.exchange

import app.fen.data.Category
import app.fen.data.FenBackupCodec
import app.fen.data.ImportBatch
import app.fen.data.Tx
import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {

    private val food = Category("c-food", "餐饮", TxType.EXPENSE, "food", 0xFFF2583E, 0)
    private val transport = Category("c-trans", "交通", TxType.EXPENSE, "transport", 0xFF3E8BF2, 1)
    private val bonus = Category("c-bonus", "奖金", TxType.INCOME, "bonus", 0xFF17A467, 2)
    private val expenseTransfer = Category("c-out", "转账", TxType.EXPENSE, "saving", 0xFF5C6B8A, 3)
    private val incomeTransfer = Category("c-in", "转账", TxType.INCOME, "saving", 0xFF5C6B8A, 4)
    private val refund = Category("c-refund", "退款", TxType.INCOME, "other_income", 0xFFE07A3E, 5)
    private val all = listOf(food, transport, bonus)
    private val withNew = all + listOf(expenseTransfer, incomeTransfer, refund)

    private val transferSources = listOf(
        "转账", "群收款", "微信红包", "微信红包（群红包）", "转账红包", "二维码收款",
    )

    private fun record(
        category: String,
        type: TxType = TxType.EXPENSE,
        amount: Long,
        note: String = "",
        transfer: Boolean = false,
    ) = ParsedRecord(
        occurredAt = 1_700_000_000_000,
        type = type,
        amount = amount,
        sourceCategory = category,
        note = note,
        isTransfer = transfer,
    )

    @Test
    fun promoteUncategorizedCreatesSameNameOnlyWhenPossible() {
        val mappings = listOf(
            CategoryMapping("宠物", TxType.EXPENSE, 2, 100, MappingTarget(MappingKind.UNCATEGORIZED)),
            CategoryMapping("餐饮美食", TxType.EXPENSE, 1, 50, MappingTarget(MappingKind.EXISTING, food.id)),
            CategoryMapping("转账", TxType.EXPENSE, 1, 50, MappingTarget(MappingKind.NEW, newName = "转账")),
            CategoryMapping("未分类", TxType.EXPENSE, 1, 10, MappingTarget(MappingKind.UNCATEGORIZED)),
            CategoryMapping("未归类", TxType.EXPENSE, 1, 10, MappingTarget(MappingKind.UNCATEGORIZED)),
            CategoryMapping("餐饮", TxType.EXPENSE, 1, 10, MappingTarget(MappingKind.UNCATEGORIZED)),
            CategoryMapping("宠物", TxType.INCOME, 1, 20, MappingTarget(MappingKind.UNCATEGORIZED)),
            CategoryMapping("   ", TxType.EXPENSE, 1, 1, MappingTarget(MappingKind.UNCATEGORIZED)),
        )

        val result = ImportPlanner.promoteUncategorizedToNew(mappings, all)

        assertEquals(MappingKind.NEW, result[0].target.kind)
        assertEquals("宠物", result[0].target.newName)
        assertEquals(MappingKind.EXISTING, result[1].target.kind)
        assertEquals(food.id, result[1].target.categoryId)
        assertEquals(MappingKind.NEW, result[2].target.kind)
        assertEquals("转账", result[2].target.newName)
        assertEquals(MappingKind.UNCATEGORIZED, result[3].target.kind)
        assertEquals(MappingKind.UNCATEGORIZED, result[4].target.kind)
        assertEquals(MappingKind.UNCATEGORIZED, result[5].target.kind)
        assertEquals(MappingKind.NEW, result[6].target.kind)
        assertEquals("宠物", result[6].target.newName)
        assertEquals(TxType.INCOME, result[6].type)
        assertEquals(MappingKind.UNCATEGORIZED, result[7].target.kind)
    }

    @Test
    fun suggestions() {
        assertEquals(MappingKind.EXISTING, CategorySuggest.suggest("餐饮美食", TxType.EXPENSE, all).kind)
        assertEquals(MappingKind.UNCATEGORIZED, CategorySuggest.suggest("商户消费", TxType.EXPENSE, all).kind)
        assertEquals(MappingKind.UNCATEGORIZED, CategorySuggest.suggest("AA巴士-退款", TxType.INCOME, all).kind)
        assertEquals(MappingKind.UNCATEGORIZED, CategorySuggest.suggest("不存在", TxType.EXPENSE, all).kind)

        val withUncategorized = all + Category("c-uncat", "未分类", TxType.EXPENSE, "other", 0xFF7C8794, 9)
        assertEquals(
            MappingKind.EXISTING,
            CategorySuggest.suggest("未分类", TxType.EXPENSE, withUncategorized).kind,
        )
    }

    @Test
    fun transferSourcesMapToTransferOfTheSameDirection() {
        for (source in transferSources) {
            val expense = CategorySuggest.suggest(source, TxType.EXPENSE, withNew)
            assertEquals(source, MappingKind.EXISTING, expense.kind)
            assertEquals(source, expenseTransfer.id, expense.categoryId)

            val income = CategorySuggest.suggest(source, TxType.INCOME, withNew)
            assertEquals(source, MappingKind.EXISTING, income.kind)
            assertEquals(source, incomeTransfer.id, income.categoryId)
        }
    }

    @Test
    fun refundsMapToIncomeRefundButPlainRedPacketsStayBonus() {
        val refunded = CategorySuggest.suggest("退款", TxType.INCOME, withNew)
        assertEquals(MappingKind.EXISTING, refunded.kind)
        assertEquals(refund.id, refunded.categoryId)

        val merchant = CategorySuggest.suggest("AA巴士-退款", TxType.INCOME, withNew)
        assertEquals(MappingKind.EXISTING, merchant.kind)
        assertEquals(refund.id, merchant.categoryId)

        val redPacket = CategorySuggest.suggest("红包", TxType.INCOME, withNew)
        assertEquals(MappingKind.EXISTING, redPacket.kind)
        assertEquals(bonus.id, redPacket.categoryId)
    }

    @Test
    fun transferAndRefundStayUncategorizedWhenThoseCategoriesWereDeleted() {
        assertEquals(MappingKind.UNCATEGORIZED, CategorySuggest.suggest("微信红包", TxType.INCOME, all).kind)
        assertEquals(MappingKind.UNCATEGORIZED, CategorySuggest.suggest("群收款", TxType.INCOME, all).kind)
        assertEquals(MappingKind.UNCATEGORIZED, CategorySuggest.suggest("AA巴士-退款", TxType.INCOME, all).kind)

        val redPacket = CategorySuggest.suggest("红包", TxType.INCOME, all)
        assertEquals(MappingKind.EXISTING, redPacket.kind)
        assertEquals(bonus.id, redPacket.categoryId)
    }

    @Test
    fun expenseRefundsAndCreditAreNotPulledIntoTheNewCategories() {
        assertEquals(MappingKind.UNCATEGORIZED, CategorySuggest.suggest("AA巴士-退款", TxType.EXPENSE, withNew).kind)
        assertEquals(MappingKind.UNCATEGORIZED, CategorySuggest.suggest("信用借还", TxType.EXPENSE, withNew).kind)
    }

    @Test
    fun qrPaymentsFallBackToTransferButMerchantSpendingStaysUnbucketed() {
        val records = listOf(
            record("扫二维码付款", amount = 1000, note = "美团"),
            record("扫二维码付款", amount = 2000, note = "未知商户"),
            record("商户消费", amount = 3000, note = "未知商户"),
            record("群收款", type = TxType.EXPENSE, amount = 4000, note = "地铁"),
        )
        val bucketed = CategorySuggest.bucketize(records, withNew)

        assertEquals("餐饮", bucketed[0].sourceCategory)
        assertEquals("扫二维码付款", bucketed[0].rawCategory)
        assertEquals("转账", bucketed[1].sourceCategory)
        assertEquals("未归类", bucketed[2].sourceCategory)
        assertEquals("群收款", bucketed[3].sourceCategory)
        assertEquals("", bucketed[3].rawCategory)
    }

    @Test
    fun transferAndRefundGetTheirIcons() {
        assertEquals("saving", CategorySuggest.iconFor("转账", TxType.EXPENSE))
        assertEquals("saving", CategorySuggest.iconFor("转账", TxType.INCOME))
        assertEquals("other_income", CategorySuggest.iconFor("退款", TxType.INCOME))
    }

    @Test
    fun genericSourcesAreBucketedByNote() {
        val records = listOf(
            record("商户消费", amount = 1000, note = "地铁"),
            record("商户消费", amount = 2000, note = "未知商户"),
            record("餐饮美食", amount = 3000, note = "午饭"),
        )
        val bucketed = CategorySuggest.bucketize(records, all)
        assertEquals("交通", bucketed[0].sourceCategory)
        assertEquals("商户消费", bucketed[0].rawCategory)
        assertEquals("未归类", bucketed[1].sourceCategory)
        assertEquals("商户消费", bucketed[1].rawCategory)
        // 非通用来源保持原样
        assertEquals("餐饮美食", bucketed[2].sourceCategory)
        assertEquals("", bucketed[2].rawCategory)
    }

    @Test
    fun autoByNote() {
        assertEquals(transport.id, CategorySuggest.keywordCategory("深圳通乘车码乘地铁消费", TxType.EXPENSE, all)?.id)
        assertEquals(food.id, CategorySuggest.keywordCategory("美团收银9097", TxType.EXPENSE, all)?.id)
        assertNull(CategorySuggest.keywordCategory("未知商户", TxType.EXPENSE, all))
    }

    @Test
    fun mappingKindsAndTransferPolicy() {
        val records = listOf(
            record("餐饮美食", amount = 1000),
            record("交通", amount = 2000),
            record("未分类", amount = 3000),
            record("转账", amount = 5000, note = "微信转账", transfer = true),
        )
        val session = ImportSession(
            fileName = "x.xlsx",
            source = "test",
            backup = null,
            records = records,
            skipped = emptyList(),
            warnings = emptyList(),
            mappings = listOf(
                CategoryMapping("餐饮美食", TxType.EXPENSE, 1, 1000, MappingTarget(MappingKind.EXISTING, food.id)),
                CategoryMapping("交通", TxType.EXPENSE, 1, 2000, MappingTarget(MappingKind.EXISTING, transport.id)),
                CategoryMapping("未分类", TxType.EXPENSE, 1, 3000, MappingTarget(MappingKind.UNCATEGORIZED)),
                CategoryMapping("转账", TxType.EXPENSE, 1, 5000, MappingTarget(MappingKind.NEW, newName = "转账")),
            ),
        )

        var counter = 0
        val plan = ImportPlanner.build(
            session = session,
            existingCategories = all,
            skipDuplicates = false,
            transferPolicy = TransferPolicy.EXPENSE,
            batchId = "batch",
            startSortOrder = 3,
            uncategorizedId = { "uncat-${it.name}" },
            newId = { "new-${counter++}" },
        )
        assertEquals(4, plan.transactions.size)
        assertEquals(food.id, plan.transactions[0].categoryId)
        assertEquals(transport.id, plan.transactions[1].categoryId)
        assertEquals("uncat-EXPENSE", plan.transactions[2].categoryId)
        assertEquals(1, plan.uncategorized)
        assertTrue(plan.transactions.all { it.importBatch == "batch" })
        assertTrue(plan.transactions.all { it.categoryId != null })
        assertEquals(1, plan.newCategories.size)
        assertEquals(TxType.EXPENSE, plan.transactions[3].type)
    }

    @Test
    fun transferCanBeSkippedOrFlipped() {
        val session = ImportSession(
            fileName = "x.xlsx",
            source = "test",
            backup = null,
            records = listOf(record("转账", amount = 5000, transfer = true)),
            skipped = emptyList(),
            warnings = emptyList(),
            mappings = listOf(
                CategoryMapping("转账", TxType.EXPENSE, 1, 5000, MappingTarget(MappingKind.UNCATEGORIZED)),
            ),
        )
        val skipped = ImportPlanner.build(
            session, all, false, TransferPolicy.SKIP, "b", 0,
            uncategorizedId = { "u-${it.name}" },
        )
        assertEquals(0, skipped.transactions.size)
        assertEquals(1, skipped.skippedTransfers)

        val income = ImportPlanner.build(
            session, all, false, TransferPolicy.INCOME, "b", 0,
            uncategorizedId = { "u-${it.name}" },
        )
        assertEquals(TxType.INCOME, income.transactions.single().type)
        assertEquals("u-INCOME", income.transactions.single().categoryId)
    }

    @Test
    fun skipDuplicates() {
        val records = listOf(
            record("餐饮美食", amount = 1000).copy(suspectDuplicate = false),
            record("餐饮美食", amount = 1000).copy(suspectDuplicate = true),
        )
        val session = ImportSession(
            fileName = "x",
            source = "t",
            backup = null,
            records = records,
            skipped = emptyList(),
            warnings = emptyList(),
            mappings = listOf(
                CategoryMapping("餐饮美食", TxType.EXPENSE, 2, 2000, MappingTarget(MappingKind.EXISTING, food.id)),
            ),
        )
        val plan = ImportPlanner.build(
            session, all, skipDuplicates = true, transferPolicy = TransferPolicy.SIGN,
            batchId = "b", startSortOrder = 0, uncategorizedId = { "u-${it.name}" },
        )
        assertEquals(1, plan.transactions.size)
        assertEquals(1, plan.skippedDuplicates)
    }

    @Test
    fun csvRoundTrip() {
        val categories = listOf(food)
        val txs = listOf(
            Tx("t1", TxType.EXPENSE, 2100, food.id, "午饭, 咖啡", 1_700_000_000_000),
            Tx("t2", TxType.INCOME, 500, null, "红包", 1_700_000_100_000),
        )
        val text = FenCsv.encode(categories, txs)
        val table = Csv.parse(text)
        assertEquals(FenCsv.header, table.rows[0])

        val result = SheetImport.parse(table, source = "Fen CSV")
        assertEquals(2, result.records.size)
        assertEquals(2100L, result.records[0].amount)
        assertEquals("餐饮", result.records[0].sourceCategory)
        assertEquals("午饭, 咖啡", result.records[0].note)
        assertEquals(TxType.INCOME, result.records[1].type)
    }

    @Test
    fun editingKeepsImportBatch() {
        val tx = Tx("t", TxType.EXPENSE, 100, food.id, "午饭", 1_700_000_000_000, importBatch = "batch-1")
        val edited = tx.copy(amount = 250, categoryId = transport.id, note = "改过", occurredAt = 1_700_000_100_000)
        assertEquals("batch-1", edited.importBatch)
        assertEquals("batch-1", tx.copy().importBatch)

        val batch = ImportBatch("batch-1", "iCost Excel", "a.xlsx", 1_700_000_000_000, rows = 3588)
        assertEquals(3588, batch.remaining)
        assertEquals(3500, batch.copy(remaining = 3500).remaining)
    }

    @Test
    fun backupRoundTrip() {
        val categories = listOf(food, bonus)
        val txs = listOf(
            Tx("t1", TxType.EXPENSE, 2100, food.id, "午饭", 1_700_000_000_000),
            Tx("t2", TxType.INCOME, 500, null, "红包", 1_700_000_100_000, importBatch = "b1"),
        )
        val json = FenBackupCodec.encode(categories, txs, 1_700_000_200_000)
        val decoded = FenBackupCodec.decode(json)
        assertNotNull(decoded)
        assertEquals(categories, decoded!!.categories)
        assertEquals(2, decoded.transactions.size)
        assertEquals(food.id, decoded.transactions[0].categoryId)
        assertEquals("午饭", decoded.transactions[0].note)

        assertNull(FenBackupCodec.decode("{\"app\":\"other\"}"))
        assertNull(FenBackupCodec.decode(json.replace("\"version\": 1", "\"version\": 99")))
        assertNull(FenBackupCodec.decode("not json"))
    }
}
