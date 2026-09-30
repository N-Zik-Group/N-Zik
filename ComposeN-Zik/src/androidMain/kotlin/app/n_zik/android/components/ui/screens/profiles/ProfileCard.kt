package app.n_zik.android.components.ui.screens.profiles

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import java.io.IOException
import java.util.Date
import kotlinx.coroutines.withContext
import timber.log.Timber
import it.fast4x.innertube.utils.parseCookieString
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.faceAvatarSourceKey
import app.it.fast4x.rimusic.utils.faceNameSourceKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.profileLastUsed
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import app.it.fast4x.rimusic.utils.encryptedPreferencesUpdateTrigger
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.maintenance.formatBytes
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.FACE_INITIALS_COLOR_COUNT
import app.n_zik.android.utils.FACE_SOURCE_PROFILE
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.ProfileFace
import app.n_zik.android.utils.RelativeUnit
import app.n_zik.android.utils.faceInitials
import app.n_zik.android.utils.faceInitialsColorIndex
import app.n_zik.android.utils.relativeTime
import app.n_zik.android.utils.resolveFaceAvatar
import app.n_zik.android.utils.resolveFaceName
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Directory of a profile's own files (its custom face photo lives at `<dir>/avatar.jpg`). */
fun profileDataDir(context: Context, profileId: String): File =
    File(context.filesDir, "profiles/${profileId}")

/** The custom face photo file of a profile (`filesDir/profiles/<id>/avatar.jpg`). */
fun profileAvatarFile(context: Context, profileId: String): File =
    File(profileDataDir(context, profileId), "avatar.jpg")

/** Absolute path of the profile's custom photo, null when it was never set. */
fun Context.profileAvatarSource(profileId: String): String? =
    profileAvatarFile(this, profileId).takeIf { it.exists() }?.absolutePath

/** Purges the profile's own files (profile deletion). */
fun deleteProfileAvatar(context: Context, profileId: String) {
    runCatching { profileDataDir(context, profileId).deleteRecursively() }
        .onFailure { Timber.tag("ProfileCard").w(it, "Could not purge the profile files of %s", profileId) }
}

/**
 * Removes only the profile's custom photo file (the face card's "Remove photo") —
 * never the whole files dir, which belongs exclusively to the profile-deletion purge.
 */
fun removeProfileAvatarFile(context: Context, profileId: String) {
    val removed = runCatching { profileAvatarFile(context, profileId).delete() }
        .onFailure { Timber.tag("ProfileCard").w(it, "Could not remove the profile photo of %s", profileId) }
    // A deleted photo changes the face: let the header re-resolve it.
    if (removed.getOrNull() == true) profileFaceUpdateTrigger++
}

/**
 * Bumped whenever the active profile's face can change (a photo is saved or removed, the
 * name or the avatar source is switched, faces are restored by an import) so the
 * always-composed app header re-resolves it — the header never leaves composition, so a
 * profile switch or one of these edits is the only moment it refreshes.
 */
var profileFaceUpdateTrigger by mutableIntStateOf(0)

/** Longest side of a re-encoded face photo (px) — the face renders at 32-64dp. */
const val AVATAR_MAX_SIDE_PX = 512

/** JPEG quality of the re-encoded face photo. */
const val AVATAR_JPEG_QUALITY = 85

/**
 * Saves the picked gallery image as the profile's face: the pick is decoded, downsized to at
 * most [AVATAR_MAX_SIDE_PX] on its longest side and re-encoded as a JPEG at
 * [AVATAR_JPEG_QUALITY] before being written to `profiles/<id>/avatar.jpg` — the face travels
 * base64 inside the profile state .txt export, so a raw multi-megabyte pick (often a PNG)
 * would bloat it; the re-encode keeps the file small and in a uniform format. Disk + decode
 * IO — run on the DATA dispatcher. Returns false when the pick could not be read, decoded or
 * written.
 */
suspend fun saveProfileAvatarFromUri(context: Context, profileId: String, uri: Uri): Boolean =
    runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot open the picked image")
        val source = decodeLimitedBitmap(bytes, AVATAR_MAX_SIDE_PX)
            ?: throw IOException("Cannot decode the picked image")
        try {
            val file = profileAvatarFile(context, profileId)
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                source.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, output)
            }
            // A new photo changes the face: let the header re-resolve it.
            profileFaceUpdateTrigger++
        } finally {
            source.recycle()
        }
    }.onFailure { Timber.tag("ProfileCard").e(it, "Could not save the profile avatar of %s", profileId) }
        .isSuccess

