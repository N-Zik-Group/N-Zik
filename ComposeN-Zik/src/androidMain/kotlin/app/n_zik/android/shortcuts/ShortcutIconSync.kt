package app.n_zik.android.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.currentProfileEntries
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rescue.RescueActivity
import timber.log.Timber

internal const val GOOGLE_LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"

/**
 * The launcher's cap on a shortcut's short label (25 chars): a longer label makes
 * `setDynamicShortcuts` throw. The framework constant `ShortcutInfo.MAX_SHORTCUT_LABEL_LENGTH`
 * is not exposed on this compile SDK, so the documented value is inlined here.
 */
internal const val MAX_SHORTCUT_SHORT_LABEL_LENGTH = 25

internal const val SHORTCUT_SEARCH_ID = "search"
internal const val SHORTCUT_ALBUMS_ID = "albums"
internal const val SHORTCUT_ARTISTS_ID = "artists"
internal const val SHORTCUT_LIBRARY_ID = "library"
internal const val SHORTCUT_RESCUE_ID = "rescue"
internal const val SHORTCUT_PROFILES_ID = "profiles"

/**
 * Prefix of the per-profile shortcut IDs: `profile_<stable profile ID>` (spec-profile-shortcuts).
 * The ID is stable on purpose — a profile rename only changes the shortcut's label, never its ID.
 */
internal const val PROFILE_SHORTCUT_PREFIX = "profile_"

/** All fixed (non-profile) shortcut IDs. */
internal val ALL_SHORTCUT_IDS =
    listOf(SHORTCUT_SEARCH_ID, SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_LIBRARY_ID, SHORTCUT_PROFILES_ID, SHORTCUT_RESCUE_ID)

/** The per-profile shortcut ID for [profileId] (stable across renames — see [PROFILE_SHORTCUT_PREFIX]). */
internal fun profileShortcutId(profileId: String): String = PROFILE_SHORTCUT_PREFIX + profileId

/** True when [shortcutId] is a per-profile shortcut ID (and not a fixed shortcut). */
internal fun isProfileShortcut(shortcutId: String): Boolean = shortcutId.startsWith(PROFILE_SHORTCUT_PREFIX)

/** The stable profile ID carried by a per-profile shortcut ID, or null when it is not one. */
internal fun profileIdOfShortcut(shortcutId: String): String? =
    if (isProfileShortcut(shortcutId)) {
        shortcutId.removePrefix(PROFILE_SHORTCUT_PREFIX).takeIf { it.isNotEmpty() }
    } else {
        null
    }

/**
 * The shortcut IDs that are valid for [profileEntries] (stable profile id + display name):
 * the fixed set plus one per user profile, plus the base profile's direct shortcut
 * (`profile_default`) — but the base one only when there is at least one user profile (with
 * only the base, the launcher icon already starts it). The base is never in the names file, so
 * it is added explicitly (the defensive filter also guards a corrupt file that could list it).
 * Pure so the base rule is unit-testable without a `Context` (spec-profile-shortcuts).
 */
internal fun dynamicShortcutIds(profileEntries: List<Pair<String, String>>): Set<String> {
    val userProfiles = profileEntries.filter { (id, _) -> id != DEFAULT_PROFILE_ID }
    val userShortcuts = userProfiles.map { (id, _) -> profileShortcutId(id) }.toSet()
    val baseShortcut = if (userProfiles.isEmpty()) emptySet() else setOf(profileShortcutId(DEFAULT_PROFILE_ID))
    return ALL_SHORTCUT_IDS.toSet() + userShortcuts + baseShortcut
}

/**
 * The shortcut IDs that are valid right now, read from the live profile entries. The config
 * CSV may carry arbitrary ids, so the "unknown id" filter (see [parseShortcutConfig]) must
 * know the live profile ids (spec-profile-shortcuts).
 */
internal fun dynamicShortcutIds(context: Context): Set<String> =
    dynamicShortcutIds(context.currentProfileEntries())

/** Maximum number of active shortcuts (launcher display limit). */
internal const val MAX_ACTIVE_SHORTCUTS = 4

