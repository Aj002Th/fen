package app.fen.autobook.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportIntentGateTest {
    @Test
    fun `first create with image is handled`() {
        assertTrue(ImportIntentGate.shouldHandleOnCreate(restored = false, hasImage = true))
    }

    @Test
    fun `restored create is ignored`() {
        assertFalse(ImportIntentGate.shouldHandleOnCreate(restored = true, hasImage = true))
    }

    @Test
    fun `new intent with image is handled`() {
        assertTrue(ImportIntentGate.shouldHandleNewIntent(hasImage = true))
    }

    @Test
    fun `new intent without image is ignored`() {
        assertFalse(ImportIntentGate.shouldHandleNewIntent(hasImage = false))
    }
}
