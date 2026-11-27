package app.fen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsBoardConfigTest {

    @Test
    fun `unknown section keys are ignored`() {
        val result = sanitizeStatsOrder(listOf("trend", "not_a_section", "overview"))
        assertEquals(listOf(StatsSection.TREND, StatsSection.OVERVIEW), result.take(2))
        assertTrue(StatsSection.OVERVIEW in result)
    }

    @Test
    fun `duplicate keys keep first position`() {
        val result = sanitizeStatsOrder(listOf("summary", "overview", "summary"))
        assertEquals(StatsSection.SUMMARY, result.first())
        assertEquals(StatsSection.OVERVIEW, result[1])
        assertEquals(1, result.count { it == StatsSection.SUMMARY })
    }

    @Test
    fun `missing sections are appended in default order`() {
        val result = sanitizeStatsOrder(listOf("summary"))
        assertEquals(StatsSection.SUMMARY, result.first())
        assertEquals(StatsSection.defaultOrder.size, result.size)
        assertEquals(StatsSection.defaultOrder.toSet(), result.toSet())
    }

    @Test
    fun `default order contains all sections`() {
        assertEquals(StatsSection.entries.toSet(), StatsSection.defaultOrder.toSet())
        assertEquals(StatsSection.defaultOrder.size, StatsSection.defaultOrder.distinct().size)
    }
}