/** Default active shortcuts when no preference has been set. */
internal val DEFAULT_ACTIVE_SHORTCUT_IDS =
    listOf(SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_LIBRARY_ID, SHORTCUT_RESCUE_ID)

// Preference keys for shortcut order/enabled state (stored in "preferences")
const val appShortcutsOrderKey = "appShortcutsOrder"
const val appShortcutsEnabledKey = "appShortcutsEnabled"

/**
 * True when the given package is the Google (Pixel) launcher.
 *
 * The Pixel launcher resolves (and caches) shortcut icons with its own configuration, so the
 * theme-adaptive `shortcut_icon` color shows up white there; on that launcher the icons must
 * stay black in both themes.
 */
internal fun isGoogleLauncher(homePackage: String?): Boolean = homePackage == GOOGLE_LAUNCHER_PACKAGE

/**
 * Package name of the default home launcher, or null when it cannot be resolved.
 *
 * Requires a `<queries>` entry for `ACTION_MAIN`/`CATEGORY_HOME` in the manifest (API 30+ package
 * visibility) -- without it this always resolves to null and [registerAppShortcuts] silently
 * falls through to the non-Pixel branch (verified on-device/emulator).
 */
internal fun homeLauncherPackage(packageManager: PackageManager): String? =
    runCatching {
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0
        )
    }.getOrNull()?.activityInfo?.packageName

/**
 * Shortcut order and enabled set, parsed from the two comma-separated preference values.
 *
 * Single source of truth shared by the launcher registration and the settings dialog, so they
 * cannot disagree. Guarantees, whatever the stored strings contain (blank, unknown ids,
 * duplicates, missing ids):
 * - [order] holds every id of [validIds] exactly once, stored order first (a profile shortcut
 *   whose profile was deleted drops out; a new profile's shortcut is appended);
 * - [enabled] always contains [SHORTCUT_RESCUE_ID] (locked), and falls back to
 *   [DEFAULT_ACTIVE_SHORTCUT_IDS] when nothing usable is stored (stored ids of deleted
 *   profiles count as unusable — the fallback keeps the launcher in a sane state).
 */
internal data class ShortcutConfig(val order: List<String>, val enabled: Set<String>)

internal fun parseShortcutConfig(
    orderStr: String?,
    enabledStr: String?,
    validIds: Collection<String> = ALL_SHORTCUT_IDS,
): ShortcutConfig {
    val storedOrder = orderStr.orEmpty().split(",").filter { it in validIds }.distinct()
    val order = storedOrder + validIds.filter { it !in storedOrder }

    val storedEnabled = enabledStr.orEmpty().split(",").filter { it in validIds }
    val enabled = (storedEnabled.ifEmpty { DEFAULT_ACTIVE_SHORTCUT_IDS } + SHORTCUT_RESCUE_ID).toSet()

    return ShortcutConfig(order, enabled)
}

/**
 * The shortcut ids to register, in display order: enabled ones only, at most
 * [MAX_ACTIVE_SHORTCUTS]. Rescue is locked, so when more than the maximum are enabled the other
 * shortcuts give way, never Rescue.
 */
internal fun resolveActiveShortcutIds(
    orderStr: String?,
    enabledStr: String?,
    validIds: Collection<String> = ALL_SHORTCUT_IDS,
): List<String> {
    val (order, enabled) = parseShortcutConfig(orderStr, enabledStr, validIds)
    val active = order.filter { it in enabled }
    if (active.size <= MAX_ACTIVE_SHORTCUTS) return active

    val kept = active.filter { it != SHORTCUT_RESCUE_ID }.take(MAX_ACTIVE_SHORTCUTS - 1).toSet()
    return active.filter { it == SHORTCUT_RESCUE_ID || it in kept }
}

/**
 * Same as above, reading the values from the `"preferences"` file. A value of an unexpected type
 * (corrupt preferences) is treated as absent instead of failing registration. The valid-id set
 * is the dynamic one (fixed shortcuts + the user profiles that exist right now).
 */
