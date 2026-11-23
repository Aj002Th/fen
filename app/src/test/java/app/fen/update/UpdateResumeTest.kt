package app.fen.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateResumeTest {

    @Test
    fun `returning from the permission page continues the install`() {
        assertEquals(
            ResumeAction.ResumeInstall,
            resumeAction(UpdatePhase.NeedPermission, canInstall = true),
        )
    }

    @Test
    fun `returning without permission stays put`() {
        assertEquals(
            ResumeAction.None,
            resumeAction(UpdatePhase.NeedPermission, canInstall = false),
        )
    }

    @Test
    fun `returning from the installer leaves the installing phase`() {
        assertEquals(
            ResumeAction.ResetInstalling,
            resumeAction(UpdatePhase.Installing, canInstall = false),
        )
        assertEquals(
            ResumeAction.ResetInstalling,
            resumeAction(UpdatePhase.Installing, canInstall = true),
        )
    }

    @Test
    fun `ordinary resumes change nothing`() {
        assertEquals(ResumeAction.None, resumeAction(UpdatePhase.Available, canInstall = true))
        assertEquals(ResumeAction.None, resumeAction(UpdatePhase.Idle, canInstall = true))
    }
}
