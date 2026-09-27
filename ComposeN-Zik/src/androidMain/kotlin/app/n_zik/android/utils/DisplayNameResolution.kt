package app.n_zik.android.utils

/**
 * Resolves the display name shown on the app from its [source].
 *
 * The `youtube` source wins only while a YouTube account is actually logged in AND a
 * name was captured by the login flow; otherwise the custom name is used, falling back
 * to [default] so the result is never blank (spec: onboarding + username choice).
 *
 * Pure on purpose — shared by the Rewind deck ViewModel and the Accounts display-name
 * card, and unit-tested without any Android dependency.
 *
 * @param source value stored under [DataStoreUtils.KEY_DISPLAY_NAME_SOURCE]
 * @param ytLoggedIn whether the existing YouTube cookie login is active
 * @param ytName account name captured by the login flow (may be blank)
 * @param customName name chosen by the user (may be blank)
 * @param default fallback name, never blank (`display_name_default`)
 */
fun resolveDisplayName(
    source: String,
    ytLoggedIn: Boolean,
    ytName: String,
    customName: String,
    default: String,
): String =
    if (source == DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE && ytLoggedIn && ytName.isNotBlank()) ytName
    else customName.ifBlank { default }
