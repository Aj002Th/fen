package app.fen.data.ledger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoPolicyTest {

    private fun batch(createdAt: Long, undoneAt: Long? = null) = BatchSummary(
        id = "b1",
        tool = "add_transactions",
        createdAt = createdAt,
        undoneAt = undoneAt,
        clientName = null,
        itemCount = 1,
        idempotencyKey = "key",
    )

    @Test
    fun allowsWithinWindow() {
        assertTrue(canUndo(batch(1_000), now = 1_000 + 23 * 60 * 60 * 1000))
    }

    @Test
    fun rejectsExpiredWindow() {
        assertFalse(canUndo(batch(1_000), now = 1_000 + 25 * 60 * 60 * 1000))
    }

    @Test
    fun rejectsAlreadyUndone() {
        assertFalse(canUndo(batch(1_000, undoneAt = 2_000), now = 3_000))
    }
}