internal fun resolveActiveShortcutIds(context: Context): List<String> {
    val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    val orderStr = runCatching { prefs.getString(appShortcutsOrderKey, null) }.getOrNull()
    val enabledStr = runCatching { prefs.getString(appShortcutsEnabledKey, null) }.getOrNull()
    return resolveActiveShortcutIds(orderStr, enabledStr, dynamicShortcutIds(context))
}

/**
 * Registers the active launcher shortcuts as dynamic shortcuts, with an icon appropriate
 * to the active home launcher.
 *
 * The shortcuts are read from user preferences (order + enabled set). Rescue is always
 * included. At most [MAX_ACTIVE_SHORTCUTS] are registered.
 *
 * This is called from [app.n_zik.android.MainApplication.onCreate] early (before
 * `Dependencies.init`) so that the Rescue shortcut exists even if initialization fails.
 */
internal fun registerAppShortcuts(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    val shortcutManager =
        runCatching { context.getSystemService(ShortcutManager::class.java) }.getOrNull() ?: return

    runCatching {
        val activeIds = resolveActiveShortcutIds(context)
        val blackIcons = isGoogleLauncher(homeLauncherPackage(context.packageManager))
        val registered = shortcutManager.setDynamicShortcuts(activeIds.map { buildShortcut(context, it, blackIcons) })
        if (registered) {
            Timber.tag("ShortcutIconSync").i(
                "Registered %d shortcuts (%s)%s",
                activeIds.size,
                activeIds.joinToString(","),
                if (blackIcons) " (Google launcher: black icons)" else ""
            )
        } else {
            // false = rate-limited: this runs on every main-process start, including background ones.
            Timber.tag("ShortcutIconSync").w("Shortcuts not registered (rate limited)")
        }
    }.onFailure {
        Timber.tag("ShortcutIconSync").e(it, "Failed to register shortcuts")
    }
}

/**
 * Label resource, icon drawable, and intent action for a fixed shortcut id.
 *
 * Split out from [buildShortcut] so the id-to-resource mapping is unit-testable without a real
 * `Context` -- resource ids are compile-time constants, so this needs no Robolectric/Android
 * resources at all, unlike [buildShortcut] itself (`context.getString`) or [shortcutIcon]
 * (`ContextCompat.getDrawable`), which do.
 *
 * Per-profile shortcuts are NOT here: their label is the profile's display name (dynamic), so
 * [shortcutSpec] only ever sees the fixed ids (a profile id would throw — by design).
 */
internal fun shortcutSpec(shortcutId: String): Triple<Int, Int, String> = when (shortcutId) {
    SHORTCUT_SEARCH_ID -> Triple(R.string.search, R.drawable.shortcut_search, MainActivity.action_search)
    SHORTCUT_ALBUMS_ID -> Triple(R.string.albums, R.drawable.shortcut_albums, MainActivity.action_albums)
    SHORTCUT_ARTISTS_ID -> Triple(R.string.artists, R.drawable.shortcut_artists, MainActivity.actions_artists)
    SHORTCUT_LIBRARY_ID -> Triple(R.string.playlists, R.drawable.shortcut_library, MainActivity.action_library)
    SHORTCUT_PROFILES_ID -> Triple(R.string.profiles, R.drawable.shortcut_profiles, MainActivity.action_profiles)
    SHORTCUT_RESCUE_ID -> Triple(R.string.rescue_center, R.drawable.shortcut_rescue, ACTION_RESCUE)
    else -> error("Unknown shortcut id $shortcutId")
}

/**
 * The intent action of a shortcut: the fixed action of the static shortcuts,
 * [MainActivity.action_profile] for the per-profile ones.
 */
internal fun shortcutIntentAction(shortcutId: String): String =
    if (isProfileShortcut(shortcutId)) MainActivity.action_profile else shortcutSpec(shortcutId).third

/**
 * The display name labelling a profile's shortcut: the stored name when set, the app default
 * name for the base profile (its ID is an internal value, never shown), the stable ID
 * otherwise. Covers both the user profiles and the base (whose shortcut label is its default
 * name, "N-Zik Fan").
 */
internal fun profileShortcutDisplayName(context: Context, profileId: String): String =
    resolveProfileDisplayName(profileId, context.profileDisplayName(profileId), context.getString(R.string.profile_base_name))

