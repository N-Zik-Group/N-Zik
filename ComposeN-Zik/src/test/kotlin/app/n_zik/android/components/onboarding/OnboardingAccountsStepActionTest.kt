package app.n_zik.android.components.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests [accountsStepInitialAction]: the accounts step auto-skips only when ALL
 * THREE optional accounts (YouTube, Last.fm, Discord) are already connected on
 * first composition (restored by the import step, or a returning state) — one
 * missing account still shows the step so the user can connect it.
 */
class OnboardingAccountsStepActionTest {

    @Test
    fun allConnectedAutoAdvances() {
        assertEquals(OnboardingAccountsStepAction.AUTO_ADVANCE, accountsStepInitialAction(true, true, true))
    }

    @Test
    fun onlyYouTubeShowsTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(true, false, false))
    }

    @Test
    fun onlyLastFmShowsTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(false, true, false))
    }

    @Test
    fun onlyDiscordShowsTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(false, false, true))
    }

    @Test
    fun youtubeAndLastFmShowTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(true, true, false))
    }

    @Test
    fun youtubeAndDiscordShowTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(true, false, true))
    }

    @Test
    fun lastFmAndDiscordShowTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(false, true, true))
    }

    @Test
    fun noneShowsTheStep() {
        assertEquals(OnboardingAccountsStepAction.SHOW, accountsStepInitialAction(false, false, false))
    }
}
