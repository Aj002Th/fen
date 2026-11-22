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
    fun `returning from a pushed page slides the root back in`() {
        assertTrue(RootTabs.slidesWhenEntering("settings", "bills", isPop = true))
        assertTrue(RootTabs.slidesWhenEntering("settings", "stats", isPop = true))
    }

    @Test
    fun `switching root tabs does not slide`() {
        assertFalse(RootTabs.slidesWhenEntering("bills", "stats", isPop = false))
        assertFalse(RootTabs.slidesWhenEntering("stats", "bills", isPop = false))
    }

    @Test
    fun `pushing a page slides it in`() {
        assertTrue(RootTabs.slidesWhenEntering("bills", "settings", isPop = false))
        assertTrue(RootTabs.slidesWhenEntering("settings", "categories", isPop = false))
    }

    @Test
    fun `navigation motion is a short slide with a faster fade out`() {
        assertEquals(260, FenMotion.navSlideMillis)
        assertEquals(180, FenMotion.navFadeInMillis)
        assertEquals(120, FenMotion.navFadeOutMillis)
        assertTrue(FenMotion.navFadeOutMillis < FenMotion.navSlideMillis)
    }

    @Test
    fun `in-page motion stays short`() {
        assertEquals(180, FenMotion.durationMillis)
    }
}
