package app.fen.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseJsonTest {

    private val full = """
        {
          "tag_name": "v1.1.0",
          "name": "Fen 1.1.0",
          "html_url": "https://github.com/Aj002Th/fen/releases/tag/v1.1.0",
          "body": "### 更新\n- 支持应用内更新",
          "assets": [
            {"name": "Fen-1.1.0.apk", "browser_download_url": "https://x/Fen-1.1.0.apk",
             "size": 123456, "digest": "sha256:ABC123"},
            {"name": "Fen-1.1.0.apk.sha256", "browser_download_url": "https://x/Fen-1.1.0.apk.sha256",
             "size": 80}
          ]
        }
    """.trimIndent()

    @Test
    fun `parses version notes assets and digest`() {
        val release = ReleaseJson.parse(full)!!
        assertEquals("v1.1.0", release.tag)
        assertEquals("1.1.0", release.version)
        assertEquals("https://x/Fen-1.1.0.apk", release.apk.url)
        assertEquals(123456L, release.apk.size)
        assertEquals("abc123", release.apk.digestSha256)
        assertEquals("https://x/Fen-1.1.0.apk.sha256", release.sha256FileUrl)
        assertTrue(release.notes.contains("支持应用内更新"))
        assertEquals("https://github.com/Aj002Th/fen/releases/tag/v1.1.0", release.htmlUrl)
    }

    @Test
    fun `falls back to any apk asset when the canonical name is missing`() {
        val json = """
            {"tag_name":"v2.0.0","body":"","html_url":"",
             "assets":[{"name":"fen-release.APK","browser_download_url":"https://x/a.APK","size":1}]}
        """.trimIndent()
        val release = ReleaseJson.parse(json)!!
        assertEquals("https://x/a.APK", release.apk.url)
        assertNull(release.sha256FileUrl)
        assertNull(release.apk.digestSha256)
    }

    @Test
    fun `no apk asset means not installable`() {
        val json = """
            {"tag_name":"v2.0.0","body":"","html_url":"","assets":[
              {"name":"source.zip","browser_download_url":"https://x/s.zip","size":1}]}
        """.trimIndent()
        assertNull(ReleaseJson.parse(json))
    }

    @Test
    fun `missing tag or malformed json returns null`() {
        assertNull(ReleaseJson.parse("""{"assets":[]}"""))
        assertNull(ReleaseJson.parse("not json"))
    }

    @Test
    fun `digest without sha256 prefix is ignored`() {
        val json = """
            {"tag_name":"v2.0.0","body":"","html_url":"","assets":[
              {"name":"Fen-2.0.0.apk","browser_download_url":"https://x/a.apk","size":1,
               "digest":"md5:abc"}]}
        """.trimIndent()
        assertNull(ReleaseJson.parse(json)!!.apk.digestSha256)
    }
}
