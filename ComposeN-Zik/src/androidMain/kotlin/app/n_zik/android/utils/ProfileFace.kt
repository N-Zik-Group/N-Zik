package app.n_zik.android.utils

/** Face source values stored under `faceNameSource` / `faceAvatarSource`. */
const val FACE_SOURCE_PROFILE = "profil"
const val FACE_SOURCE_YOUTUBE = "youtube"
const val FACE_SOURCE_DISCORD = "discord"
const val FACE_SOURCE_LASTFM = "lastfm"

/** Number of deterministic backgrounds of the initials avatar. */
const val FACE_INITIALS_COLOR_COUNT = 8

/**
 * The resolved face of a profile: the name shown in-app and the avatar to render.
 * The name is never blank; the avatar is a photo (file path or URL) or the
 * deterministic initials generated from the name — the face always renders
 * something (spec decision Q1).
 */
data class ProfileFace(
    val name: String,
    val avatar: FaceAvatar,
)

/** What to render for the face avatar. */
sealed interface FaceAvatar {
    /** A photo: an `http(s)://` URL or a local file path. */
    data class Photo(val source: String) : FaceAvatar

    /** The deterministic initials placeholder generated from [name]. */
    data class Initials(val name: String) : FaceAvatar
}

/**
 * Resolves the face name of a profile.
 *
 * The selected account source wins only while that account is actually logged in AND
 * a name was captured; otherwise the profile's own display name is used, then the
 * app default name — the result is never blank.
 *
 * Pure on purpose — shared by the Welcome greeting, the Rewind deck, the Accounts
 * face card and the Profiles page, and unit-tested without any Android dependency.
 *
 * @param source value stored under `faceNameSource` ("profil" default)
 * @param profileName the profile's own display name (never blank in practice)
 * @param ytLoggedIn whether the YouTube account of this profile is logged in
 * @param ytName account name captured by the login flow (may be blank)
 * @param discordLoggedIn whether the Discord account of this profile is logged in
 * @param discordName Discord username (may be blank)
 * @param lastfmLoggedIn whether the Last.fm account of this profile is logged in
 * @param lastfmName Last.fm username (may be blank)
 * @param defaultName fallback name, never blank (`profile_base_name`)
 */
fun resolveFaceName(
    source: String,
    profileName: String,
    ytLoggedIn: Boolean,
    ytName: String,
    discordLoggedIn: Boolean,
    discordName: String,
    lastfmLoggedIn: Boolean,
    lastfmName: String,
    defaultName: String,
): String = when {
    source == FACE_SOURCE_YOUTUBE && ytLoggedIn && ytName.isNotBlank() -> ytName.trim()
    source == FACE_SOURCE_DISCORD && discordLoggedIn && discordName.isNotBlank() -> discordName.trim()
    source == FACE_SOURCE_LASTFM && lastfmLoggedIn && lastfmName.isNotBlank() -> lastfmName.trim()
    else -> profileName.ifBlank { defaultName }
}

/**
 * Resolves the face avatar of a profile.
 *
 * The selected account source wins only while that account is logged in AND an
 * avatar was captured; otherwise the profile's own photo is used when set, and
 * the deterministic initials of the profile name otherwise (fallback = display
 * name / placeholder, spec boundary "source indisponible").
 */
fun resolveFaceAvatar(
    source: String,
    profileName: String,
    profilePhoto: String?,
    ytLoggedIn: Boolean,
    ytAvatar: String,
    discordLoggedIn: Boolean,
    discordAvatar: String,
    lastfmLoggedIn: Boolean,
    lastfmAvatar: String,
): FaceAvatar = when {
    source == FACE_SOURCE_YOUTUBE && ytLoggedIn && ytAvatar.isNotBlank() -> FaceAvatar.Photo(ytAvatar.trim())
    source == FACE_SOURCE_DISCORD && discordLoggedIn && discordAvatar.isNotBlank() -> FaceAvatar.Photo(discordAvatar.trim())
    source == FACE_SOURCE_LASTFM && lastfmLoggedIn && lastfmAvatar.isNotBlank() -> FaceAvatar.Photo(lastfmAvatar.trim())
    !profilePhoto.isNullOrBlank() -> FaceAvatar.Photo(profilePhoto.trim())
    else -> FaceAvatar.Initials(profileName)
}

/**
 * Deterministic initials for the placeholder avatar: the first letter of each of the
 * first two words, uppercased; a blank name renders the fallback letter so the face
 * always renders something.
 */
fun faceInitials(name: String): String {
    val letters = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
    return if (letters.isEmpty()) "?" else letters.joinToString("")
}

/**
 * Deterministic background color index of the initials avatar: the same name always
 * gets the same color, across cards and restarts (the palette itself is resolved by
 * the rendering composable).
 */
fun faceInitialsColorIndex(name: String): Int {
    val seed = name.trim()
    return if (seed.isEmpty()) 0 else (seed.hashCode() and Int.MAX_VALUE) % FACE_INITIALS_COLOR_COUNT
}

/** One step of the relative "last used" timeline. */
enum class RelativeUnit {
    /** Less than a minute ago (also covers clock skew). */
    JustNow,
    Minutes,
    Hours,
    Days,
    Weeks,
    Months,
    /** Older than a year: a plain date is shown instead. */
    Date,
}

/** A bucketed relative time: [unit] with its [value] (count of units, or the timestamp for [RelativeUnit.Date]). */
data class RelativeTime(
    val unit: RelativeUnit,
    val value: Long,
)

/**
 * Buckets [millis] into a relative time against [nowMillis] (pure, unit-tested).
 * Negative deltas (clock skew) are clamped to [RelativeUnit.JustNow]; the other
 * buckets always carry a value of at least 1.
 */
fun relativeTime(millis: Long, nowMillis: Long): RelativeTime {
    val delta = (nowMillis - millis).coerceAtLeast(0L)
    return when {
        delta < 60_000L -> RelativeTime(RelativeUnit.JustNow, 0)
        delta < 3_600_000L -> RelativeTime(RelativeUnit.Minutes, delta / 60_000L)
        delta < 86_400_000L -> RelativeTime(RelativeUnit.Hours, delta / 3_600_000L)
        delta < 7L * 86_400_000L -> RelativeTime(RelativeUnit.Days, delta / 86_400_000L)
        delta < 30L * 86_400_000L -> RelativeTime(RelativeUnit.Weeks, delta / (7L * 86_400_000L))
        delta < 365L * 86_400_000L -> RelativeTime(RelativeUnit.Months, delta / (30L * 86_400_000L))
        else -> RelativeTime(RelativeUnit.Date, millis)
    }
}
