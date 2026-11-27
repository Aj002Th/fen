package app.fen.mcp.net

class RateLimiter(
    private val clock: () -> Long,
    private val maxPerMinute: Int = 60,
    private val maxFailures: Int = 5,
    private val blockMs: Long = 5 * 60_000,
) {

    private data class Window(var start: Long, var count: Int)
    private data class Failure(var count: Int, var blockedUntil: Long)

    private val windows = HashMap<String, Window>()
    private val failures = HashMap<String, Failure>()

    @Synchronized
    fun allow(ip: String): Boolean {
        val now = clock()
        prune(now)
        val failure = failures[ip]
        if (failure != null && failure.blockedUntil > now) return false

        val window = windows[ip]
        if (window == null || now - window.start >= 60_000) {
            windows[ip] = Window(now, 1)
            return true
        }
        if (window.count >= maxPerMinute) return false
        window.count += 1
        return true
    }

    @Synchronized
    fun recordAuthFailure(ip: String) {
        val now = clock()
        val failure = failures[ip] ?: Failure(0, 0)
        failure.count += 1
        if (failure.count >= maxFailures) {
            failure.blockedUntil = now + blockMs
            failure.count = 0
        }
        failures[ip] = failure
    }

    @Synchronized
    fun recordSuccess(ip: String) {
        failures.remove(ip)
    }

    private fun prune(now: Long) {
        if (windows.size > 512) {
            windows.entries.removeAll { now - it.value.start >= 60_000 }
        }
        if (failures.size > 512) {
            failures.entries.removeAll { it.value.blockedUntil in 1..now && it.value.count == 0 }
        }
    }
}
