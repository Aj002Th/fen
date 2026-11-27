package app.fen.mcp.tools

import app.fen.data.Category
import app.fen.data.Snapshot
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.data.ledger.LedgerReader
import app.fen.data.query.GroupBy
import app.fen.data.query.Sort
import app.fen.data.query.SummaryType
import app.fen.data.query.TxFilter
import app.fen.data.query.query
import app.fen.data.query.summarize
import app.fen.data.query.totals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReadTools(
    private val reader: LedgerReader,
    private val serverVersion: String,
    private val writeEnabled: () -> Boolean,
    private val defaultLimit: () -> Int = { 100 },
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    fun descriptors(): List<ToolDescriptor> = listOf(
        ToolDescriptor(
            name = "get_overview",
            description = "Fen 账本概览：数据范围、时区、当前月收支、分类数。建议第一步调用。",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
            },
            kind = ToolKind.READ,
            handler = { getOverview() },
        ),
        ToolDescriptor(
            name = "list_transactions",
            description = "按时间、类型、分类、关键词、金额区间查询流水，游标分页。金额参数用元字符串。",
            inputSchema = listTransactionsSchema(),
            kind = ToolKind.READ,
            handler = { args -> listTransactions(args) },
        ),
        ToolDescriptor(
            name = "get_summary",
            description = "按区间或月份汇总收支；totals 总是包含收支两侧，type 只过滤 groups。",
            inputSchema = getSummarySchema(),
            kind = ToolKind.READ,
            handler = { args -> getSummary(args) },
        ),
        ToolDescriptor(
            name = "list_categories",
            description = "列出分类 id、名称、类型、使用次数和最近使用时间。写入前必须先拿 id。",
            inputSchema = listCategoriesSchema(),
            kind = ToolKind.READ,
            handler = { args -> listCategories(args) },
        ),
    )

    private fun getOverview(): ToolResult {
        val snapshot = reader.readSnapshot()
        val txs = snapshot.transactions
        val month = YearMonth.now(zone)
        val monthTxs = txs.filter { YearMonth.from(it.localDate()) == month }
        val monthTotals = monthTxs.totals()
        val dates = txs.map { it.localDate() }.sorted()

        val payload = buildJsonObject {
            put("schema_version", 1)
            put("server_version", serverVersion)
            put("timezone", zone.id)
            put("currency", "CNY")
            put(
                "data",
                buildJsonObject {
                    put("first_date", dates.firstOrNull()?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
                    put("last_date", dates.lastOrNull()?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
                    put("transaction_count", txs.size)
                    put("category_count", snapshot.categories.size)
                },
            )
            put(
                "current_month",
                buildJsonObject {
                    put("month", month.toString())
                    put("expense_minor", monthTotals.expense)
                    put("income_minor", monthTotals.income)
                    put("net_minor", monthTotals.net)
                    put("transaction_count", monthTxs.size)
                },
            )
            put("write_enabled", writeEnabled())
        }
        return ToolResult.Ok(payload)
    }

    private fun listTransactions(args: JsonObject): ToolResult {
        val snapshot = reader.readSnapshot()
        val month = args.stringOrNull("month")
        val fromRaw = args.stringOrNull("from")
        val toRaw = args.stringOrNull("to")
        if (month != null && (fromRaw != null || toRaw != null)) {
            return ToolResult.Error("VALIDATION", "month and from/to are mutually exclusive")
        }

        val fromMillis: Long?
        val toMillis: Long?
        if (month != null) {
            val parsed = parseMonth(month) ?: return ToolResult.Error("VALIDATION", "Invalid month: $month")
            fromMillis = parsed.atDay(1).startMillis()
            toMillis = parsed.atEndOfMonth().endMillis()
        } else {
            val from = fromRaw?.let { parseDate(it) ?: return ToolResult.Error("VALIDATION", "Invalid from: $it") }
            val to = toRaw?.let { parseDate(it) ?: return ToolResult.Error("VALIDATION", "Invalid to: $it") }
            fromMillis = from?.startMillis()
            toMillis = to?.endMillis()
        }

        val type = when (args.stringOrNull("type")) {
            null -> null
            "expense" -> TxType.EXPENSE
            "income" -> TxType.INCOME
            else -> return ToolResult.Error("VALIDATION", "type must be expense or income")
        }
        val minMinor = args.stringOrNull("min_amount")?.let {
            Money.parseYuanToMinor(it) ?: return ToolResult.Error("VALIDATION", "Invalid min_amount")
        }
        val maxMinor = args.stringOrNull("max_amount")?.let {
            Money.parseYuanToMinor(it) ?: return ToolResult.Error("VALIDATION", "Invalid max_amount")
        }
        val limit = args.intOrNull("limit") ?: defaultLimit()
        if (limit !in 1..200) return ToolResult.Error("VALIDATION", "limit must be 1..200")

        val filter = TxFilter(
            fromMillis = fromMillis,
            toMillis = toMillis,
            type = type,
            categoryId = args.stringOrNull("category_id"),
            keyword = args.stringOrNull("keyword") ?: "",
            minMinor = minMinor,
            maxMinor = maxMinor,
        )
        val categories = snapshot.categories.associateBy { it.id }
        val all = snapshot.transactions.query(filter, categories, Sort.TIME_DESC)

        val cursor = args.stringOrNull("cursor")?.let {
            Cursor.decode(it) ?: return ToolResult.Error("VALIDATION", "Invalid cursor")
        }
        val afterCursor = if (cursor == null) all else all.filter {
            it.occurredAt < cursor.first ||
                (it.occurredAt == cursor.first && it.id < cursor.second)
        }
        val page = afterCursor.take(limit)
        val hasMore = afterCursor.size > limit

        val payload = buildJsonObject {
            put("items", buildJsonArray { page.forEach { add(it.toJson(categories)) } })
            put("next_cursor", if (hasMore && page.isNotEmpty()) JsonPrimitive(Cursor.encode(page.last().occurredAt, page.last().id)) else JsonNull)
            put("has_more", hasMore)
            put("matched_count", all.size)
        }
        return ToolResult.Ok(payload)
    }

    private fun getSummary(args: JsonObject): ToolResult {
        val snapshot = reader.readSnapshot()
        val monthRaw = args.stringOrNull("month")
        val fromRaw = args.stringOrNull("from")
        val toRaw = args.stringOrNull("to")

        val from: LocalDate
        val to: LocalDate
        var previousFrom: Long? = null
        var previousTo: Long? = null
        if (monthRaw != null) {
            if (fromRaw != null || toRaw != null) {
                return ToolResult.Error("VALIDATION", "month and from/to are mutually exclusive")
            }
            val month = parseMonth(monthRaw) ?: return ToolResult.Error("VALIDATION", "Invalid month: $monthRaw")
            from = month.atDay(1)
            to = month.atEndOfMonth()
            val prev = month.minusMonths(1)
            previousFrom = prev.atDay(1).startMillis()
            previousTo = prev.atEndOfMonth().endMillis()
        } else {
            if (fromRaw == null || toRaw == null) {
                return ToolResult.Error("VALIDATION", "Provide month or both from/to")
            }
            from = parseDate(fromRaw) ?: return ToolResult.Error("VALIDATION", "Invalid from: $fromRaw")
            to = parseDate(toRaw) ?: return ToolResult.Error("VALIDATION", "Invalid to: $toRaw")
            if (to < from) return ToolResult.Error("VALIDATION", "to must be on or after from")
        }

        val groupBy = when (args.stringOrNull("group_by") ?: "category") {
            "category" -> GroupBy.CATEGORY
            "day" -> GroupBy.DAY
            "month" -> GroupBy.MONTH
            "type" -> GroupBy.TYPE
            else -> return ToolResult.Error("VALIDATION", "group_by must be category, day, month or type")
        }
        val type = when (args.stringOrNull("type") ?: "expense") {
            "expense" -> SummaryType.EXPENSE
            "income" -> SummaryType.INCOME
            "all" -> SummaryType.ALL
            else -> return ToolResult.Error("VALIDATION", "type must be expense, income or all")
        }

        val summary = summarize(
            txs = snapshot.transactions,
            categories = snapshot.categories,
            fromMillis = from.startMillis(),
            toMillis = to.endMillis(),
            groupBy = groupBy,
            type = type,
            zone = zone,
            previousFromMillis = previousFrom,
            previousToMillis = previousTo,
        )

        val payload = buildJsonObject {
            put(
                "range",
                buildJsonObject {
                    put("from", from.toString())
                    put("to", to.toString())
                },
            )
            put(
                "totals",
                buildJsonObject {
                    put("expense_minor", summary.totals.expense)
                    put("income_minor", summary.totals.income)
                    put("net_minor", summary.totals.net)
                    put("count", summary.count)
                },
            )
            put(
                "previous",
                buildJsonObject {
                    val previous = summary.previous
                    put("expense_minor", previous?.expense ?: 0L)
                    put("income_minor", previous?.income ?: 0L)
                    put("net_minor", previous?.net ?: 0L)
                    put("count", summary.previousCount ?: 0)
                },
            )
            put(
                "change",
                buildJsonObject {
                    put("expense_percent", summary.expenseChangePercent?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("income_percent", summary.incomeChangePercent?.let { JsonPrimitive(it) } ?: JsonNull)
                },
            )
            put(
                "groups",
                buildJsonArray {
                    summary.groups.forEach { group ->
                        add(
                            buildJsonObject {
                                put("key", group.key?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("label", group.label)
                                put("type", group.type.name.lowercase())
                                put("amount_minor", group.amount)
                                put("amount", Money.formatPlain(group.amount))
                                put("count", group.count)
                                put("share", group.sharePercent)
                            },
                        )
                    }
                },
            )
        }
        return ToolResult.Ok(payload)
    }

    private fun listCategories(args: JsonObject): ToolResult {
        val snapshot = reader.readSnapshot()
        val type = when (args.stringOrNull("type")) {
            null -> null
            "expense" -> TxType.EXPENSE
            "income" -> TxType.INCOME
            else -> return ToolResult.Error("VALIDATION", "type must be expense or income")
        }
        val usage = snapshot.transactions
            .mapNotNull { tx -> tx.categoryId?.let { it to tx } }
            .groupBy({ it.first }, { it.second })
        val payload = buildJsonObject {
            put(
                "categories",
                buildJsonArray {
                    snapshot.categories
                        .filter { type == null || it.type == type }
                        .sortedBy { it.sortOrder }
                        .forEach { category ->
                            val txs = usage[category.id].orEmpty()
                            add(category.toJson(txs.size, txs.maxOfOrNull { it.occurredAt }))
                        }
                },
            )
        }
        return ToolResult.Ok(payload)
    }

    private fun listTransactionsSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put(
            "properties",
            buildJsonObject {
                put("month", stringProperty("本地月份 YYYY-MM；与 from/to 二选一", "^\\d{4}-\\d{2}$"))
                put("from", stringProperty("本地开始日期 YYYY-MM-DD，含当天", "^\\d{4}-\\d{2}-\\d{2}$"))
                put("to", stringProperty("本地结束日期 YYYY-MM-DD，含当天", "^\\d{4}-\\d{2}-\\d{2}$"))
                put("type", enumProperty("收支类型", listOf("expense", "income")))
                put("category_id", stringProperty("分类 id，来自 list_categories"))
                put("keyword", stringProperty("匹配备注、分类名或格式化金额", null))
                put("min_amount", stringProperty("最小金额（元），如 10.00", "^\\d{1,9}(\\.\\d{1,2})?$"))
                put("max_amount", stringProperty("最大金额（元），如 100.00", "^\\d{1,9}(\\.\\d{1,2})?$"))
                put(
                    "limit",
                    buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("maximum", 200)
                        put("default", 100)
                    },
                )
                put("cursor", stringProperty("上一页返回的 next_cursor"))
            },
        )
    }

    private fun getSummarySchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put(
            "properties",
            buildJsonObject {
                put("month", stringProperty("本地月份 YYYY-MM；与 from/to 二选一", "^\\d{4}-\\d{2}$"))
                put("from", stringProperty("本地开始日期 YYYY-MM-DD", "^\\d{4}-\\d{2}-\\d{2}$"))
                put("to", stringProperty("本地结束日期 YYYY-MM-DD", "^\\d{4}-\\d{2}-\\d{2}$"))
                put("group_by", enumProperty("分组方式", listOf("category", "day", "month", "type")))
                put("type", enumProperty("只影响 groups，不影响 totals", listOf("expense", "income", "all")))
            },
        )
    }

    private fun listCategoriesSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put(
            "properties",
            buildJsonObject {
                put("type", enumProperty("收支类型", listOf("expense", "income")))
            },
        )
    }

    private fun Tx.toJson(categories: Map<String, Category>): JsonObject = buildJsonObject {
        put("id", id)
        put("type", type.name.lowercase())
        put("amount_minor", amount)
        put("amount", Money.formatPlain(amount))
        put("currency", "CNY")
        put("category_id", categoryId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("category_name", categoryId?.let { categories[it]?.name }?.let { JsonPrimitive(it) } ?: JsonNull)
        put("note", note)
        put("occurred_at", isoOffset(occurredAt))
        put("occurred_at_ms", occurredAt)
    }

    private fun Category.toJson(usageCount: Int, lastUsedAt: Long?): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("type", type.name.lowercase())
        put("usage_count", usageCount)
        put("last_used_at", lastUsedAt?.let { JsonPrimitive(isoOffset(it)) } ?: JsonNull)
    }

    private fun Tx.localDate(): LocalDate = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()

    private fun isoOffset(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun LocalDate.startMillis(): Long =
        atStartOfDay(zone).toInstant().toEpochMilli()

    private fun LocalDate.endMillis(): Long =
        plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    private fun parseMonth(raw: String): YearMonth? =
        if (Regex("^\\d{4}-\\d{2}$").matches(raw)) YearMonth.parse(raw) else null

    private fun parseDate(raw: String): LocalDate? =
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(raw)) LocalDate.parse(raw) else null

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun stringProperty(description: String, pattern: String? = null): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        if (pattern != null) put("pattern", pattern)
    }

    private fun enumProperty(description: String, values: List<String>): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        put("enum", JsonArray(values.map { JsonPrimitive(it) }))
    }
}
