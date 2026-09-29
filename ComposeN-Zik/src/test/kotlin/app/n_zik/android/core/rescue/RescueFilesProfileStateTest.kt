package app.n_zik.android.core.rescue

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.n_zik.android.core.backup.ProfileStateArchive
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Base64

/**
 * Tests the profile-state core of [RescueFiles] (spec-profiles-page-face, import/export
 * adaptation): the archive is a single .txt combining the profile list (id + display name)
 * and a base64 face line per profile that has a custom face. The export writes the list
 * lines plus the face lines; the import restores the names file, the display names and the
 * faces after the JPEG check. A legacy list-only .txt (no face lines) is still importable,
 * and an unsafe / non-base64 / non-JPEG face line is dropped while the list is kept. The
 * context is a fresh mock — these functions do not depend on the rescue target profile, so
 * no [RescueFiles.initialize] is needed here.
 */
class RescueFilesProfileStateTest {

    @TempDir
    lateinit var tmp: File

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private val stateUri = mockk<Uri>()

    // The real profile_preferences store, backed by an in-memory map.
    private val store = mutableMapOf<String, Any?>()

    // The fluent put chain hands the editor back; inside `answers { }` the receiver is
    // the MockKAnswerScope, so the mock is referenced by name, not `this`.
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    init {
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String>()
            editor
        }
        every { editor.remove(any()) } answers {
            store.remove(firstArg<String>())
            editor
        }
    }

    @BeforeEach
    fun setup() {
        filesDir = File(tmp, "files").apply { mkdirs() }
        val prefs = mockk<SharedPreferences> {
            every { edit() } answers { editor }
            // Reads go through the same in-memory map the editor writes to.
            every { getString(any(), any()) } answers { store[firstArg<String>()] as String? }
        }
        resolver = mockk()
        context = mockk()
        every { context.filesDir } returns filesDir
        every { context.contentResolver } returns resolver
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns prefs
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2)
    private val notJpeg = "not a jpeg".toByteArray()

    private fun faceLine(id: String, bytes: ByteArray): String =
        "__face__$id\t${Base64.getEncoder().encodeToString(bytes)}"

    private fun nameLine(id: String, name: String): String =
        "__name__$id\t$name"

    // ──────────────────────────────────────────────────────────────────────
    // Export
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun exportWritesTheListLinesAndTheBase64FaceLines() {
        // The names file mirrors the store (the app keeps both in sync): seed the store
        // too, so the resolved display name is what the export must carry.
        store["displayName_work"] = "Boulot"
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\njane\n")
        File(filesDir, "profiles/work/avatar.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(jpeg)
        }
        val target = File(tmp, "out.txt")
        every { resolver.openOutputStream(stateUri) } returns FileOutputStream(target)

        val result = RescueFiles.exportProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        val lines = target.readText().trim().lines()
        assertEquals(3, lines.size)
        assertEquals("work\tBoulot", lines[0])
        assertEquals("jane", lines[1])
        val face = lines[2]
        assertTrue(face.startsWith("__face__work\t"))
        assertArrayEquals(jpeg, Base64.getDecoder().decode(face.substringAfter('\t')))
    }

    @Test
    fun exportWritesNoFaceLineForProfilesWithoutAFace() {
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\njane\n")
        val target = File(tmp, "out.txt")
        every { resolver.openOutputStream(stateUri) } returns FileOutputStream(target)

        val result = RescueFiles.exportProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        val lines = target.readText().trim().lines()
        assertEquals(2, lines.size, "a list-only export is valid")
        assertFalse(lines.any { it.startsWith("__face__") })
        assertFalse(lines.any { it.startsWith("__name__") })
    }

    @Test
    fun exportWritesTheBaseNameLineWhenTheBaseWasRenamed() {
        // The base profile is never a list line, so its custom display name would be lost
        // by an export unless it gets its own name line.
        store["displayName_default"] = "Moi"
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\n")
        val target = File(tmp, "out.txt")
        every { resolver.openOutputStream(stateUri) } returns FileOutputStream(target)

        val result = RescueFiles.exportProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        val lines = target.readText().trim().lines()
        assertTrue(
            lines.any { it == nameLine("default", "Moi") },
            "the renamed base must carry a __name__ line so its pseudo survives the import"
        )
        assertEquals(2, lines.size, "one list line + the base name line")
    }

    @Test
    fun exportWritesNoNameLineWhenTheBaseWasNeverRenamed() {
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\n")
        val target = File(tmp, "out.txt")
        every { resolver.openOutputStream(stateUri) } returns FileOutputStream(target)

        val result = RescueFiles.exportProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertFalse(target.readText().contains("__name__"), "no base name line when the base was never renamed")
    }

    @Test
    fun theBaseProfileFaceIsExportedEvenWhenAnotherProfileIsActive() {
        // The user is on "work": the base profile is not the active one, but it still
        // has a face that must survive an export (a profile switch must not drop it).
        store["activeProfile"] = "work"
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\n")
        File(filesDir, "profiles/default/avatar.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(jpeg)
        }
        File(filesDir, "profiles/work/avatar.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(jpeg)
        }
        val target = File(tmp, "out.txt")
        every { resolver.openOutputStream(stateUri) } returns FileOutputStream(target)

        val result = RescueFiles.exportProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        val lines = target.readText().trim().lines()
        // One list line (the base is implicit) + one face line for the base and work
        assertEquals(3, lines.size)
        val faceLines = lines.filter { it.startsWith("__face__") }
        assertEquals(2, faceLines.size)
        assertTrue(
            faceLines.any { it.startsWith("__face__default\t") },
            "the base face must be exported even when another profile is active"
        )
        assertTrue(faceLines.any { it.startsWith("__face__work\t") })
    }

    @Test
    fun exportFailsWhenThereIsNoState() {
        val result = RescueFiles.exportProfileState(context, stateUri)

        assertTrue(result.isFailure)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Import
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun importRestoresTheNamesFileTheDisplayNamesAndTheFaces() {
        val source = File(tmp, "in.txt").apply {
            writeText("work\tBoulot\njane\n${faceLine("work", jpeg)}\n${faceLine("jane", jpeg)}\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.profiles)
        assertEquals(2, result.getOrNull()?.faces, "both listed faces are written")
        assertEquals("work\tBoulot\njane", File(filesDir, "Profiles_names.txt").readText())
        assertEquals("Boulot", store["displayName_work"])
        assertNull(store["displayName_jane"], "an id without a name must not create a stored name")
        assertArrayEquals(jpeg, File(filesDir, "profiles/work/avatar.jpg").readBytes())
        assertArrayEquals(jpeg, File(filesDir, "profiles/jane/avatar.jpg").readBytes())
    }

    @Test
    fun aLegacyListOnlyTxtRestoresTheListWithoutFaces() {
        val source = File(tmp, "in.txt").apply { writeText("work\tBoulot\njane\n") }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.profiles)
        assertEquals(0, result.getOrNull()?.faces, "a list-only archive carries no faces")
        assertEquals("work\tBoulot\njane", File(filesDir, "Profiles_names.txt").readText())
        assertFalse(File(filesDir, "profiles/work/avatar.jpg").exists())
    }

    @Test
    fun anEmptyFileFailsWithoutWriting() {
        every { resolver.openInputStream(stateUri) } returns ByteArrayInputStream(ByteArray(0))

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isFailure)
        assertFalse(File(filesDir, "Profiles_names.txt").exists())
    }

    @Test
    fun aFileWithOnlyUnsafeLinesFails() {
        every { resolver.openInputStream(stateUri) } returns ByteArrayInputStream("a/b\tName\n".toByteArray())

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isFailure)
        assertFalse(File(filesDir, "Profiles_names.txt").exists())
    }

    @Test
    fun anUnsafeFaceLineIsDroppedButTheListIsKept() {
        val source = File(tmp, "in.txt").apply {
            writeText("work\tBoulot\n${faceLine("a/b", jpeg)}\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.profiles)
        assertEquals(0, result.getOrNull()?.faces, "the unsafe face line is dropped")
        assertFalse(File(filesDir, "profiles/work/avatar.jpg").exists())
    }

    @Test
    fun aNonJpegFaceLineIsDroppedButTheListIsKept() {
        val source = File(tmp, "in.txt").apply {
            writeText("work\tBoulot\n${faceLine("work", notJpeg)}\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.profiles)
        assertEquals(0, result.getOrNull()?.faces, "the non-image face line is dropped")
        assertFalse(File(filesDir, "profiles/work/avatar.jpg").exists())
    }

    @Test
    fun aNonJpegFaceIsKeptWhenItIsARealImage() {
        // The face is a raw copy of the picked gallery image, so it may be a PNG (not a
        // JPEG) — the import must accept it, not drop it (the base face was lost to this).
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)
        val source = File(tmp, "in.txt").apply {
            writeText("${faceLine("default", png)}\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess, "a base-only archive with a PNG face is a valid state")
        assertEquals(0, result.getOrNull()?.profiles, "a base-only archive lists no profile")
        assertEquals(1, result.getOrNull()?.faces, "the base face is counted (not a 0-import toast)")
        assertArrayEquals(png, File(filesDir, "profiles/default/avatar.jpg").readBytes())
    }

    @Test
    fun aNonBase64FaceLineIsDroppedButTheListIsKept() {
        val source = File(tmp, "in.txt").apply {
            writeText("work\tBoulot\n__face__work\t!!!not-base64!!!\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.profiles)
        assertEquals(0, result.getOrNull()?.faces, "the non-base64 face line is dropped")
        assertFalse(File(filesDir, "profiles/work/avatar.jpg").exists())
    }

    @Test
    fun anOrphanFaceIsDroppedButTheBaseProfileFaceIsKept() {
        val source = File(tmp, "in.txt").apply {
            writeText("work\tBoulot\n${faceLine("ghost", jpeg)}\n${faceLine("default", jpeg)}\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.profiles)
        assertEquals(1, result.getOrNull()?.faces, "the orphan face is dropped, the base face is written")
        assertFalse(File(filesDir, "profiles/ghost/avatar.jpg").exists())
        assertArrayEquals(jpeg, File(filesDir, "profiles/default/avatar.jpg").readBytes())
    }

    @Test
    fun aMissingStreamFails() {
        every { resolver.openInputStream(stateUri) } returns null

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isFailure)
    }

    @Test
    fun aBaseOnlyArchiveRestoresTheBaseFaceWithoutWipingExistingProfiles() {
        // The device already has a user profile. The imported archive is base-only:
        // no list line (the base is implicit) but it carries the base face. Re-importing
        // it must restore the base face without wiping the existing profile list.
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\n")
        val source = File(tmp, "in.txt").apply { writeText("${faceLine("default", jpeg)}\n") }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess, "a base-only archive (empty list + base face) is a valid state")
        // The existing user profile is preserved — an empty list must not wipe it
        assertEquals("work\tBoulot", File(filesDir, "Profiles_names.txt").readText().trim())
        // The base face is restored — and the counts feed the toast: 0 profiles + 1 face
        // selects the dedicated "base profile" message instead of "0 profiles".
        assertEquals(0, result.getOrNull()?.profiles)
        assertEquals(1, result.getOrNull()?.faces)
        assertArrayEquals(jpeg, File(filesDir, "profiles/default/avatar.jpg").readBytes())
    }

    @Test
    fun aBaseOnlyArchiveRestoresTheBaseCustomName() {
        // The user renamed the base profile ("Moi") on another device. The archive is
        // base-only: no list line, no face — only the base name line. Re-importing it must
        // restore the custom pseudo without touching the device's existing profiles.
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\n")
        val source = File(tmp, "in.txt").apply { writeText("${nameLine("default", "Moi")}\n") }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess, "a name-only base archive (no list, no face) is a valid state")
        assertEquals(0, result.getOrNull()?.profiles)
        assertEquals(0, result.getOrNull()?.faces)
        assertEquals(
            1, result.getOrNull()?.names,
            "the base name is restored (the toast must say base profile, not 0 profiles)"
        )
        assertEquals("Moi", store["displayName_default"], "the custom pseudo of the base is restored")
        assertEquals("work\tBoulot", File(filesDir, "Profiles_names.txt").readText().trim())
    }

    @Test
    fun aBaseOnlyArchiveRestoresTheBaseFaceAndTheBaseCustomName() {
        // The full base-only export: the base face AND the base's custom name travel
        // together, and both must come back on the re-import.
        val source = File(tmp, "in.txt").apply {
            writeText("${faceLine("default", jpeg)}\n${nameLine("default", "Moi")}\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.profiles)
        assertEquals(1, result.getOrNull()?.faces)
        assertEquals(1, result.getOrNull()?.names)
        assertArrayEquals(jpeg, File(filesDir, "profiles/default/avatar.jpg").readBytes())
        assertEquals("Moi", store["displayName_default"])
        assertFalse(File(filesDir, "Profiles_names.txt").exists(), "the base is never a list line")
    }

    @Test
    fun anUnsafeNameLineIsDroppedButTheListIsKept() {
        val source = File(tmp, "in.txt").apply {
            writeText("work\tBoulot\n${nameLine("a/b", "Moi")}\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.profiles)
        assertEquals(0, result.getOrNull()?.names, "the unsafe name line is dropped")
        assertNull(store["displayName_default"])
    }

    @Test
    fun anOrphanNameIsDroppedButTheBaseNameIsKept() {
        val source = File(tmp, "in.txt").apply {
            writeText("work\tBoulot\n${nameLine("ghost", "Moi")}\n${nameLine("default", "Moi")}\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val result = RescueFiles.importProfileState(context, stateUri)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.names, "the orphan name is dropped, the base name is restored")
        assertEquals("Moi", store["displayName_default"])
        assertNull(store["displayName_ghost"], "a name for a profile that does not exist must not be restored")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Target-dialog read (readProfileStateEntries)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun readProfileStateEntriesCarriesTheListAndTheRenamedBase() {
        // The all-import target dialog reads the state file without applying it. The base
        // is never a list line, so it only shows up when the archive carries its renamed
        // name (the __name__default line) — the user expects to pick a renamed base.
        val source = File(tmp, "in.txt").apply {
            writeText("work\tBoulot\n${nameLine("default", "Moi")}\n")
        }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val entries = RescueFiles.readProfileStateEntries(context, stateUri)

        assertEquals(listOf("work" to "Boulot", "default" to "Moi"), entries)
    }

    @Test
    fun readProfileStateEntriesHasNoBaseRowForALegacyListOnlyFile() {
        val source = File(tmp, "in.txt").apply { writeText("work\tBoulot\n") }
        every { resolver.openInputStream(stateUri) } returns FileInputStream(source)

        val entries = RescueFiles.readProfileStateEntries(context, stateUri)

        assertEquals(listOf("work" to "Boulot"), entries, "no base name line -> no base row")
    }

    @Test
    fun readProfileStateEntriesReturnsEmptyWhenTheStreamIsMissing() {
        every { resolver.openInputStream(stateUri) } returns null

        val entries = RescueFiles.readProfileStateEntries(context, stateUri)

        assertTrue(entries.isEmpty(), "an unreadable file must not crash the dialog")
    }

    // ──────────────────────────────────────────────────────────────────────
    // hasState
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun hasStateIsTrueWhenOnlyTheBaseWasRenamed() {
        // No list file, no face: the renamed base's custom display name alone is
        // archivable state (it lives in the prefs store, never in the list file).
        store["displayName_default"] = "Moi"

        assertTrue(ProfileStateArchive.hasState(context))
    }

    @Test
    fun hasStateIsTrueWhenOnlyAFaceExists() {
        // No list file, no custom name: a stored face alone is archivable state.
        File(filesDir, "profiles/default/avatar.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(jpeg)
        }

        assertTrue(ProfileStateArchive.hasState(context))
    }
}
