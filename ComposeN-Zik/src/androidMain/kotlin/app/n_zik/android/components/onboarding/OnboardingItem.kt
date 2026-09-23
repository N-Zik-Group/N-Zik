package app.n_zik.android.components.onboarding

/**
 * One onboarding card (RiPlay pattern): a permission the app can use
 * optionally, its current [status] and the action to take on it.
 */
data class OnboardingItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: Int,
    val status: PermissionStatus,
    val onRequest: () -> Unit
)

/**
 * Status of an optional permission, as seen by the onboarding screen.
 *
 * `DENIED` vs `PERMANENTLY_DENIED` cannot be read from
 * `ContextCompat.checkSelfPermission` alone: the system is willing to re-ask
 * only while it still considers a rationale should be shown, so a denial
 * combined with "no rationale left" maps to `PERMANENTLY_DENIED`.
 */
enum class PermissionStatus {
    GRANTED,
    NOT_REQUESTED,
    DENIED,
    PERMANENTLY_DENIED
}

/**
 * Maps a runtime permission check to a [PermissionStatus].
 *
 * Pure on purpose (unit-tested in [app.n_zik.android.components.onboarding]):
 * [isGranted] comes from `ContextCompat.checkSelfPermission`,
 * [shouldShowRationale] from `Activity.shouldShowRequestPermissionRationale` (M+)
 * and [hasBeenRequested] tracks whether the onboarding screen ever launched the
 * system dialog for this permission (the caller persists it across recreation,
 * see `DataStoreUtils.KEY_ONBOARDING_PERMISSIONS_REQUESTED`).
 */
fun permissionStatus(
    isGranted: Boolean,
    shouldShowRationale: Boolean,
    hasBeenRequested: Boolean,
): PermissionStatus = when {
    isGranted -> PermissionStatus.GRANTED
    !hasBeenRequested -> PermissionStatus.NOT_REQUESTED
    shouldShowRationale -> PermissionStatus.DENIED
    else -> PermissionStatus.PERMANENTLY_DENIED
}
