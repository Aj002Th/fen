package app.fen.data.exchange

import app.fen.data.Category
import app.fen.data.Tx
import app.fen.data.TxType
import java.util.UUID

/**
 * 把「解析结果 + 用户映射」算成一份可写库的计划。
 * 纯函数，不碰数据库，便于单元测试。
 */
object ImportPlanner {

    fun buildMappings(records: List<ParsedRecord>, categories: List<Category>): List<CategoryMapping> =
        aggregateSourceCategories(records).map { source ->
            CategoryMapping(
                source = source.name,
                type = source.type,
                count = source.count,
                amount = source.amount,
                target = CategorySuggest.suggest(source.name, source.type, categories),
                origins = source.origins,
            )
        }

    /**
     * 来源名能不能当成一个新分类。
     * 和映射弹窗里的「新建『xxx』分类」同一套条件：空名、「未归类」、「未分类」，
     * 以及同类型下已经有这个名字的，都不能建。
     */
    fun canCreateSameName(name: String, type: TxType, categories: List<Category>): Boolean {
        val sourceName = name.trim()
        return sourceName.isNotEmpty() &&
            sourceName != UNBUCKETED_NAME &&
            sourceName != UNCATEGORIZED_NAME &&
            categories.none { it.type == type && it.name == sourceName }
    }

    /**
     * 把仍是「未分类」、且来源名可以建分类的映射，改成新建同名分类。
     * 已经归到现有分类、或已经是新建的，保持不动。只改映射，不写库。
     */
    fun promoteUncategorizedToNew(
        mappings: List<CategoryMapping>,
        categories: List<Category>,
    ): List<CategoryMapping> = mappings.map { mapping ->
        val name = mapping.source.trim()
        if (
            mapping.target.kind != MappingKind.UNCATEGORIZED ||
            !canCreateSameName(name, mapping.type, categories)
        ) {
            mapping
        } else {
            mapping.copy(target = MappingTarget(MappingKind.NEW, newName = name))
        }
    }

    fun build(
        session: ImportSession,
        existingCategories: List<Category>,
        skipDuplicates: Boolean,
        transferPolicy: TransferPolicy,
        batchId: String?,
        startSortOrder: Int,
        uncategorizedId: (app.fen.data.TxType) -> String,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): ImportPlan {
        val newCategories = mutableListOf<Category>()
        val byName = existingCategories.associateBy { it.type to it.name }.toMutableMap()
        val knownIds = existingCategories.map { it.id }.toMutableSet()
        var nextOrder = startSortOrder

        fun ensureCategory(name: String, type: TxType): String? {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return null
            byName[type to trimmed]?.let { return it.id }
            val category = Category(
                id = newId(),
                name = trimmed,
                type = type,
                icon = CategorySuggest.iconFor(trimmed, type),
                color = CategorySuggest.colorFor(trimmed),
                sortOrder = nextOrder++,
            )
            newCategories.add(category)
            byName[type to trimmed] = category
            knownIds.add(category.id)
            return category.id
        }

        var skippedDuplicates = 0
        var skippedTransfers = 0
        var uncategorized = 0
        val transactions = ArrayList<Tx>(session.records.size)

        for (record in session.records) {
            if (skipDuplicates && record.suspectDuplicate) {
                skippedDuplicates++
                continue
            }

            var type = record.type
            if (record.isTransfer) {
                when (transferPolicy) {
                    TransferPolicy.SKIP -> {
                        skippedTransfers++
                        continue
                    }
                    TransferPolicy.EXPENSE -> type = TxType.EXPENSE
                    TransferPolicy.INCOME -> type = TxType.INCOME
                    TransferPolicy.SIGN -> Unit
                }
            }

            val mapping = session.mappingFor(record.sourceCategory, record.type)
                ?: session.mappingFor(record.sourceCategory, type)
            val resolved: String? = when (mapping?.target?.kind) {
                MappingKind.EXISTING ->
                    mapping.target.categoryId?.takeIf { knownIds.contains(it) }
                MappingKind.NEW ->
                    ensureCategory(mapping.target.newName.ifBlank { sourceLabel(record.sourceCategory) }, type)
                MappingKind.UNCATEGORIZED, null -> null
            }
            val categoryId = resolved ?: uncategorizedId(type)
            if (resolved == null) uncategorized++

            transactions.add(
                Tx(
                    id = newId(),
                    type = type,
                    amount = record.amount,
                    categoryId = categoryId,
                    note = record.note,
                    occurredAt = record.occurredAt,
                    importBatch = batchId,
                )
            )
        }

        return ImportPlan(
            newCategories = newCategories,
            transactions = transactions,
            skippedTransfers = skippedTransfers,
            uncategorized = uncategorized,
            skippedDuplicates = skippedDuplicates,
        )
    }
}
