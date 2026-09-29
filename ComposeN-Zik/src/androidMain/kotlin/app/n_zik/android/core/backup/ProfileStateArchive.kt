package app.n_zik.android.core.backup

import android.content.Context
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import app.it.fast4x.rimusic.utils.currentProfileEntries
import app.it.fast4x.rimusic.utils.isProfileIdSafe
import app.it.fast4x.rimusic.utils.parseProfileEntries
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.readProfileIds
import app.it.fast4x.rimusic.utils.saveProfileDisplayName
import app.it.fast4x.rimusic.utils.writeProfileEntries
import app.n_zik.android.components.ui.screens.profiles.profileAvatarFile
import app.n_zik.android.components.ui.screens.profiles.profileFaceUpdateTrigger
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

/**
 * The combined profile state archive (spec-profiles-page-face, import/export adaptation): a
 * single text file holding the profile list (one `id<TAB>display name` line per profile, a bare
 * `id` when it was never renamed), for every profile that has a custom face one face line
 * `__face__<id><TAB><base64 JPEG>` and, for the base profile when it was renamed, one name line
 * `__name__default<TAB><display name>` (the base is never a list line, so its custom name would
 * otherwise be lost by an export). A profile without a face has no face line — a list-only
 * file (what earlier versions exported) is a valid archive, so its import keeps working.
 */
object ProfileStateArchive {

    private const val TAG = "ProfileStateArchive"

    /** Face line prefix: `__face__<id><TAB><base64 JPEG>`. */
    const val FACE_LINE_PREFIX = "__face__"

    /** Name line prefix: `__name__<id><TAB><display name>` (only the base is ever exported). */
    const val NAME_LINE_PREFIX = "__name__"

    /**
     * The readable content of an archive: the validated list, the faces (profile id to bytes)
     * and the stored display names (profile id to name — in practice only the base).
     */
    data class Archive(
        val entries: List<Pair<String, String>>,
        val faces: Map<String, ByteArray>,
        val names: Map<String, String> = emptyMap()
    )

    /** The outcome of [apply]: the restored list size, the faces written and the names restored. */
    data class ApplyResult(
        val profiles: Int,
        val faces: Int,
        val names: Int = 0
    )

    /**
     * True when there is anything to archive: a list file, at least one face, or a renamed
     * base profile (its custom name lives in the prefs store, never in the list file).
     */
    fun hasState(context: Context): Boolean {
        if (File(context.filesDir, PROFILE_NAMES_FILE_NAME).exists()) return true
        if (context.profileDisplayName(DEFAULT_PROFILE_ID)?.isNotBlank() == true) return true
        return faceCandidateIds(context).any { profileAvatarFile(context, it).exists() }
    }

    /**
     * Writes [context]'s current profile state into [out] as a single text file: the list
     * lines first, then one face line per profile that has a custom face, then one name line
     * for the base profile when it was renamed (it is never listed, so its custom name would
     * otherwise be lost by an export).
     */
    fun writeArchive(context: Context, out: OutputStream) {
        val entries = context.currentProfileEntries()
        val lines = StringBuilder()
        entries.forEach { (id, name) ->
            lines.append(id)
            if (name.isNotEmpty()) lines.append('\t').append(name)
            lines.append('\n')
        }
        var faceCount = 0
        faceCandidateIds(context).forEach { id ->
            val file = profileAvatarFile(context, id)
            if (file.exists()) {
                lines.append(FACE_LINE_PREFIX)
                    .append(id)
                    .append('\t')
                    .append(Base64.getEncoder().encodeToString(file.readBytes()))
                    .append('\n')
                faceCount++
            }
        }
        var nameCount = 0
        context.profileDisplayName(DEFAULT_PROFILE_ID)?.let { baseName ->
            lines.append(NAME_LINE_PREFIX)
                .append(DEFAULT_PROFILE_ID)
                .append('\t')
                .append(baseName)
                .append('\n')
            nameCount++
        }
        out.bufferedWriter().use { it.write(lines.toString()) }
        Timber.tag(TAG).i(
            "Profile state archive written: %d profiles, %d faces, %d names",
            entries.size, faceCount, nameCount
        )
    }

    /**
     * Reads an archive from [inStream]: the list lines are validated by [parseProfileEntries]
     * (an unsafe line is dropped, never kept), a face line is kept only for a safe profile
     * id whose base64 payload decodes to a recognized image and a name line only for a safe
     * profile id with a non-blank name. A file without face or name lines (an export from an
     * earlier version) is a valid list-only archive.
     */
    fun readArchive(inStream: InputStream): Archive {
        val text = inStream.bufferedReader().readText()
        val faces = LinkedHashMap<String, ByteArray>()
        val names = LinkedHashMap<String, String>()
        val listLines = mutableListOf<String>()
        text.lineSequence().forEach { line ->
            if (line.startsWith(NAME_LINE_PREFIX)) {
                val rest = line.removePrefix(NAME_LINE_PREFIX)
                val tab = rest.indexOf('\t')
                if (tab <= 0) {
                    Timber.tag(TAG).w("Dropping malformed name line %s", line)
                    return@forEach
                }
                val id = rest.substring(0, tab).trim()
                val name = rest.substring(tab + 1).trim()
                if (isProfileIdSafe(id) && name.isNotEmpty()) {
                    names[id] = name
                } else {
                    Timber.tag(TAG).w("Dropping name line for %s (unsafe id or blank name)", id)
                }
            } else if (line.startsWith(FACE_LINE_PREFIX)) {
                val rest = line.removePrefix(FACE_LINE_PREFIX)
                val tab = rest.indexOf('\t')
                if (tab <= 0) {
                    Timber.tag(TAG).w("Dropping malformed face line %s", line)
                    return@forEach
                }
                val id = rest.substring(0, tab)
                val payload = rest.substring(tab + 1)
                val bytes = try {
                    Base64.getDecoder().decode(payload)
                } catch (e: IllegalArgumentException) {
                    Timber.tag(TAG).w("Dropping face line for %s (payload is not base64)", id)
                    return@forEach
                }
                if (isProfileIdSafe(id) && isValidImageHeader(bytes)) {
                    faces[id] = bytes
                } else {
                    Timber.tag(TAG).w("Dropping face line for %s (unsafe id or not a recognized image)", id)
                }
            } else {
                listLines.add(line)
            }
        }
        return Archive(parseProfileEntries(listLines.joinToString("\n")), faces, names)
    }

