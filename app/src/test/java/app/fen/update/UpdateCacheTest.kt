package app.fen.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `clean removes every file in the updates dir`() {
        val dir = tmp.newFolder("updates")
        File(dir, "Fen-1.0.0.apk").writeText("partial")
        File(dir, "Fen-1.1.0.apk.part").writeText("partial")
        cleanUpdatesDir(dir)
        assertTrue(dir.isDirectory)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `clean survives a missing dir`() {
        val dir = File(tmp.root, "does-not-exist")
        cleanUpdatesDir(dir)
        assertFalse(dir.exists())
    }
}
