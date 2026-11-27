package app.fen.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutSubtitleTest {

    private val release = ReleaseJson.parse(
        """{"tag_name":"v1.1.0","body":"","html_url":"","assets":[
             {"name":"Fen-1.1.0.apk","browser_download_url":"https://x/a.apk","size":1}]}"""
    )!!

    private fun state(phase: UpdatePhase, release: UpdateRelease? = null) =
        UpdateUiState(phase = phase, currentVersion = "1.0.0", release = release)

    @Test
    fun `resting subtitles never repeat the current version`() {
        assertEquals("检查是否有新版本", aboutUpdateSubtitle(state(UpdatePhase.Idle)))
        assertEquals("已是最新版本", aboutUpdateSubtitle(state(UpdatePhase.UpToDate)))
    }

    @Test
    fun `available names the new version`() {
        assertEquals(
            "发现新版本 1.1.0",
            aboutUpdateSubtitle(state(UpdatePhase.Available, release)),
        )
    }

    @Test
    fun `checking and failure keep their own copy`() {
        assertEquals("正在检查…", aboutUpdateSubtitle(state(UpdatePhase.Checking)))
        assertEquals("检查失败，点此重试", aboutUpdateSubtitle(state(UpdatePhase.Error)))
    }

    @Test
    fun `transient phases fall back to the neutral subtitle`() {
        listOf(
            UpdatePhase.Downloading,
            UpdatePhase.Verifying,
            UpdatePhase.Installing,
            UpdatePhase.NeedPermission,
        ).forEach { phase ->
            assertEquals("检查是否有新版本", aboutUpdateSubtitle(state(phase, release)))
        }
    }
}