/**
 * Decodes [bytes] into a bitmap whose longest side is at most [maxSide]: a bounds-only
 * decode picks a power-of-two sample size (cheap for huge picks), then the full decode is
 * exactly scaled when the sampler left a margin. Null when the bytes are not an image.
 */
internal fun decodeLimitedBitmap(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null
    val longest = maxOf(width, height)
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= maxSide) sampleSize *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    ) ?: return null
    if (decoded.width <= maxSide && decoded.height <= maxSide) return decoded
    val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1),
        true
    ) ?: return decoded
    decoded.recycle()
    return scaled
}

/** Plain prefs of a profile: `preferences` for the base, `preferences_<id>` otherwise. */
internal fun plainProfilePrefs(context: Context, profileId: String): SharedPreferences =
    context.getSharedPreferences(
        if (profileId == DEFAULT_PROFILE_ID) "preferences" else "preferences_$profileId",
        Context.MODE_PRIVATE,
    )

/** The plain prefs file of a profile on disk (`shared_prefs/preferences[_<id>].xml`). */
fun profilePrefsFile(context: Context, profileId: String): File =
    File(context.applicationInfo.dataDir, "shared_prefs/preferences${profileFileSuffix(profileId)}.xml")

/** The prefs file suffix of [profileId] (empty for the base — same rule as `Context.preferences`). */
private fun profileFileSuffix(profileId: String): String =
    if (profileId == DEFAULT_PROFILE_ID) "" else "_$profileId"

/**
 * Sizes in bytes of the shared (app-level) data shown on every card — the same
 * numbers for every profile, since images / song cache / downloads are not
 * profile-scoped yet.
 */
data class ProfileSharedSizes(
    val imagesBytes: Long?,
    val songCacheBytes: Long?,
    val downloadsBytes: Long?,
)

/**
 * Resolves the shared sizes with the same sources as the maintenance sheet:
 * Coil's tracked image cache, the ExoPlayer song-cache dir (walked — null when
 * the disk cache is disabled, it then lives in a temp dir) and the tracked
 * download cache. Disk IO — run on the DATA dispatcher.
 */
fun profileSharedSizes(context: Context): ProfileSharedSizes {
    val app = context.applicationContext
    return ProfileSharedSizes(
        imagesBytes = runCatching { ImageCacheFactory.getCacheSize() }.getOrNull()?.takeIf { it > 0L },
        songCacheBytes = runCatching {
            val base = when (app.preferences.getEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)) {
                ExoPlayerCacheLocation.System -> app.cacheDir
                ExoPlayerCacheLocation.Private -> app.filesDir
            }
            // When the disk cache is disabled the cache lives in a temp dir: the
            // walked app dir is empty, which reads as "No data".
            totalDirectorySize(File(base, PlayerServiceModern.CACHE_DIRNAME))
        }.getOrNull()?.takeIf { it > 0L },
        downloadsBytes = runCatching { MyDownloadHelper.getDownloadCache(app).cacheSpace }
            .getOrNull()?.takeIf { it > 0L },
    )
}

/** Total size in bytes of [dir] (recursive; 0 when it does not exist). */
private fun totalDirectorySize(dir: File): Long {
    val children = dir.listFiles() ?: return 0L
    return children.sumOf { if (it.isDirectory) totalDirectorySize(it) else it.length() }
}

/**
 * Resolves the face of [profileId]: its own face sources (plain profiled prefs), its
 * display name, its own account state (per-profile secure prefs) and its own photo.
 * Every chunk is guarded — a failing read blanks its chunk and the fallbacks keep the
 * face rendering something.
 */
