package app.n_zik.android.components.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests [accountsStepInitialAction]: the accounts step auto-skips only when BOTH
 * optional accounts are already connected on first composition (restored by the
 * import step, or a returning state) — one missing account still shows the step so
 * the user can connect it.
 */
class OnboardingAccountsStepActionTest {

    @Test
    fun bothConnectedAutoAdvances() {
        assertEquals(OnboardingAccountsStepAction.AUTO_ADVANCE, accountsStepInitialAction(true, true))
    }

    @Test
    fun onlyLastFmShowsTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(true, false))
    }

    @Test
    fun onlyDiscordShowsTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(false, true))
    }

    @Test
    fun neitherShowsTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(false, false))
    }
}
