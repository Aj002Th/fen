package app.fen.data.exchange

import app.fen.data.TxType

/**
 * 把 iCost 导出/模板、以及通用账单 CSV 的表格读成 [ParsedRecord]。
 *
 * 按「表头名」定位列，不依赖列顺序；兼容：
 * - iCost 导出（20 列，日期 `2024/01/01 00:06`，支出为负）
 * - iCost 导入模板（10 列，日期 `2011年01月11日 12:00:00`，金额为正）
 * - Fen 自己的 CSV、以及常见的微信/支付宝原始账单表头
 */
object SheetImport {

    private enum class Field { DATE, TYPE, AMOUNT, CATEGORY, SUBCATEGORY, NOTE, ACCOUNT, CURRENCY }

    private enum class RawType { EXPENSE, INCOME, TRANSFER, UNKNOWN }

    private data class FoundHeader(val row: Int, val fields: Map<Field, Int>)

    fun parse(table: Table, source: String = "table"): SheetResult {
        val warnings = mutableListOf<String>()
        val header = findHeader(table)
            ?: return SheetResult(
                records = emptyList(),
                skipped = emptyList(),
                warnings = listOf("没有找到「日期 / 金额」表头，无法识别这个文件的格式。"),
                source = source,
            )

        val records = mutableListOf<ParsedRecord>()
        val skipped = mutableListOf<SkippedRow>()
        var nonCny = 0

        for (index in (header.row + 1) until table.rows.size) {
            val row = table.rows[index]
            if (row.all { it.isBlank() }) continue
            val line = index + 1

            val dateText = header.text(row, Field.DATE)
            val amountText = header.text(row, Field.AMOUNT)
            val typeText = header.text(row, Field.TYPE)
            val category = header.text(row, Field.CATEGORY).trim()
            val note = header.text(row, Field.NOTE).trim()
            val currency = header.text(row, Field.CURRENCY).trim()
            val rawType = parseType(typeText)

            if (dateText.isBlank() && amountText.isBlank() && typeText.isBlank()) {
                skipped.add(SkippedRow(line, "空行或表尾说明", row.joinToString(" | ").take(120)))
                continue
            }

            val occurredAt = parseDateTimeToMillis(dateText)
            if (occurredAt == null) {
                skipped.add(SkippedRow(line, "日期无法识别：$dateText", row.joinToString(" | ").take(120)))
                continue
            }
            val money = parseMoneyToMinor(amountText)
            if (money == null) {
                skipped.add(SkippedRow(line, "金额无法识别：$amountText", row.joinToString(" | ").take(120)))
                continue
            }
            if (currency.isNotBlank() && currency.uppercase() !in setOf("CNY", "RMB", "CNH", "人民币", "￥", "¥")) {
                nonCny++
                skipped.add(SkippedRow(line, "暂不支持币种 $currency", row.joinToString(" | ").take(120)))
                continue
            }

            val isTransfer = rawType == RawType.TRANSFER
            val type = when {
                rawType == RawType.EXPENSE -> TxType.EXPENSE
                rawType == RawType.INCOME -> TxType.INCOME
                // 转账 / 缺失类型：按正负号判断方向（负数=转出，正数=转入）
                money < 0 -> TxType.EXPENSE
                money > 0 -> TxType.INCOME
                else -> TxType.EXPENSE
            }

            records.add(
                ParsedRecord(
                    occurredAt = occurredAt,
                    type = type,
                    amount = kotlin.math.abs(money),
                    sourceCategory = category.ifBlank { "" },
                    note = note,
                    isTransfer = isTransfer,
                )
            )
        }

        if (nonCny > 0) warnings.add("有 $nonCny 行非 CNY 记录已跳过（暂不支持多币种）。")
        if (header.fields[Field.TYPE] == null) warnings.add("文件没有「类型」列，已按金额正负判断收支。")
        return SheetResult(
            records = markDuplicates(records),
            skipped = skipped,
            warnings = warnings,
            source = source,
        )
    }

    /** 同一文件内完全相同的记录（同时间/类型/金额/备注）标记为疑似重复，不自动删除。 */
    fun markDuplicates(records: List<ParsedRecord>): List<ParsedRecord> {
        val seen = HashSet<String>()
        return records.map { record ->
            val key = listOf(record.occurredAt, record.type, record.amount, record.note.trim()).joinToString("|")
            if (!seen.add(key)) record.copy(suspectDuplicate = true) else record
        }
    }

    private fun findHeader(table: Table): FoundHeader? {
        val limit = minOf(table.rows.size, 12)
        for (index in 0 until limit) {
            val row = table.rows[index]
            val fields = LinkedHashMap<Field, Int>()
            row.forEachIndexed { column, raw ->
                val field = fieldOf(raw) ?: return@forEachIndexed
                if (!fields.containsKey(field)) fields[field] = column
            }
            val hasDate = fields.containsKey(Field.DATE)
            val hasAmount = fields.containsKey(Field.AMOUNT)
            val hasCategory = fields.containsKey(Field.CATEGORY) || fields.containsKey(Field.TYPE)
            if (hasDate && hasAmount && hasCategory) return FoundHeader(index, fields)
        }
        return null
    }

    private fun fieldOf(raw: String): Field? {
        val header = raw.trim()
            .replace(" ", "")
            .removeSuffix("（元）")
            .removeSuffix("(元)")
            .removeSuffix("（人民币）")
            .removeSuffix("(人民币)")
        return when {
            header == "日期" || header == "时间" || header.contains("日期") || header.contains("时间") -> Field.DATE
            header == "类型" || header == "收支" || header == "收支类型" || header == "记账类型" ||
                header == "收/支" || header == "收/支类型" -> Field.TYPE
            header == "金额" || header == "交易金额" || header == "发生金额" || header.endsWith("金额") -> Field.AMOUNT
            header == "二级分类" || header == "子分类" -> Field.SUBCATEGORY
            header == "一级分类" || header == "分类" || header == "交易分类" || header == "主分类" ||
                header == "交易类型" || header.endsWith("分类") -> Field.CATEGORY
            header == "备注" || header == "说明" || header.contains("商品") ||
                header == "交易备注" || header.contains("备注") -> Field.NOTE
            header.startsWith("账户") || header == "支付方式" || header == "付款方式" -> Field.ACCOUNT
            header == "货币" || header == "币种" -> Field.CURRENCY
            else -> null
        }
    }

    private fun parseType(text: String): RawType {
        val value = text.trim().lowercase()
        if (value.isEmpty()) return RawType.UNKNOWN
        return when {
            value.contains("转账") || value.contains("transfer") -> RawType.TRANSFER
            value.contains("支出") || value.contains("付款") || value.contains("消费") ||
                value == "out" || value.contains("expense") || value.contains("debit") -> RawType.EXPENSE
            value.contains("收入") || value.contains("收款") ||
                value == "in" || value.contains("income") || value.contains("credit") -> RawType.INCOME
            else -> RawType.UNKNOWN
        }
    }

    private fun FoundHeader.text(row: List<String>, field: Field): String {
        val index = fields[field] ?: return ""
        return row.getOrNull(index)?.trim() ?: ""
    }
}
