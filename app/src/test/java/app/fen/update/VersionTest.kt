package app.fen.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun `normalize strips v prefix and dev suffix`() {
        assertEquals("1.0.0", UpdateVersion.normalize("v1.0.0"))
        assertEquals("1.0.0", UpdateVersion.normalize("1.0.0-dev.feat_update"))
        assertEquals("1.0.0", UpdateVersion.normalize(" v1.0.0 "))
    }

    @Test
    fun `segments treat missing parts as zero`() {
        assertEquals(listOf(1, 0, 0), UpdateVersion.segments("1"))
        assertEquals(listOf(1, 2, 0), UpdateVersion.segments("1.2"))
        assertEquals(listOf(1, 2, 3), UpdateVersion.segments("1.2.3"))
    }

    @Test
    fun `segments turn junk into zero instead of throwing`() {
        assertEquals(listOf(1, 0, 0), UpdateVersion.segments("1.x.0"))
        assertEquals(listOf(0, 0, 0), UpdateVersion.segments(""))
    }

    @Test
    fun `newer patch wins`() {
        assertTrue(UpdateVersion.isNewer("1.0.1", "1.0.0"))
        assertFalse(UpdateVersion.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(UpdateVersion.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateVersion.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateVersion.isNewer("v1.0.0", "1.0.0-dev.master"))
    }

    @Test
    fun `numeric comparison is not string comparison`() {
        assertTrue(UpdateVersion.isNewer("1.10.0", "1.9.9"))
        assertFalse(UpdateVersion.isNewer("1.9.9", "1.10.0"))
    }
}
