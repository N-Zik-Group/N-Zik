package app.n_zik.android.components.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests [profileStepInitialAction]: the profile step auto-skips when the import step
 * settled the clone's identity — restored profiles (the state file carries the list,
 * whatever the base's own name) or a custom base name restored by the import. A
 * database-only import restores no profile and leaves the base unnamed, so the step
 * is shown to name the base clone.
 */
class OnboardingProfileStepActionTest {

    @Test
    fun noImportAndNoCustomNameShowsTheStep() {
        assertEquals(
            OnboardingProfileStepAction.SHOW,
            profileStepInitialAction(baseHasCustomName = false, importedProfilesPresent = false)
        )
    }

    @Test
    fun aCustomBaseNameAutoAdvances() {
        assertEquals(
            OnboardingProfileStepAction.AUTO_ADVANCE,
            profileStepInitialAction(baseHasCustomName = true, importedProfilesPresent = false)
        )
    }

    @Test
    fun importedProfilesAutoAdvanceEvenWithoutACustomBaseName() {
        assertEquals(
            OnboardingProfileStepAction.AUTO_ADVANCE,
            profileStepInitialAction(baseHasCustomName = false, importedProfilesPresent = true)
        )
    }

    @Test
    fun importedProfilesAndACustomBaseNameAutoAdvance() {
        assertEquals(
            OnboardingProfileStepAction.AUTO_ADVANCE,
            profileStepInitialAction(baseHasCustomName = true, importedProfilesPresent = true)
        )
    }
}