fun loadProfileFace(context: Context, profileId: String, defaultName: String): ProfileFace {
    val app = context.applicationContext
    val prefs = plainProfilePrefs(app, profileId)
    val nameSource = prefs.getString(faceNameSourceKey, FACE_SOURCE_PROFILE) ?: FACE_SOURCE_PROFILE
    val avatarSource = prefs.getString(faceAvatarSourceKey, FACE_SOURCE_PROFILE) ?: FACE_SOURCE_PROFILE

    val profileName = resolveProfileDisplayName(profileId, app.profileDisplayName(profileId), defaultName)

    val secure = runCatching { profileSecurePrefs(app, profileId) }.getOrNull()
    val ytLoggedIn = runCatching {
        secure?.let { parseCookieString(it.getString(ytCookieKey, "") ?: "") }?.contains("SAPISID") == true
    }.getOrDefault(false)
    val ytName = secure?.getString(ytAccountNameKey, "").orEmpty()
    val ytAvatar = secure?.getString(ytAccountThumbnailKey, "")?.takeIf { it.isNotBlank() }.orEmpty()
    val discordLoggedIn = secure?.getString(discordPersonalAccessTokenKey, "")?.isNotBlank() == true
    val discordName = secure?.getString(discordUsernameKey, "").orEmpty()
    val discordAvatar = secure?.getString(discordAvatarKey, "")?.takeIf { it.isNotBlank() }.orEmpty()
    val lastfmLoggedIn = secure?.getString(lastfmSessionKey, "")?.isNotBlank() == true
    val lastfmName = secure?.getString(lastfmUsernameKey, "").orEmpty()
    val lastfmAvatar = secure?.getString(lastfmAvatarUrlKey, "")?.takeIf { it.isNotBlank() }.orEmpty()

    return ProfileFace(
        name = resolveFaceName(
            source = nameSource,
            profileName = profileName,
            ytLoggedIn = ytLoggedIn,
            ytName = ytName,
            discordLoggedIn = discordLoggedIn,
            discordName = discordName,
            lastfmLoggedIn = lastfmLoggedIn,
            lastfmName = lastfmName,
            defaultName = defaultName,
        ),
        avatar = resolveFaceAvatar(
            source = avatarSource,
            profileName = profileName,
            profilePhoto = app.profileAvatarSource(profileId),
            ytLoggedIn = ytLoggedIn,
            ytAvatar = ytAvatar,
            discordLoggedIn = discordLoggedIn,
            discordAvatar = discordAvatar,
            lastfmLoggedIn = lastfmLoggedIn,
            lastfmAvatar = lastfmAvatar,
        ),
    )
}

/** Face of the active profile (Welcome greeting, Rewind deck, Accounts card). */
fun loadActiveProfileFace(context: Context, defaultName: String): ProfileFace =
    loadProfileFace(context, getActiveProfile(context), defaultName)

/**
 * The relative "last used" label of [lastUsed] (epoch millis), null when the profile
 * was never used (the caller shows "Never").
 */
fun lastUsedRelativeText(context: Context, lastUsed: Long?, nowMillis: Long = System.currentTimeMillis()): String? {
    val millis = lastUsed ?: return null
    val relative = relativeTime(millis, nowMillis)
    return when (relative.unit) {
        RelativeUnit.JustNow -> context.getString(R.string.profile_last_used_now)
        RelativeUnit.Minutes -> context.getString(R.string.profile_last_used_minutes, relative.value)
        RelativeUnit.Hours -> context.getString(R.string.profile_last_used_hours, relative.value)
        RelativeUnit.Days -> context.getString(R.string.profile_last_used_days, relative.value)
        RelativeUnit.Weeks -> context.getString(R.string.profile_last_used_weeks, relative.value)
        RelativeUnit.Months -> context.getString(R.string.profile_last_used_months, relative.value)
        RelativeUnit.Date -> DateFormat.getDateFormat(context).format(Date(millis)).toString()
    }
}

/** Deterministic backgrounds of the initials avatar (index by [faceInitialsColorIndex]). */
private val INITIALS_BACKGROUNDS = listOf(
    Color(0xFF7B4FD8),
    Color(0xFF1E88E5),
    Color(0xFF2E9E5B),
    Color(0xFFE8842C),
    Color(0xFFD64550),
    Color(0xFF00ACC1),
    Color(0xFFC0921C),
    Color(0xFF8D6E63),
)

/**
 * The face avatar: a photo (URL or local file) over the deterministic initials, both
 * clipped to the artist roundness ([artistThumbnailShape] — the same shape the artist
 * logo uses). While the photo is loading the app-wide Coil loader shows (the same
 * `R.drawable.loader` placeholder the other thumbnails use); a failing photo load keeps
 * the initials visible, so the face always renders something (spec decision Q1). Every
 * logo of the app renders through this composable, so the treatment applies everywhere
 * it is called; the clickable callers clip to the shape before their `.clickable`,
 * so the click ripple follows the shape (artist roundness in the header, card
 * roundness on the profile cards) instead of flashing a square outline.
 */
@Composable
fun ProfileFaceAvatar(
    avatar: FaceAvatar,
    faceName: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val initialsName = when (avatar) {
        is FaceAvatar.Initials -> avatar.name
        is FaceAvatar.Photo -> faceName
    }
    Box(modifier = modifier.size(size)) {
        // The initials are always rendered underneath: the photo covers them when
        // loaded, and a failing photo load keeps them visible.
        ProfileInitialsFace(initialsName, size)
        if (avatar is FaceAvatar.Photo) {
            val isUrl = avatar.source.startsWith("http://", ignoreCase = true) ||
                avatar.source.startsWith("https://", ignoreCase = true)
            val model: Any = remember(avatar.source) { if (isUrl) avatar.source else File(avatar.source) }
            AsyncImage(
                model = model,
                imageLoader = ImageCacheFactory.LOADER,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.loader),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(artistThumbnailShape()),
            )
        }
    }
}

