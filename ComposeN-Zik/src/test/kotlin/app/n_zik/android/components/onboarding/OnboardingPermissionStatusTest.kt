package app.n_zik.android.components.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests [permissionStatus]: the mapping from the runtime permission check to the
 * onboarding card state (spec I/O matrix, PERM_GRANT row).
 */
class OnboardingPermissionStatusTest {

    @Test
    fun grantedMapsToGranted() {
        assertEquals(
            PermissionStatus.GRANTED,
            permissionStatus(isGranted = true, shouldShowRationale = false, hasBeenRequested = true)
        )
    }

    @Test
    fun grantedWinsEvenWhenPreviouslyRequested() {
        assertEquals(
            PermissionStatus.GRANTED,
            permissionStatus(isGranted = true, shouldShowRationale = true, hasBeenRequested = true)
        )
    }

    @Test
    fun neverRequestedMapsToNotRequested() {
        assertEquals(
            PermissionStatus.NOT_REQUESTED,
            permissionStatus(isGranted = false, shouldShowRationale = false, hasBeenRequested = false)
        )
    }

    @Test
    fun deniedWithARemainingRationaleMapsToDenied() {
        assertEquals(
            PermissionStatus.DENIED,
            permissionStatus(isGranted = false, shouldShowRationale = true, hasBeenRequested = true)
        )
    }

    @Test
    fun deniedWithoutARemainingRationaleMapsToPermanentlyDenied() {
        assertEquals(
            PermissionStatus.PERMANENTLY_DENIED,
            permissionStatus(isGranted = false, shouldShowRationale = false, hasBeenRequested = true)
        )
    }
}
