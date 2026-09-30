package app.n_zik.android.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests the first-launch onboarding step transitions (flow: permissions -> restore ->
 * accounts -> profile). The accounts step precedes the profile step (user decision):
 * the account face sources must be unlockable when the clone's identity is chosen.
 *
 * The restore step can restart the app, so the flow contract under test here is the
 * step sequence itself: the activity persists the current step on every transition and
 * resumes at it after a restart (see `DataStoreUtils.KEY_ONBOARDING_STEP`). A successful
 * restore never completes the flow — the complete flag stays unwritten, so the restart
 * lands back on the persisted step and the user stays inside onboarding. The same holds
 * for a Discord-token restart from the accounts step: it advances to the profile step
 * (persisted) and the restart lands on it.
 */
class OnboardingStepTest {

    @Test
    fun permissionsAdvancesToRestore() {
        assertEquals(OnboardingStep.IMPORT, OnboardingStep.PERMISSIONS.advance())
    }

    @Test
    fun restoreAdvancesToAccounts() {
        assertEquals(OnboardingStep.ACCOUNTS, OnboardingStep.IMPORT.advance())
    }

    @Test
    fun accountsAdvancesToProfile() {
        assertEquals(OnboardingStep.NAME, OnboardingStep.ACCOUNTS.advance())
    }

    @Test
    fun profileCompletesTheFlow() {
        assertNull(OnboardingStep.NAME.advance())
    }

    @Test
    fun fullFlowSequenceIsPermissionsRestoreAccountsProfileApp() {
        var step: OnboardingStep? = OnboardingStep.PERMISSIONS
        val visited = mutableListOf<OnboardingStep?>(step)
        while (step != null) {
            step = step!!.advance()
            visited.add(step)
        }
        assertEquals(
            listOf<OnboardingStep?>(
                OnboardingStep.PERMISSIONS,
                OnboardingStep.IMPORT,
                OnboardingStep.ACCOUNTS,
                OnboardingStep.NAME,
                null
            ),
            visited
        )
    }

    // ── Startup resolution (post-restart / post-crash / post-restore resume) ──

    @Test
    fun completeFlagAlwaysWinsOverThePersistedStep() {
        assertNull(
            OnboardingStep.resolveStartupStep(complete = true, persistedStepName = OnboardingStep.IMPORT.name)
        )
        assertNull(
            OnboardingStep.resolveStartupStep(complete = true, persistedStepName = OnboardingStep.NAME.name)
        )
        assertNull(OnboardingStep.resolveStartupStep(complete = true, persistedStepName = ""))
    }

    @Test
    fun persistedStepResumesTheFlowAfterRestart() {
        OnboardingStep.entries.forEach { step ->
            assertEquals(step, OnboardingStep.resolveStartupStep(complete = false, persistedStepName = step.name))
        }
    }

    @Test
    fun restoreRestartResumesInOnboardingNeverInTheApp() {
        // The regression this guard exists for: a successful restore must not write the
        // complete flag, so whatever step the flow persisted before the restart resumes
        // inside onboarding — never the main app
        assertEquals(
            OnboardingStep.IMPORT,
            OnboardingStep.resolveStartupStep(complete = false, persistedStepName = OnboardingStep.IMPORT.name)
        )
    }

    @Test
    fun blankOrUnknownPersistedStepFallsBackToTheFirstStep() {
        assertEquals(
            OnboardingStep.PERMISSIONS,
            OnboardingStep.resolveStartupStep(complete = false, persistedStepName = "")
        )
        assertEquals(
            OnboardingStep.PERMISSIONS,
            OnboardingStep.resolveStartupStep(complete = false, persistedStepName = "NOT_A_STEP")
        )
    }
}
