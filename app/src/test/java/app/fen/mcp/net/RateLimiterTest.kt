package app.fen.mcp.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    private var now = 1_000_000L
    private val limiter = RateLimiter(
        clock = { now },
        maxPerMinute = 3,
        maxFailures = 2,
        blockMs = 60_000,
    )

    @Test
    fun limitsRequestsPerMinute() {
        assertTrue(limiter.allow("1.2.3.4"))
        assertTrue(limiter.allow("1.2.3.4"))
        assertTrue(limiter.allow("1.2.3.4"))
        assertFalse(limiter.allow("1.2.3.4"))
    }

    @Test
    fun resetsAfterAMinute() {
        repeat(3) { limiter.allow("1.2.3.4") }
        assertFalse(limiter.allow("1.2.3.4"))
        now += 60_001
        assertTrue(limiter.allow("1.2.3.4"))
    }

    @Test
    fun blocksAfterRepeatedAuthFailures() {
        limiter.recordAuthFailure("1.2.3.4")
        limiter.recordAuthFailure("1.2.3.4")
        assertFalse(limiter.allow("1.2.3.4"))
        now += 60_001
        assertTrue(limiter.allow("1.2.3.4"))
    }

    @Test
    fun successClearsFailures() {
        limiter.recordAuthFailure("1.2.3.4")
        limiter.recordSuccess("1.2.3.4")
        limiter.recordAuthFailure("1.2.3.4")
        assertTrue(limiter.allow("1.2.3.4"))
    }

    @Test
    fun separateIpsDoNotShareWindows() {
        repeat(3) { limiter.allow("1.1.1.1") }
        assertFalse(limiter.allow("1.1.1.1"))
        assertTrue(limiter.allow("2.2.2.2"))
    }
}
