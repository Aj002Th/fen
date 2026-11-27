package app.fen.mcp.write

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteGateTest {

    private var now = 1_000L
    private val gate = WriteGate { now }

    @Test
    fun disabledByDefault() {
        assertFalse(gate.isEnabled(null))
        assertEquals(0L, gate.remainingMs(null))
    }

    @Test
    fun enabledUntilDeadline() {
        assertTrue(gate.isEnabled(2_000L))
        assertEquals(1_000L, gate.remainingMs(2_000L))
    }

    @Test
    fun expiresAtDeadline() {
        now = 2_000L
        assertFalse(gate.isEnabled(2_000L))
        assertEquals(0L, gate.remainingMs(2_000L))
    }
}