    /**
     * Applies [archive] to [context]: the list is written atomically, the stored display names
     * (both the list lines' names and the base's own name line) are restored, and every face
     * is materialized at `profiles/<id>/avatar.jpg` — a face or a name whose id is neither
     * listed nor the base profile is dropped (it would belong to a profile that does not exist
     * on this device).
     *
     * The base profile is implicit (never a list line) but always restorable, so a base-only
     * export — an empty list carrying the base face and/or the base name — is a valid archive:
     * it restores the base face and name WITHOUT touching the device's existing profile list
     * (an empty list must not wipe the user's profiles; only a non-empty list replaces it).
     *
     * @return the restored list size, the number of faces actually written and the number of
     *   names restored (a base-only archive restores 0 profiles but its face and/or name).
     * @throws IllegalArgumentException when the archive holds no profile at all (no list line,
     *   no face, no name) — there is nothing to restore.
     */
    fun apply(context: Context, archive: Archive): ApplyResult {
        require(archive.entries.isNotEmpty() || archive.faces.isNotEmpty() || archive.names.isNotEmpty()) {
            "Profile state archive contains no valid profile"
        }
        // An empty list is a base-only export: leave the names file (and the stored names)
        // untouched so the device's existing profiles are preserved.
        if (archive.entries.isNotEmpty()) {
            if (!context.writeProfileEntries(archive.entries)) {
                error("Could not write $PROFILE_NAMES_FILE_NAME")
            }
            archive.entries.forEach { (id, name) ->
                if (name.isNotEmpty()) context.saveProfileDisplayName(id, name)
            }
        }
        val restorable = archive.entries.mapTo(mutableSetOf<String>()) { it.first }.also { it += DEFAULT_PROFILE_ID }
        var facesWritten = 0
        archive.faces.forEach { (id, bytes) ->
            if (id !in restorable) {
                Timber.tag(TAG).w("Dropping face for profile %s (not in the restored list)", id)
                return@forEach
            }
            val faceFile = profileAvatarFile(context, id)
            faceFile.parentFile?.mkdirs()
            faceFile.writeBytes(bytes)
            facesWritten++
        }
        var namesRestored = 0
        archive.names.forEach { (id, name) ->
            if (id !in restorable) {
                Timber.tag(TAG).w("Dropping name for profile %s (not in the restored list)", id)
                return@forEach
            }
            context.saveProfileDisplayName(id, name)
            namesRestored++
        }
        // An import changes the face state (the names feed the initials, the faces feed the
        // photos): let the always-composed header re-resolve the active face.
        profileFaceUpdateTrigger++
        Timber.tag(TAG).i(
            "Profile state applied: %d profiles, %d faces, %d names",
            archive.entries.size, facesWritten, namesRestored
        )
        return ApplyResult(archive.entries.size, facesWritten, namesRestored)
    }

    /**
     * The ids that can carry a face: every profile that exists — the base profile (always,
     * it is implicit) plus the listed profiles. The active profile is always in this set,
     * so the base face is archived whether or not it is the current profile (a switch to
     * another profile must not drop the base's photo from an export).
     */
    private fun faceCandidateIds(context: Context): List<String> =
        (listOf(DEFAULT_PROFILE_ID) + context.readProfileIds()).distinct()

    /**
     * True when [bytes] start with the magic bytes of a real image. Faces saved since the
     * re-encode ([saveProfileAvatarFromUri]) are JPEGs, but a state file written by an earlier
     * version (raw copy of the pick) may carry a PNG or a WebP — accepting the common magic
     * bytes keeps those legacy faces importable instead of dropping them.
     */
    private fun isValidImageHeader(bytes: ByteArray): Boolean =
        isJpegHeader(bytes) || isPngHeader(bytes) || isWebPHeader(bytes) || isGifHeader(bytes)

    /** JPEG: SOI marker `FF D8 FF`. */
    private fun isJpegHeader(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

    /** PNG: signature `89 50 4E 47`. */
    private fun isPngHeader(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()

    /** WebP: `RIFF....WEBP` (the `WEBP` payload tag at offset 8). */
    private fun isWebPHeader(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()

    /** GIF: `GIF`. */
    private fun isGifHeader(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()
}
