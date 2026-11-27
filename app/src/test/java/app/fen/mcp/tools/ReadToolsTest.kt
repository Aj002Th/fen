package app.fen.mcp.tools

import app.fen.data.Category
import app.fen.data.Snapshot
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.data.ledger.LedgerReader
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadToolsTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val food = Category("c-food", "餐饮", TxType.EXPENSE, "food", 0L, 0)
    private val shopping = Category("c-shopping", "购物", TxType.EXPENSE, "shopping", 0L, 1)
    private val salary = Category("c-salary", "工资", TxType.INCOME, "salary", 0L, 2)
    private val categories = listOf(food, shopping, salary)

    private fun at(day: Int, hour: Int = 12): Long =
        LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val txs = listOf(
        Tx("1", TxType.EXPENSE, 12_345, food.id, "午饭", at(21, 12)),
        Tx("2", TxType.EXPENSE, 6_000, shopping.id, "超市", at(20, 12)),
        Tx("3", TxType.EXPENSE, 4_000, null, "", at(19, 12)),
        Tx("4", TxType.INCOME, 900_000, salary.id, "月薪", at(18, 12)),
        Tx("5", TxType.EXPENSE, 3_200, food.id, "", at(17, 12)),
    )

    private val tools = ReadTools(
        reader = LedgerReader { Snapshot(categories, txs) },
        serverVersion = "0.9.0",
        writeEnabled = { false },
        zone = zone,
    )

    private suspend fun call(name: String, args: JsonObject = buildJsonObject { }): ToolResult =
        tools.descriptors().first { it.name == name }.handler(args)

    private fun payload(result: ToolResult): JsonObject {
        assertTrue(result is ToolResult.Ok)
        return (result as ToolResult.Ok).payload
    }

    @Test
    fun overviewHasRangeAndCounts() = runTest {
        val body = payload(call("get_overview"))

        assertEquals(5, body["data"]!!.jsonObject["transaction_count"]!!.jsonPrimitive.content.toInt())
        assertEquals("2026-11-17", body["data"]!!.jsonObject["first_date"]!!.jsonPrimitive.content)
        assertEquals("2026-11-21", body["data"]!!.jsonObject["last_date"]!!.jsonPrimitive.content)
        assertEquals("Asia/Shanghai", body["timezone"]!!.jsonPrimitive.content)
        assertFalse(body["write_enabled"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun listTransactionsPagesWithoutDuplicates() = runTest {
        val first = payload(call("list_transactions", buildJsonObject { put("limit", 2) }))
        val firstIds = first["items"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        val cursor = first["next_cursor"]!!.jsonPrimitive.content

        assertEquals(2, firstIds.size)
        assertTrue(first["has_more"]!!.jsonPrimitive.content.toBoolean())

        val second = payload(
            call(
                "list_transactions",
                buildJsonObject {
                    put("limit", 2)
                    put("cursor", cursor)
                },
            )
        )
        val secondIds = second["items"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }

        assertEquals(2, secondIds.size)
        assertTrue(firstIds.intersect(secondIds.toSet()).isEmpty())
    }

    @Test
    fun listTransactionsRejectsBadCursor() = runTest {
        val result = call("list_transactions", buildJsonObject { put("cursor", "not-base64") })

        assertTrue(result is ToolResult.Error)
        assertEquals("VALIDATION", (result as ToolResult.Error).code)
    }

    @Test
    fun keywordMatchesCategoryAndFormattedAmount() = runTest {
        val byCategory = payload(call("list_transactions", buildJsonObject { put("keyword", "餐饮") }))
        val byAmount = payload(call("list_transactions", buildJsonObject { put("keyword", "123.45") }))

        assertEquals(2, byCategory["items"]!!.jsonArray.size)
        assertEquals(1, byAmount["items"]!!.jsonArray.size)
    }

    @Test
    fun summaryReconcilesTotalsAndGroups() = runTest {
        val body = payload(call("get_summary", buildJsonObject { put("month", "2026-11") }))
        val totals = body["totals"]!!.jsonObject
        val groups = body["groups"]!!.jsonArray

        assertEquals(25_545, totals["expense_minor"]!!.jsonPrimitive.content.toInt())
        assertEquals(900_000, totals["income_minor"]!!.jsonPrimitive.content.toInt())
        assertEquals(25_545, groups.sumOf { it.jsonObject["amount_minor"]!!.jsonPrimitive.content.toInt() })
        assertTrue(groups.any { it.jsonObject["label"]!!.jsonPrimitive.content == "未分类" })
    }

    @Test
    fun summaryRejectsMonthAndRangeTogether() = runTest {
        val result = call(
            "get_summary",
            buildJsonObject {
                put("month", "2026-11")
                put("from", "2026-11-01")
            },
        )

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun categoriesCountUsage() = runTest {
        val body = payload(call("list_categories"))
        val list = body["categories"]!!.jsonArray

        assertEquals(3, list.size)
        val foodJson = list.first { it.jsonObject["id"]!!.jsonPrimitive.content == food.id }.jsonObject
        assertEquals(2, foodJson["usage_count"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun currentMonthIsReported() = runTest {
        val now = LocalDateTime.now(zone)
        val tx = Tx(
            "now",
            TxType.EXPENSE,
            1_000,
            food.id,
            "",
            now.atZone(zone).toInstant().toEpochMilli(),
        )
        val localTools = ReadTools(
            reader = LedgerReader { Snapshot(categories, txs + tx) },
            serverVersion = "0.9.0",
            writeEnabled = { false },
            zone = zone,
        )

        val body = (localTools.descriptors().first { it.name == "get_overview" }.handler(buildJsonObject { }) as ToolResult.Ok).payload

        assertEquals(YearMonth.now(zone).toString(), body["current_month"]!!.jsonObject["month"]!!.jsonPrimitive.content)
    }
}
