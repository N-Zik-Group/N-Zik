package app.n_zik.android.components.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests [nameStepInitialAction]: the name step auto-skips when a YouTube account is
 * already connected on first composition (the restored display-name source is kept
 * as-is; showing the cards would offer a "Continue" that switches the source to custom).
 */
class OnboardingNameStepActionTest {

    @Test
    fun notConnectedShowsTheStep() {
        assertEquals(OnboardingNameStepAction.SHOW, nameStepInitialAction(ytLoggedIn = false))
    }

    @Test
    fun connectedAutoAdvances() {
        assertEquals(OnboardingNameStepAction.AUTO_ADVANCE, nameStepInitialAction(ytLoggedIn = true))
    }
}
