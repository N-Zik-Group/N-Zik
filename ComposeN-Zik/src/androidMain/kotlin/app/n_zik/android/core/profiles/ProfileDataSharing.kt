package app.n_zik.android.core.profiles

import android.content.Context
import androidx.core.content.edit
import app.it.fast4x.rimusic.utils.profilePreferences
import timber.log.Timber

/**
 * The opt-in sharing flags (spec-profile-data-separation): one boolean per shareable
 * [ProfileDataItem], per profile, stored in the global `profile_preferences` store — the
 * same store as the `displayName_<id>` / `lastUsed_<id>` entries. Plain SharedPreferences
 * only, so the flags are readable from the UI, from the boot path and from the `:rescue`
 * process (no Room, no `Dependencies`) BEFORE a profile's own settings are wiped.
 *
 * The default is SEPARATE: a missing key means the profile keeps its own suffixed data
 * (the sharing is an opt-in per item, user decision 2026-10-01).
 */

/** The key of the sharing flag of [item] for [profileId] in `profile_preferences`. */
fun profileShareKey(item: ProfileDataItem, profileId: String): String =
    "share_${item.shareToken}_$profileId"

/** True when [profileId] shares [item] with the base profile (opt-in — default separate). */
fun Context.profileShares(item: ProfileDataItem, profileId: String): Boolean =
    profilePreferences.getBoolean(profileShareKey(item, profileId), false)

/**
 * Sets the sharing flag of [item] for [profileId]. A `false` flag is REMOVED, not stored:
 * the default (separate) is the absence of the key, so the store only ever carries the
 * opt-ins. The location change applies at the next process boot — the locations are
 * resolved at boot, never swapped hot in process.
 */
fun Context.setProfileShares(item: ProfileDataItem, profileId: String, value: Boolean) {
    runCatching {
        profilePreferences.edit {
            if (value) putBoolean(profileShareKey(item, profileId), true)
            else remove(profileShareKey(item, profileId))
        }
    }.onFailure {
        Timber.tag("ProfileDataSharing").e(it, "Could not store the %s sharing flag of %s", item.shareToken, profileId)
    }
}

/** Forgets the sharing flags of [profileId] (profile deletion, factory reset). */
fun Context.clearProfileShareFlags(profileId: String) {
    profilePreferences.edit {
        ProfileDataItem.ALL.forEach { item -> remove(profileShareKey(item, profileId)) }
    }
}

/** The items [profileId] shares with the base profile (empty = everything separate, the default). */
fun Context.profileSharedItems(profileId: String): Set<ProfileDataItem> =
    ProfileDataItem.ALL.filterTo(mutableSetOf()) { profileShares(it, profileId) }
