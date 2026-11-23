package app.fen.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBadgeTest {

    private fun state(phase: UpdatePhase) = UpdateUiState(phase = phase, currentVersion = "1.0.0")

    @Test
    fun `available shows the badge`() {
        assertTrue(hasAvailableUpdate(state(UpdatePhase.Available)))
    }

    @Test
    fun `every other phase hides the badge`() {
        listOf(
            UpdatePhase.Idle,
            UpdatePhase.Checking,
            UpdatePhase.UpToDate,
            UpdatePhase.Downloading,
            UpdatePhase.Verifying,
            UpdatePhase.Installing,
            UpdatePhase.NeedPermission,
            UpdatePhase.Error,
        ).forEach { phase ->
            assertFalse("$phase must not show the badge", hasAvailableUpdate(state(phase)))
        }
    }
}
