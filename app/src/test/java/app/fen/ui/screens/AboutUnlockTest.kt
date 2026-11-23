package app.fen.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutUnlockTest {

    @Test
    fun `nine taps are not enough`() {
        assertFalse(shouldUnlockDev(0))
        assertFalse(shouldUnlockDev(1))
        assertFalse(shouldUnlockDev(9))
    }

    @Test
    fun `the tenth tap unlocks`() {
        assertTrue(shouldUnlockDev(10))
        assertTrue(shouldUnlockDev(11))
    }
}
