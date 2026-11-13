package app.n_zik.android.core.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.PROFILE_NAMES_FILE_NAME
import app.n_zik.android.utils.currentProfileEntries
import app.n_zik.android.utils.isProfileIdSafe
import app.n_zik.android.utils.parseProfileEntries
import app.n_zik.android.utils.profileDisplayName
import app.n_zik.android.utils.readProfileIds
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.profileDisplayNameKey
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.profileShareKey
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.components.ui.screens.profiles.profileAvatarFile
import app.n_zik.android.components.ui.screens.profiles.profileFaceUpdateTrigger
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.UUID

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
    internal const val PENDING_NAMES_FILE = "profile-state-names.pending"
    private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private const val MAX_IMAGE_SIDE = 4096
    private const val MAX_ENTRIES = 256
    private const val MAX_FIELD_LENGTH = 1024

    /** Face line prefix: `__face__<id><TAB><base64 JPEG>`. */
    const val FACE_LINE_PREFIX = "__face__"

    /** Name line prefix: `__name__<id><TAB><display name>` (only the base is ever exported). */
    const val NAME_LINE_PREFIX = "__name__"

    /** The readable content of an archive: the validated list, the faces (profile id to bytes) and the stored display names (profile id to name — in practice only the base). */
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

    /** True when there is anything to archive: a list file, at least one face, or a renamed base profile (its custom name lives in the prefs store, never in the list file). */
    fun hasState(context: Context): Boolean {
        if (File(context.filesDir, PROFILE_NAMES_FILE_NAME).exists()) return true
        if (context.profileDisplayName(DEFAULT_PROFILE_ID)?.isNotBlank() == true) return true
        return faceCandidateIds(context).any { profileAvatarFile(context, it).exists() }
    }

    /** Writes [context]'s current profile state into [out] as a single text file: the list lines first, then one face line per profile that has a custom face, then one name line for the base profile when it was renamed (it is never listed, so its custom name would otherwise be lost by an export). */
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
        val text = readBoundedText(inStream)
        val faces = LinkedHashMap<String, ByteArray>()
        val names = LinkedHashMap<String, String>()
        val listLines = mutableListOf<String>()
        text.lineSequence().forEachIndexed { index, line ->
            require(index < MAX_ENTRIES * 3) { "Too many entries in the profile state archive" }
            if (line.startsWith(NAME_LINE_PREFIX)) {
                val rest = line.removePrefix(NAME_LINE_PREFIX)
                val tab = rest.indexOf('\t')
                if (tab <= 0) {
                    Timber.tag(TAG).w("Dropping malformed profile name line")
                    return@forEachIndexed
                }
                val id = rest.substring(0, tab).trim()
                val name = rest.substring(tab + 1).trim()
                if (validId(id) && name.isNotEmpty() && name.length <= MAX_FIELD_LENGTH) {
                    names[id] = name
                } else {
                    Timber.tag(TAG).w("Dropping name line for %s (unsafe id or blank name)", id)
                }
            } else if (line.startsWith(FACE_LINE_PREFIX)) {
                val rest = line.removePrefix(FACE_LINE_PREFIX)
                val tab = rest.indexOf('\t')
                if (tab <= 0) {
                    Timber.tag(TAG).w("Dropping malformed profile face line")
                    return@forEachIndexed
                }
                val id = rest.substring(0, tab)
                val payload = rest.substring(tab + 1)
                require(payload.length <= (MAX_IMAGE_BYTES + 2) / 3 * 4) { "Profile avatar data is too large" }
                val bytes = try {
                    Base64.getDecoder().decode(payload)
                } catch (e: IllegalArgumentException) {
                    Timber.tag(TAG).w("Dropping face line for %s (payload is not base64)", id)
                    return@forEachIndexed
                }
                if (validId(id) && isValidAvatar(bytes)) {
                    faces[id] = bytes
                } else {
                    Timber.tag(TAG).w("Dropping face line for %s (unsafe id or not a recognized image)", id)
                }
            } else {
                require(line.length <= MAX_FIELD_LENGTH * 2 + 1) { "Profile state field is too long" }
                listLines.add(line)
            }
        }
        val entries = parseProfileEntries(listLines.joinToString("\n"))
        require(entries.size <= MAX_ENTRIES && faces.size <= MAX_ENTRIES && names.size <= MAX_ENTRIES)
        return Archive(entries, faces, names)
    }

    /**
     * Applies [archive] to [context]: the list is written atomically, the stored display names
     * (both the list lines' names and the base's own name line) are restored, and every face
     * is materialized at `profiles/<id>/avatar.jpg` — a face or a name whose id is neither
     * listed nor the base profile is dropped (it would belong to a profile that does not exist
     * on this device).
     *
     * The base profile is implicit (never a list line) but always restorable, so a base-only
     * export — an empty list carrying the base face and/or name — is valid: it restores them
     * WITHOUT touching the device's existing profile list (only a non-empty list replaces it).
     *
     * @return the restored list size, the faces actually written and the names restored.
     * @throws IllegalArgumentException when the archive holds no profile at all (no list line,
     *   no face, no name) — there is nothing to restore.
     */
    fun apply(context: Context, archive: Archive): ApplyResult =
        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            require(archive.entries.isNotEmpty() || archive.faces.isNotEmpty() || archive.names.isNotEmpty()) {
                "Profile state archive contains no valid profile"
            }
            require(archive.entries.size <= MAX_ENTRIES && archive.faces.size <= MAX_ENTRIES && archive.names.size <= MAX_ENTRIES)
            require(archive.entries.all { (id, name) ->
                validId(id) && id != DEFAULT_PROFILE_ID && name.length <= MAX_FIELD_LENGTH && name.none { it == '\n' || it == '\r' }
            } && archive.entries.map { it.first }.distinct().size == archive.entries.size)
            val files = context.filesDir.canonicalFile
            DatabaseRestoreJournal.Transaction(files, files, profileState = true).recover()
            resumePendingNames(context)
            RescueFiles.resumeProfileDeletions(context)
            val existing = context.currentProfileEntries().toMap()
            val active = getActiveProfile(context)
            val archiveIds = archive.entries.map { it.first }.toSet()
            val published = (existing + archive.entries.toMap()).toList()
            val restorable = archiveIds + DEFAULT_PROFILE_ID
            val names = archive.entries.toMap() + archive.names.filterKeys { it in restorable }
            require(names.all { (id, name) -> validId(id) && name.length <= MAX_FIELD_LENGTH })
            val faces = archive.faces.filterKeys { it in restorable }
            require(faces.values.all(::isValidAvatar))
            val newIds = archiveIds - existing.keys
            newIds.forEach { RescueFiles.requireUnoccupiedProfileId(context, it) }
            val resetActive = active != DEFAULT_PROFILE_ID && active !in existing && active !in archiveIds

            val stage = files.resolve(".profile-state-prepare-${UUID.randomUUID()}")
            check(stage.mkdir())
            try {
                val updates = linkedMapOf<File, File?>()
                fun stageFile(target: File, write: (FileOutputStream) -> Unit) {
                    check(target == target.canonicalFile)
                    val staged = stage.resolve(updates.size.toString())
                    FileOutputStream(staged).use { stream ->
                        write(stream)
                        stream.fd.sync()
                    }
                    updates[target] = staged
                }
                for ((id, bytes) in faces) {
                    stageFile(files.resolve("profiles/$id/avatar.jpg")) { it.write(bytes) }
                }
                for (id in newIds) {
                    stageFile(files.resolve("profile-storage-generations/$id")) {
                        it.write(UUID.randomUUID().toString().encodeToByteArray())
                    }
                }
                stageFile(files.resolve(PENDING_NAMES_FILE)) { stream ->
                    val data = DataOutputStream(stream)
                    data.writeBoolean(resetActive)
                    data.writeInt(names.size)
                    names.forEach { (id, name) ->
                        data.writeUTF(id)
                        data.writeUTF(name)
                    }
                    data.writeInt(newIds.size)
                    newIds.forEach(data::writeUTF)
                    data.flush()
                }
                if (archive.entries.isNotEmpty()) {
                    stageFile(files.resolve(PROFILE_NAMES_FILE_NAME)) {
                        val text = published.joinToString("\n") { (id, name) -> if (name.isEmpty()) id else "$id\t$name" }
                        it.write(text.encodeToByteArray())
                    }
                }
                DatabaseRestoreJournal.installProfileState(files, updates)
                resumePendingNames(context)
                profileFaceUpdateTrigger++
                ApplyResult(archive.entries.size, faces.size, archive.names.keys.count { it in restorable })
            } finally {
                ProfileResetSnapshot.deleteVerifiedTree(stage)
            }
        }

    internal fun resumePendingNames(context: Context) {
        val marker = context.filesDir.canonicalFile.resolve(PENDING_NAMES_FILE)
        if (ProfileResetSnapshot.fingerprint(marker) == "-") return
        check(marker.isFile && marker.length() <= MAX_ARCHIVE_BYTES)
        val editor = context.profilePreferences.edit()
        DataInputStream(marker.inputStream().buffered()).use { input ->
            if (input.readBoolean()) editor.putString(activeProfileKey, DEFAULT_PROFILE_ID)
            val names = input.readInt()
            check(names in 0..MAX_ENTRIES * 2)
            repeat(names) {
                val id = input.readUTF()
                val name = input.readUTF()
                check(validId(id) && name.length <= MAX_FIELD_LENGTH)
                if (name.isBlank()) editor.remove(profileDisplayNameKey(id))
                else editor.putString(profileDisplayNameKey(id), name)
            }
            val newIds = input.readInt()
            check(newIds in 0..MAX_ENTRIES)
            repeat(newIds) {
                val id = input.readUTF()
                check(validId(id))
                ProfileDataItem.ALL.forEach { editor.remove(profileShareKey(it, id)) }
            }
            check(input.read() == -1)
        }
        check(editor.commit()) { "Could not publish profile names" }
        check(marker.delete()) { "Could not complete profile state restoration" }
    }

    private fun readBoundedText(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            require(read <= MAX_ARCHIVE_BYTES - output.size()) { "Profile state archive is too large" }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun validId(id: String): Boolean =
        id.isNotBlank() && id.length <= MAX_FIELD_LENGTH && isProfileIdSafe(id) &&
            id.none { it == '\r' || it == '\n' || it == '\t' }

    internal fun isValidAvatar(bytes: ByteArray): Boolean {
        if (bytes.size > MAX_IMAGE_BYTES || !isValidImageHeader(bytes)) return false
        if (Build.VERSION.SDK_INT == 0) return true
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_IMAGE_SIDE || bounds.outHeight !in 1..MAX_IMAGE_SIDE) return false
        val sample = BitmapFactory.Options().apply { inSampleSize = 4 }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sample) ?: return false
        decoded.recycle()
        return true
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
