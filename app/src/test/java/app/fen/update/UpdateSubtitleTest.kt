package app.fen.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSubtitleTest {

    private val release = ReleaseJson.parse(
        """{"tag_name":"v1.1.0","body":"","html_url":"","assets":[
             {"name":"Fen-1.1.0.apk","browser_download_url":"https://x/a.apk","size":1}]}"""
    )!!

    private fun state(
        phase: UpdatePhase,
        release: UpdateRelease? = null,
    ) = UpdateUiState(phase = phase, currentVersion = "1.0.0", release = release)

    @Test
    fun `idle shows the current version`() {
        assertEquals("当前版本 1.0.0", updateRowSubtitle(state(UpdatePhase.Idle)))
    }

    @Test
    fun `checking is announced`() {
        assertEquals("正在检查…", updateRowSubtitle(state(UpdatePhase.Checking)))
    }

    @Test
    fun `available shows the new version number`() {
        assertEquals(
            "发现新版本 1.1.0",
            updateRowSubtitle(state(UpdatePhase.Available, release)),
        )
    }

    @Test
    fun `failure invites a retry`() {
        assertEquals("检查失败，点此重试", updateRowSubtitle(state(UpdatePhase.Error)))
    }

    @Test
    fun `downloading keeps showing the current version`() {
        assertEquals(
            "当前版本 1.0.0",
            updateRowSubtitle(state(UpdatePhase.Downloading, release)),
        )
    }
}
