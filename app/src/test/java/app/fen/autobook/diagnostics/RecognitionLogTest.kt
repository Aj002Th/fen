package app.fen.autobook.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionLogTest {
    @Test
    fun `keeps newest entries within limit`() {
        val log = RecognitionLog(maxEntries = 2)

        log.add("a")
        log.add("b")
        log.add("c")

        assertEquals(listOf("b", "c"), log.snapshot())
    }

    @Test
    fun `clear removes all entries`() {
        val log = RecognitionLog()
        log.add("a")

        log.clear()

        assertTrue(log.snapshot().isEmpty())
    }
}
