package app.n_zik.android.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests the first-launch onboarding step transitions (flow: permissions -> restore ->
 * name -> accounts).
 *
 * The restore step can restart the app, so the flow contract under test here is the
 * step sequence itself: the activity persists the current step on every transition and
 * resumes at it after a restart (see `DataStoreUtils.KEY_ONBOARDING_STEP`). A successful
 * restore instead completes the flow (flag written, the restart lands directly in the
 * app — see `MainActivity.completeOnboarding`).
 */
class OnboardingStepTest {

    @Test
    fun permissionsAdvancesToRestore() {
        assertEquals(OnboardingStep.IMPORT, OnboardingStep.PERMISSIONS.advance())
    }

    @Test
    fun restoreAdvancesToName() {
        assertEquals(OnboardingStep.NAME, OnboardingStep.IMPORT.advance())
    }

    @Test
    fun nameAdvancesToAccounts() {
        assertEquals(OnboardingStep.ACCOUNTS, OnboardingStep.NAME.advance())
    }

    @Test
    fun accountsCompletesTheFlow() {
        assertNull(OnboardingStep.ACCOUNTS.advance())
    }

    @Test
    fun fullFlowSequenceIsPermissionsRestoreNameAccountsApp() {
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
                OnboardingStep.NAME,
                OnboardingStep.ACCOUNTS,
                null
            ),
            visited
        )
    }
}
