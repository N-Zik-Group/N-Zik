package app.it.fast4x.rimusic.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.File
import java.io.IOException
import timber.log.Timber

const val activeProfileKey = "activeProfile"

/** ID of the base profile: implicit, never listed in the names file. */
const val DEFAULT_PROFILE_ID = "default"

/** File holding one stable profile ID per line (the base profile is never listed). */
const val PROFILE_NAMES_FILE_NAME = "Profiles_names.txt"

val Context.profilePreferences: SharedPreferences
    get() = getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)

fun getActiveProfile(context: Context): String {
    val prefs = context.profilePreferences
    return prefs.getString(activeProfileKey, DEFAULT_PROFILE_ID) ?: DEFAULT_PROFILE_ID
}

fun setActiveProfile(profileName: String, context: Context) {
    val prefs = context.profilePreferences
    prefs.edit { putString(activeProfileKey, profileName).commit() }
}

/**
 * One row of the profiles page: the stable [id] that owns the data files
 * (`data_<id>.db`, `preferences_<id>`, `secure_preferences_<id>` — renaming the
 * display name never touches them), the resolved [displayName] and the last use
 * timestamp in epoch millis (null when it was never recorded).
 */
data class ProfileInfo(
    val id: String,
    val displayName: String,
    val lastUsed: Long?,
)

/** Key of the display name of [profileId] in `profile_preferences`. */
fun profileDisplayNameKey(profileId: String): String = "displayName_$profileId"

/** Key of the last use timestamp (epoch millis) of [profileId] in `profile_preferences`. */
fun profileLastUsedKey(profileId: String): String = "lastUsed_$profileId"

/**
 * Parses the raw content of [PROFILE_NAMES_FILE_NAME] into the user profile entries
 * (stable ID + display name, file format v2): one line per profile, either a bare
 * ID (legacy format) or `id<TAB>name`.
 *
 * Pure on purpose (unit-tested without Android): trims the lines, drops the blank
 * and unsafe IDs, keeps the first occurrence of each ID (file order) and never
 * returns the base [DEFAULT_PROFILE_ID] — the base profile is implicit, not a line
 * of the file.
 */
fun parseProfileEntries(raw: String): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    val seen = mutableSetOf<String>()
    for (line in raw.lineSequence()) {
        val parts = line.trim().split("\t", limit = 2)
        val id = parts.first().trim()
        val name = parts.getOrNull(1)?.trim().orEmpty()
        if (id.isEmpty() || id == DEFAULT_PROFILE_ID || id in seen) continue
        // A corrupted or legacy line ("..", "a/b") must never resolve to a real
        // data directory — dropping it is safer than orphaning its files.
        if (!isProfileIdSafe(id)) continue
        seen += id
        entries += id to name
    }
    return entries
}

/**
 * Parses the raw content of [PROFILE_NAMES_FILE_NAME] into the user profile IDs.
 *
 * Pure on purpose (unit-tested without Android): trims the lines, drops the blank
 * and unsafe ones, keeps the first occurrence of each ID (file order) and never
 * returns the base [DEFAULT_PROFILE_ID] — the base profile is implicit, not a line
 * of the file.
 */
fun parseProfileIds(raw: String): List<String> = parseProfileEntries(raw).map { it.first }

/**
 * Resolves the display name shown for a profile: the stored name when set, the app
 * default name for the base profile (its ID is an internal value, never shown),
 * the ID itself otherwise. Pure (unit-tested without Android).
 */
fun resolveProfileDisplayName(id: String, storedName: String?, defaultName: String): String {
    val trimmed = storedName?.trim().orEmpty()
    return when {
        trimmed.isNotEmpty() -> trimmed
        id == DEFAULT_PROFILE_ID -> defaultName
        else -> id
    }
}

/**
 * True when [name] is safe as a stable profile ID: it becomes part of file names
 * (`data_<id>.db`, `preferences_<id>`, `profiles/<id>/`), so path separators, the
 * traversal sequences ".." and the current-dir "." are rejected (a "." ID would
 * resolve to the `profiles/` root and a purge of it would wipe every profile).
 * IDs starting with "__" are rejected too: the profile-state archive uses `__face__`
 * and `__name__` line prefixes, and such an ID would be misparsed as an archive
 * directive (the profile vanishing from a restored list, a phantom name injected).
 * Pure (unit-tested without Android).
 */
fun isProfileIdSafe(name: String): Boolean =
    name.none { it == '/' || it == '\\' || it == ':' } && name != ".." && name != "." &&
        !name.startsWith("__")

/**
 * Validates a new profile display name (creation and rename share the rule): the
 * trimmed candidate must be non-blank, file-name safe, free of line breaks (the
 * names file, the state archive and the rescue face capture are line-based — a
 * break would split the profile's line, truncating the name and registering a
 * phantom profile out of the spilled part) and not taken by [takenNames]
 * (compared trimmed and case-insensitively, so " Work " and "work" both collide
 * with "Work"). Pure (unit-tested without Android).
 */
fun isProfileNameValid(name: String, takenNames: Collection<String>): Boolean {
    val candidate = name.trim()
    return candidate.isNotBlank() && isProfileIdSafe(candidate) &&
        candidate.none { it == '\n' || it == '\r' } &&
        takenNames.none { it.trim().equals(candidate, ignoreCase = true) }
}

