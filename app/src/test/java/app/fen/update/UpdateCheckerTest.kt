package app.fen.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private class FakeSource(var release: UpdateRelease?, var error: String? = null) : ReleaseSource {
        var calls = 0
        override suspend fun fetchLatest(): Result<UpdateRelease> {
            calls += 1
            val r = release
            return if (error != null || r == null) {
                Result.failure(IllegalStateException(error ?: "empty"))
            } else {
                Result.success(r)
            }
        }
    }

    private class FakeStore : UpdateStore {
        override var lastCheckAt: Long = 0L
        override var cachedReleaseJson: String = ""
    }

    private fun release(version: String) = ReleaseJson.parse(
        """{"tag_name":"v$version","body":"notes","html_url":"h","assets":[
             {"name":"Fen-$version.apk","browser_download_url":"https://x/a.apk","size":10}]}"""
    )!!

    @Test
    fun `first check fetches and reports a newer release`() = runBlocking {
        val source = FakeSource(release("1.1.0"))
        val checker = UpdateChecker(source, FakeStore(), "1.0.0") { 1_000L }
        val result = checker.check(force = false)
        assertTrue(result is UpdateCheck.Available)
        assertEquals("1.1.0", (result as UpdateCheck.Available).release.version)
        assertEquals(1, source.calls)
    }

    @Test
    fun `same version is up to date`() = runBlocking {
        val checker = UpdateChecker(FakeSource(release("1.0.0")), FakeStore(), "1.0.0") { 1_000L }
        assertTrue(checker.check(force = false) is UpdateCheck.UpToDate)
    }

    @Test
    fun `second check within an hour does not hit the network`() = runBlocking {
        val source = FakeSource(release("1.1.0"))
        val store = FakeStore()
        val checker = UpdateChecker(source, store, "1.0.0") { 1_000L }
        checker.check(force = false)
        store.lastCheckAt = 1_000L + 3_599_999L
        val result = UpdateChecker(source, store, "1.0.0") { 1_000L + 3_599_999L }.check(force = false)
        assertEquals(1, source.calls)
        assertTrue(result is UpdateCheck.Available)
    }

    @Test
    fun `force check ignores the throttle`() = runBlocking {
        val source = FakeSource(release("1.1.0"))
        val checker = UpdateChecker(source, FakeStore(), "1.0.0") { 1_000L }
        checker.check(force = true)
        checker.check(force = true)
        assertEquals(2, source.calls)
    }

    @Test
    fun `clock going backwards still checks`() = runBlocking {
        val source = FakeSource(release("1.1.0"))
        val store = FakeStore().apply { lastCheckAt = 9_999_999_999L }
        val checker = UpdateChecker(source, store, "1.0.0") { 1_000L }
        checker.check(force = false)
        assertEquals(1, source.calls)
    }

    @Test
    fun `failure keeps offering the cached newer release`() = runBlocking {
        val store = FakeStore()
        val good = UpdateChecker(FakeSource(release("1.1.0")), store, "1.0.0") { 1_000L }
        good.check(force = true)
        val failing = FakeSource(null, error = "网络连接失败，请检查网络")
        val result = UpdateChecker(failing, store, "1.0.0") { 2_000_000L }.check(force = true)
        val available = result as UpdateCheck.Available
        assertEquals("1.1.0", available.release.version)
        assertTrue(available.stale)
    }

    @Test
    fun `failure without cache is reported`() = runBlocking {
        val result = UpdateChecker(
            FakeSource(null, error = "网络连接失败，请检查网络"),
            FakeStore(),
            "1.0.0",
        ) { 1_000L }.check(force = true)
        assertTrue(result is UpdateCheck.Failed)
        assertEquals("网络连接失败，请检查网络", (result as UpdateCheck.Failed).message)
        assertNull(result.cached)
    }

    @Test
    fun `unexpected source failure becomes a reported error`() = runBlocking {
        val source = object : ReleaseSource {
            override suspend fun fetchLatest(): Result<UpdateRelease> =
                throw IllegalStateException("boom")
        }
        val result = UpdateChecker(source, FakeStore(), "1.0.0") { 1_000L }.check(force = true)
        assertTrue(result is UpdateCheck.Failed)
        assertEquals("boom", (result as UpdateCheck.Failed).message)
    }

    @Test
    fun `cachedNewer exposes the cached release for instant display`() = runBlocking {
        val store = FakeStore()
        UpdateChecker(FakeSource(release("1.1.0")), store, "1.0.0") { 1_000L }.check(force = true)
        val checker = UpdateChecker(FakeSource(null), store, "1.0.0") { 1_000L }
        assertEquals("1.1.0", checker.cachedNewer()?.version)
    }

    @Test
    fun `cachedNewer ignores releases that are not newer`() = runBlocking {
        val store = FakeStore()
        UpdateChecker(FakeSource(release("1.0.0")), store, "1.0.0") { 1_000L }.check(force = true)
        val checker = UpdateChecker(FakeSource(null), store, "1.0.0") { 1_000L }
        assertNull(checker.cachedNewer())
    }

    @Test
    fun `http errors get distinct messages`() {
        assertEquals("GitHub 暂时限制了请求，请稍后再试", updateHttpMessage(403))
        assertEquals("GitHub 暂时限制了请求，请稍后再试", updateHttpMessage(429))
        assertEquals("GitHub 返回异常（HTTP 500）", updateHttpMessage(500))
    }
}