/**
 * The launcher short label of a shortcut: the fixed label of the static shortcuts, the profile
 * display name for the per-profile ones (they share one icon, so the label is what makes them
 * readable). Truncated to [ShortcutInfo.MAX_SHORTCUT_LABEL_LENGTH] — a longer label would make
 * the registration throw.
 */
internal fun shortcutShortLabel(context: Context, shortcutId: String): String =
    if (isProfileShortcut(shortcutId)) {
        profileShortcutDisplayName(context, profileIdOfShortcut(shortcutId).orEmpty())
            .take(MAX_SHORTCUT_SHORT_LABEL_LENGTH)
    } else {
        context.getString(shortcutSpec(shortcutId).first)
    }

/** Intent action for the Rescue Center shortcut. Must match the manifest intent-filter. */
const val ACTION_RESCUE = "app.n_zik.android.action.rescue"

/**
 * The activity a shortcut opens. Rescue MUST open [RescueActivity] (its own process, no app
 * init): pointing it at [MainActivity] would send the user into the very crash it exists for.
 * Split out so this is unit-testable without a `Context`.
 */
internal fun shortcutTargetClass(shortcutId: String): Class<*> =
    if (shortcutId == SHORTCUT_RESCUE_ID) RescueActivity::class.java else MainActivity::class.java

private fun buildShortcut(context: Context, shortcutId: String, blackIcon: Boolean): ShortcutInfo {
    // The per-profile shortcuts share the same icon as the generic « Profiles » one — only the
    // label (the profile display name) distinguishes them (spec-profile-shortcuts).
    val drawableRes = if (isProfileShortcut(shortcutId)) {
        R.drawable.shortcut_profiles
    } else {
        shortcutSpec(shortcutId).second
    }
    val intent = Intent(context, shortcutTargetClass(shortcutId)).setAction(shortcutIntentAction(shortcutId))
    // The direct per-profile shortcut carries the stable profile ID: the app sets it active
    // before launch (cold) or switches to it (warm) — MainActivity consumes the extra one-shot.
    if (isProfileShortcut(shortcutId)) {
        intent.putExtra(MainActivity.EXTRA_PROFILE_ID, profileIdOfShortcut(shortcutId).orEmpty())
    }

    return ShortcutInfo.Builder(context, shortcutId)
        .setShortLabel(shortcutShortLabel(context, shortcutId))
        .setIcon(shortcutIcon(context, drawableRes, blackIcon))
        .setIntent(intent)
        .build()
}

/**
 * Split out from [buildShortcut] so the icon-type choice is testable: `ShortcutInfo` exposes no
 * public getter for the icon it was built with, so a test asserting on the built `ShortcutInfo`
 * directly cannot see which branch ran.
 */
internal fun shortcutIcon(context: Context, drawableRes: Int, blackIcon: Boolean): Icon =
    if (blackIcon) {
        Icon.createWithBitmap(blackShortcutIcon(context, drawableRes))
    } else {
        Icon.createWithResource(context, drawableRes)
    }

private fun blackShortcutIcon(context: Context, drawableRes: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(context, drawableRes)
        ?: error("Missing shortcut drawable $drawableRes")
    val sizePx = (96 * context.resources.displayMetrics.density).toInt()
    return tintedBitmap(drawable, Color.BLACK, sizePx)
}

/**
 * Renders [drawable] tinted with [tint] into a square [sizePx] bitmap.
 *
 * Split out from [blackShortcutIcon] so the tint+render logic is unit-testable with a synthetic
 * `Drawable` -- this module's test source set does not have `includeAndroidResources` enabled
 * (turning it on regressed ~36 unrelated tests elsewhere via a JVM security-provider conflict), so
 * a real drawable resource cannot be resolved from a unit test; this function takes an
 * already-resolved `Drawable` and needs no resource lookup at all.
 */
internal fun tintedBitmap(drawable: Drawable, tint: Int, sizePx: Int): Bitmap {
    val mutable = drawable.mutate()
    DrawableCompat.setTint(mutable, tint)
    mutable.setBounds(0, 0, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    mutable.draw(Canvas(bitmap))
    return bitmap
}
