package app.fen.ui

import app.fen.ui.theme.FenMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootTabsTest {

    @Test
    fun `selecting stats replaces bills`() {
        assertEquals(listOf("stats"), RootTabs.stackAfterSelect(listOf("bills"), "stats"))
    }

    @Test
    fun `selecting bills replaces stats`() {
        assertEquals(listOf("bills"), RootTabs.stackAfterSelect(listOf("stats"), "bills"))
    }

    @Test
    fun `selecting the current root keeps that stack`() {
        val current = listOf("stats")
        assertEquals(current, RootTabs.stackAfterSelect(current, "stats"))
    }

    @Test
    fun `only bills and stats are roots`() {
        assertFalse(RootTabs.isRoot("settings"))
        assertTrue(RootTabs.isRoot("bills"))
        assertTrue(RootTabs.isRoot("stats"))
    }

    @Test
    fun `shared motion duration is 180 milliseconds`() {
        assertEquals(180, FenMotion.durationMillis)
    }
}
