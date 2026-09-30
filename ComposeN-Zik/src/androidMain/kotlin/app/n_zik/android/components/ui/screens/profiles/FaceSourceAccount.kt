package app.n_zik.android.components.ui.screens.profiles

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import app.n_zik.android.utils.FACE_SOURCE_DISCORD
import app.n_zik.android.utils.FACE_SOURCE_LASTFM
import app.n_zik.android.utils.FACE_SOURCE_PROFILE
import app.n_zik.android.utils.FACE_SOURCE_YOUTUBE
import it.fast4x.innertube.utils.parseCookieString

/**
 * Login state of a profile's accounts (one-shot read, per-profile secure prefs),
 * plus the name and avatar captured by each account's login — the face preview
 * resolves the account sources against them. Shared by the settings face card
 * and the onboarding profile step: both lock the face-source options with the
 * same rule (an account source is locked while that account is not logged in).
 */
data class AccountLoginState(
    val youtube: Boolean,
    val ytName: String,
    val ytAvatar: String,
    val discord: Boolean,
    val discordName: String,
    val discordAvatar: String,
    val lastfm: Boolean,
    val lastfmName: String,
    val lastfmAvatar: String,
)

/**
 * One-shot read of a profile's account login state and captured face identity.
 * Every chunk is guarded — a failing read just locks its source and blanks its
 * name/avatar, the profile source is never locked.
 */
fun loadAccountLoginState(app: Context, profileId: String): AccountLoginState {
    val secure = runCatching { profileSecurePrefs(app, profileId) }.getOrNull()
    return AccountLoginState(
        youtube = runCatching {
            parseCookieString(secure?.getString(ytCookieKey, "") ?: "").contains("SAPISID")
        }.getOrDefault(false),
        ytName = runCatching { secure?.getString(ytAccountNameKey, "") }.getOrNull()?.trim().orEmpty(),
        ytAvatar = runCatching { secure?.getString(ytAccountThumbnailKey, "") }
            .getOrNull()?.takeIf { it.isNotBlank() }.orEmpty(),
        discord = runCatching {
            secure?.getString(discordPersonalAccessTokenKey, "")?.isNotBlank() == true
        }.getOrDefault(false),
        discordName = runCatching { secure?.getString(discordUsernameKey, "") }.getOrNull()?.trim().orEmpty(),
        discordAvatar = runCatching { secure?.getString(discordAvatarKey, "") }
            .getOrNull()?.takeIf { it.isNotBlank() }.orEmpty(),
        lastfm = runCatching {
            secure?.getString(lastfmSessionKey, "")?.isNotBlank() == true
        }.getOrDefault(false),
        lastfmName = runCatching { secure?.getString(lastfmUsernameKey, "") }.getOrNull()?.trim().orEmpty(),
        lastfmAvatar = runCatching { secure?.getString(lastfmAvatarUrlKey, "") }
            .getOrNull()?.takeIf { it.isNotBlank() }.orEmpty(),
    )
}

/** Label shown for a face source (onboarding source rows + the "source" entries). */
@Composable
fun faceSourceLabel(source: String): String = when (source) {
    FACE_SOURCE_PROFILE -> stringResource(R.string.face_source_profile)
    FACE_SOURCE_YOUTUBE -> stringResource(R.string.display_name_source_youtube)
    FACE_SOURCE_DISCORD -> stringResource(R.string.face_source_discord)
    FACE_SOURCE_LASTFM -> stringResource(R.string.face_source_lastfm)
    else -> stringResource(R.string.face_source_profile)
}

/** Icon of a face source entry. */
fun faceSourceIcon(source: String): Int = when (source) {
    FACE_SOURCE_PROFILE -> R.drawable.person
    FACE_SOURCE_YOUTUBE -> R.drawable.logo_youtube
    FACE_SOURCE_DISCORD -> R.drawable.logo_discord
    FACE_SOURCE_LASTFM -> R.drawable.logo_lastfm
    else -> R.drawable.person
}