/**
 * The display names already taken by [ids] (the base profile included) plus the
 * reserved [DEFAULT_PROFILE_ID] (a profile named "default" would share the base
 * data files). [displayNameOf] resolves the stored display name of an ID (null
 * when it was never renamed). Pure (unit-tested without Android).
 */
fun takenProfileNames(
    ids: Collection<String>,
    displayNameOf: (String) -> String?,
    defaultName: String,
): Set<String> =
    (listOf(DEFAULT_PROFILE_ID) + ids.toList())
        .mapTo(mutableSetOf()) { resolveProfileDisplayName(it, displayNameOf(it), defaultName) }
        .also { it += DEFAULT_PROFILE_ID }

/** Display name stored for [profileId], null when the profile was never renamed. */
fun Context.profileDisplayName(profileId: String): String? =
    profilePreferences.getString(profileDisplayNameKey(profileId), null)?.trim()?.ifEmpty { null }

/**
 * Renames [profileId]: a single prefs write — the stable ID (and therefore the data
 * files) is left untouched; a blank name clears the stored one, so the profile
 * falls back to its ID (or the base default name for the base profile).
 */
fun Context.saveProfileDisplayName(profileId: String, name: String) {
    profilePreferences.edit {
        if (name.isBlank()) remove(profileDisplayNameKey(profileId))
        else putString(profileDisplayNameKey(profileId), name.trim())
    }
}

/** The display names already taken (for the create/rename validation). */
fun Context.takenProfileNames(ids: Collection<String>, defaultName: String): Set<String> =
    takenProfileNames(ids, { profileDisplayName(it) }, defaultName)

/** Last use of [profileId] in epoch millis; null when it was never recorded. */
fun Context.profileLastUsed(profileId: String): Long? =
    profilePreferences.getLong(profileLastUsedKey(profileId), 0L).takeIf { it > 0L }

/** Records the last use of [profileId] (epoch millis). */
fun Context.saveProfileLastUsed(profileId: String, millis: Long) {
    profilePreferences.edit { putLong(profileLastUsedKey(profileId), millis) }
}

/** Forgets the per-profile face entries of [profileId] (profile deletion). */
fun Context.clearProfileFaceEntries(profileId: String) {
    profilePreferences.edit {
        remove(profileDisplayNameKey(profileId))
        remove(profileLastUsedKey(profileId))
    }
}

/** [ProfileInfo] of [profileId], resolved against the app default name. */
fun Context.profileInfo(profileId: String, defaultName: String): ProfileInfo =
    ProfileInfo(
        id = profileId,
        displayName = resolveProfileDisplayName(profileId, profileDisplayName(profileId), defaultName),
        lastUsed = profileLastUsed(profileId),
    )

/**
 * The profile entries (stable ID + stored display name) read from
 * [PROFILE_NAMES_FILE_NAME] (empty when the file is missing or unreadable).
 */
fun Context.readProfileEntries(): List<Pair<String, String>> =
    runCatching {
        filesDir.resolve(PROFILE_NAMES_FILE_NAME).let { file ->
            if (file.exists()) file.readText() else ""
        }
    }.onFailure {
        Timber.tag("ProfilePreferences").w(it, "Could not read %s; falling back to no profiles", PROFILE_NAMES_FILE_NAME)
    }.getOrNull()?.let { raw -> parseProfileEntries(raw) }.orEmpty()

/** The user profile IDs read from [PROFILE_NAMES_FILE_NAME] (empty when the file is missing or unreadable). */
fun Context.readProfileIds(): List<String> = readProfileEntries().map { it.first }

/**
 * The profile entries the names file should currently carry: the IDs of the file
 * with the display name resolved against the `profile_preferences` store (empty
 * name when it was never renamed) — the file is the backup format, so its copy of
 * the names must mirror the store, not go stale after a rename.
 */
fun Context.currentProfileEntries(): List<Pair<String, String>> =
    readProfileEntries().map { (id, _) -> id to (profileDisplayName(id).orEmpty()) }

/**
 * Writes the user profile entries back to [PROFILE_NAMES_FILE_NAME] — one line per
 * profile: `id<TAB>display name`, a bare `id` when it was never renamed. The write
 * is atomic (temp file + rename) so a crash mid-write cannot truncate the file and
 * orphan the profiles' data; returns false when the write failed.
 */
fun Context.writeProfileEntries(entries: List<Pair<String, String>>): Boolean =
    runCatching {
        val file = filesDir.resolve(PROFILE_NAMES_FILE_NAME)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(entries.joinToString("\n") { (id, name) -> if (name.isEmpty()) id else "$id\t$name" })
        // File.renameTo atomically replaces an existing destination on Android (POSIX
        // rename) but fails when the destination already exists on Windows; fall back
        // to delete-then-rename so the atomic write stays portable across file systems.
        val replaced = temp.renameTo(file) || (file.delete() && temp.renameTo(file))
        if (!replaced) {
            temp.delete()
            throw IOException("Could not replace $PROFILE_NAMES_FILE_NAME")
        }
    }.onFailure {
        Timber.tag("ProfilePreferences").e(it, "Could not write %s", PROFILE_NAMES_FILE_NAME)
    }.isSuccess