@Composable
private fun ProfileInitialsFace(name: String, size: Dp) {
    // The index is bounded by FACE_INITIALS_COLOR_COUNT (see faceInitialsColorIndex).
    require(FACE_INITIALS_COLOR_COUNT == INITIALS_BACKGROUNDS.size) {
        "FACE_INITIALS_COLOR_COUNT must match the initials palette size"
    }
    val background = INITIALS_BACKGROUNDS[faceInitialsColorIndex(name)]
    Box(
        modifier = Modifier
            .size(size)
            .clip(artistThumbnailShape())
            .background(background, artistThumbnailShape()),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = faceInitials(name),
            style = typography().s.semiBold.copy(color = Color.White),
        )
    }
}

/**
 * The NZik profile card (Profiles page + Accounts tab): the face (avatar + resolved
 * name), the separate/shared data line, the database size, the last use (relative)
 * and the switch / rename / delete actions (null hides the action; the card click
 * switches when an action is provided).
 */
@Composable
fun ProfileCard(
    face: ProfileFace,
    lastUsed: Long?,
    dbSizeBytes: Long?,
    settingsSizeBytes: Long?,
    imagesSizeBytes: Long?,
    songCacheSizeBytes: Long?,
    downloadsSizeBytes: Long?,
    isActive: Boolean,
    onSwitch: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = colorPalette()
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = 4.dp,
                shape = uiRoundnessShape(),
                spotColor = palette.accent.copy(alpha = 0.2f),
            )
            // The clip sits BEFORE the clickable so the click ripple follows the card
            // roundness (a square outline would otherwise flash at its corners).
            .clip(uiRoundnessShape())
            .clickable(enabled = onSwitch != null) { onSwitch?.invoke() },
        shape = uiRoundnessShape(),
        colors = CardDefaults.cardColors(
            // The same rule as the other NZik cards (SettingsSectionCard): the
            // pitch-black themes need the lighter gray, or the card vanishes on
            // pure black.
            containerColor = if (colorPalette() === PureBlackColorPalette ||
                colorPalette() === ModernBlackColorPalette ||
                colorPaletteMode == ColorPaletteMode.PitchBlack
            ) {
                Color(0xFF1A1A1A) // Gray dark for pitch black themes
            } else {
                colorPalette().background1
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileFaceAvatar(avatar = face.avatar, faceName = face.name, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = face.name,
                        style = typography().s.semiBold.copy(color = palette.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        // The same status line for every profile (the base included): the
                        // profile-scoped data (database, settings, encrypted sessions) is
                        // separate per profile, while the app-level data (downloads, media
                        // cache) stays shared until the opt-in sharing work lands.
                        text = stringResource(R.string.profile_data_status),
                        style = typography().xxs.copy(color = palette.textSecondary),
                    )
                }
                if (isActive) {
                    BasicText(
                        text = stringResource(R.string.profile_active),
                        style = typography().xxs.semiBold.copy(color = palette.accent),
                        modifier = Modifier
                            .clip(uiRoundnessShape())
                            .background(palette.accent.copy(alpha = 0.12f), uiRoundnessShape())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProfileCardInfoRow(
                icon = R.drawable.data_migration,
                label = stringResource(R.string.profile_database),
                value = dbSizeBytes?.let { formatBytes(it) } ?: stringResource(R.string.profile_no_data),
            )
            ProfileCardInfoRow(
                icon = R.drawable.settings,
                label = stringResource(R.string.settings),
                value = settingsSizeBytes?.let { formatBytes(it) } ?: stringResource(R.string.profile_no_data),
            )
            // Shared data (app-level, identical on every card) — the same sources
            // and labels as the maintenance sheet.
            ProfileCardInfoRow(
                icon = R.drawable.image,
                label = stringResource(R.string.maintenance_image_cache),
                value = imagesSizeBytes?.let { formatBytes(it) } ?: stringResource(R.string.profile_no_data),
            )
            // The shared icons match the Settings (data) page entries: image,
            // music_file (song cache) and download.
            ProfileCardInfoRow(
                icon = R.drawable.music_file,
                label = stringResource(R.string.maintenance_song_cache),
                value = songCacheSizeBytes?.let { formatBytes(it) } ?: stringResource(R.string.profile_no_data),
            )
            ProfileCardInfoRow(
                icon = R.drawable.download,
                label = stringResource(R.string.maintenance_downloads),
                value = downloadsSizeBytes?.let { formatBytes(it) } ?: stringResource(R.string.profile_no_data),
            )
            ProfileCardInfoRow(
                icon = R.drawable.history,
                label = stringResource(R.string.profile_last_used),
                value = lastUsedRelativeText(context, lastUsed) ?: stringResource(R.string.profile_never_used),
            )

            if (onRename != null || onDelete != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (onRename != null) {
                        IconButton(onClick = onRename) {
                            Icon(
                                painter = painterResource(R.drawable.pencil),
                                contentDescription = stringResource(R.string.profile_rename),
                                tint = palette.textSecondary,
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                painter = painterResource(R.drawable.trash),
                                contentDescription = stringResource(R.string.remove_profile),
                                tint = Color.Red,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One stat row of the card (the shared settings icon-box pattern: icon + label + value). */
@Composable
private fun ProfileCardInfoRow(icon: Int, label: String, value: String) {
    val palette = colorPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(palette.accent.copy(alpha = 0.1f), uiRoundnessShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                tint = palette.accent,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        BasicText(
            text = label,
            style = typography().xxs.copy(color = palette.textSecondary),
        )
        Spacer(modifier = Modifier.weight(1f))
        BasicText(
            text = value,
            style = typography().xs.copy(color = palette.text),
        )
    }
}

/** One-shot reading of the active profile for the Accounts card. */
private data class ActiveProfileSnapshot(
    val id: String,
    val face: ProfileFace?,
    val lastUsed: Long?,
    val dbSizeBytes: Long?,
    val settingsSizeBytes: Long?,
    val imagesSizeBytes: Long?,
    val songCacheSizeBytes: Long?,
    val downloadsSizeBytes: Long?,
)

/**
 * The profile card of the Accounts tab: the active profile's face, its state
 * (active + separate/shared) and its database size — no actions, the face card right
 * above owns the edit entries. Carries a 16dp trailing margin: the section cards
 * around it (the face card above, the account sections below) each carry a 16dp
 * bottom spacer, without which this card would touch the card below it.
 */
@Composable
fun ProfileAccountCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val defaultName = stringResource(R.string.profile_base_name)
    var snapshot by remember { mutableStateOf<ActiveProfileSnapshot?>(null) }

    // Re-resolve the snapshot whenever the face state changes: a face edit on the
    // face card above (photo pick / remove, source change, rename) bumps
    // [profileFaceUpdateTrigger], and an account login can change a face sourced
    // from that account — the same trigger pair the always-composed header keys on.
    val faceTrigger = encryptedPreferencesUpdateTrigger + profileFaceUpdateTrigger
    LaunchedEffect(faceTrigger) {
        snapshot = withContext(NzikDispatchers.DATA) {
            val id = getActiveProfile(context)
            // The shared sizes are identical for every card — one read for all.
            val shared = profileSharedSizes(context)
            ActiveProfileSnapshot(
                id = id,
                face = runCatching { loadProfileFace(context, id, defaultName) }.getOrNull(),
                lastUsed = runCatching { context.profileLastUsed(id) }.getOrNull(),
                // File.length() returns 0 for a missing file — null renders
                // "No data" instead of a misleading "0 B".
                dbSizeBytes = runCatching {
                    context.getDatabasePath(Database.fileNameForProfile(id)).length().takeIf { it > 0L }
                }.getOrNull(),
                settingsSizeBytes = runCatching { profilePrefsFile(context, id).length().takeIf { it > 0L } }.getOrNull(),
                imagesSizeBytes = shared.imagesBytes,
                songCacheSizeBytes = shared.songCacheBytes,
                downloadsSizeBytes = shared.downloadsBytes,
            )
        }
    }

    val snap = snapshot ?: return
    val fallbackName = resolveProfileDisplayName(snap.id, null, defaultName)
    Column(modifier = modifier) {
        ProfileCard(
            face = snap.face ?: ProfileFace(fallbackName, FaceAvatar.Initials(fallbackName)),
            lastUsed = snap.lastUsed,
            dbSizeBytes = snap.dbSizeBytes,
            settingsSizeBytes = snap.settingsSizeBytes,
            imagesSizeBytes = snap.imagesSizeBytes,
            songCacheSizeBytes = snap.songCacheSizeBytes,
            downloadsSizeBytes = snap.downloadsSizeBytes,
            isActive = true,
            onSwitch = null,
            onRename = null,
            onDelete = null,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
