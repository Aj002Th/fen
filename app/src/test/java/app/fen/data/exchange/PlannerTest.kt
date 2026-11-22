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
    private val all = listOf(food, transport, bonus)

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
