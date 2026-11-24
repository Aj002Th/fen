package app.fen.data.exchange

import app.fen.data.Tx
import app.fen.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExportFilterTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val txs = listOf(
        Tx("a", TxType.EXPENSE, 100, "food", "", at("2026-11-01", 0)),
        Tx("b", TxType.EXPENSE, 200, "food", "", at("2026-11-20", 23)),
        Tx("c", TxType.INCOME, 300, "salary", "", at("2026-11-15")),
        Tx("d", TxType.EXPENSE, 400, "transit", "", at("2026-10-31", 23)),
        Tx("e", TxType.EXPENSE, 500, null, "", at("2026-11-10")),
        Tx("f", TxType.INCOME, 600, "salary", "", at("2026-12-01", 0)),
    )

    @Test
    fun emptyFilterKeepsEverything() {
        assertEquals(txs.map { it.id }, txs.filterForExport(ExportFilter(), zone).map { it.id })
    }

    @Test
    fun dateRangeIsInclusiveOnBothEnds() {
        val filter = ExportFilter(
            from = LocalDate.parse("2026-11-01"),
            to = LocalDate.parse("2026-11-30"),
        )
        assertEquals(listOf("a", "b", "c", "e"), txs.filterForExport(filter, zone).map { it.id })
    }

    @Test
    fun openEndedRange() {
        val from = txs.filterForExport(ExportFilter(from = LocalDate.parse("2026-11-15")), zone)
        assertEquals(listOf("b", "c", "f"), from.map { it.id })
        val until = txs.filterForExport(ExportFilter(to = LocalDate.parse("2026-11-01")), zone)
        assertEquals(listOf("a", "d"), until.map { it.id })
    }

    @Test
    fun scopeFiltersType() {
        val expense = txs.filterForExport(ExportFilter(scope = ExportScope.EXPENSE), zone)
        assertEquals(listOf("a", "b", "d", "e"), expense.map { it.id })
        val income = txs.filterForExport(ExportFilter(scope = ExportScope.INCOME), zone)
        assertEquals(listOf("c", "f"), income.map { it.id })
    }

    @Test
    fun categoriesDropUncategorizedAndUnknown() {
        val filter = ExportFilter(categoryIds = setOf("food"))
        assertEquals(listOf("a", "b"), txs.filterForExport(filter, zone).map { it.id })
    }

    @Test
    fun emptyCategorySetExportsNothing() {
        val filter = ExportFilter(categoryIds = emptySet())
        assertEquals(emptyList<String>(), txs.filterForExport(filter, zone).map { it.id })
    }

    @Test
    fun filtersCombine() {
        val filter = ExportFilter(
            from = LocalDate.parse("2026-11-01"),
            to = LocalDate.parse("2026-11-30"),
            scope = ExportScope.INCOME,
            categoryIds = setOf("salary"),
        )
        assertEquals(listOf("c"), txs.filterForExport(filter, zone).map { it.id })
    }
}
