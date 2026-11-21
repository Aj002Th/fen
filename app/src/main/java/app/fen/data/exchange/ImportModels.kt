package app.fen.data.exchange

import app.fen.data.Category
import app.fen.data.FenBackup
import app.fen.data.Tx
import app.fen.data.TxType

/** 表格形状：统一按字符串读取，空单元格为空串。 */
data class Table(val rows: List<List<String>>)

/** 一行被跳过的原始数据，用于导入预览里解释原因。 */
data class SkippedRow(val line: Int, val reason: String, val raw: String)

/** 解析完成后、尚未决定分类/转账策略的一条记录。 */
data class ParsedRecord(
    val occurredAt: Long,
    val type: TxType,
    val amount: Long,
    val sourceCategory: String,
    val note: String,
    val isTransfer: Boolean = false,
    val suspectDuplicate: Boolean = false,
)

/** 来源分类的聚合，用于「分类映射」列表。 */
data class SourceCategory(
    val name: String,
    val type: TxType,
    val count: Int,
    val amount: Long,
)

/** 表格类文件（iCost / 通用 CSV）的解析结果。 */
data class SheetResult(
    val records: List<ParsedRecord>,
    val skipped: List<SkippedRow>,
    val warnings: List<String> = emptyList(),
    val source: String = "table",
)

/** 分类映射方式。 */
enum class MappingKind { EXISTING, NEW, UNCATEGORIZED, AUTO }

/**
 * 一个来源分类要落到哪里：
 * - EXISTING：合并进已有分类（categoryId）
 * - NEW：新建分类（newName，默认用来源名）
 * - UNCATEGORIZED：不分类
 * - AUTO：按备注关键词自动归类，匹配不到则不分类
 */
data class MappingTarget(
    val kind: MappingKind,
    val categoryId: String? = null,
    val newName: String = "",
)

data class CategoryMapping(
    val source: String,
    val type: TxType,
    val count: Int,
    val amount: Long,
    val target: MappingTarget,
)

/** 转账行（iCost 模板里 type=转账）如何落成支出/收入。 */
enum class TransferPolicy { SIGN, SKIP, EXPENSE, INCOME }

/** 导入计划：真正写库前算好的分类与流水。 */
data class ImportPlan(
    val newCategories: List<Category>,
    val transactions: List<Tx>,
    val skippedTransfers: Int,
    val uncategorized: Int,
    val skippedDuplicates: Int,
)

data class ImportOutcome(
    val batchId: String?,
    val imported: Int,
    val newCategories: Int,
    val skipped: Int,
    val skippedDuplicates: Int,
)

/** 来源分类为空时统一显示为「未分类」，保证聚合与映射使用同一个 key。 */
fun sourceLabel(name: String): String = name.trim().ifEmpty { "未分类" }

/** 一次导入会话，UI 直接渲染它。 */
data class ImportSession(
    val fileName: String,
    val source: String,
    val backup: FenBackup?,
    val records: List<ParsedRecord>,
    val skipped: List<SkippedRow>,
    val warnings: List<String>,
    val mappings: List<CategoryMapping>,
) {
    val isBackup: Boolean get() = backup != null
    val expenseRecords: List<ParsedRecord> get() = records.filter { it.type == TxType.EXPENSE }
    val incomeRecords: List<ParsedRecord> get() = records.filter { it.type == TxType.INCOME }
    val transferCount: Int get() = records.count { it.isTransfer }
    val duplicateCount: Int get() = records.count { it.suspectDuplicate }
    val dateRange: Pair<Long, Long>? get() =
        if (records.isEmpty()) null else records.minOf { it.occurredAt } to records.maxOf { it.occurredAt }
    val total: Pair<Long, Long> get() =
        expenseRecords.sumOf { it.amount } to incomeRecords.sumOf { it.amount }

    fun mappingFor(source: String, type: TxType): CategoryMapping? =
        mappings.firstOrNull { it.source == sourceLabel(source) && it.type == type }

    /** 某个来源分类用于 AUTO 时命中的记录。 */
    fun recordsFor(source: String, type: TxType): List<ParsedRecord> =
        records.filter { sourceLabel(it.sourceCategory) == source && it.type == type }
}

/** 把一组记录按 (来源分类, 类型) 聚合。 */
fun aggregateSourceCategories(records: List<ParsedRecord>): List<SourceCategory> =
    records.groupBy { sourceLabel(it.sourceCategory) to it.type }
        .map { (key, items) ->
            SourceCategory(
                name = key.first,
                type = key.second,
                count = items.size,
                amount = items.sumOf { it.amount },
            )
        }
        .sortedWith(compareBy<SourceCategory>({ it.type }, { -it.count }))
