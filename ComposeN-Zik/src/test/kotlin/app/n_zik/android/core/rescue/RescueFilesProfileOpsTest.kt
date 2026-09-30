package app.n_zik.android.core.rescue

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Tests the profile operations of the Rescue Center (spec-profiles-page-face, danger zone):
 * [RescueFiles.deleteProfiles] purges the chosen user profiles (names line, settings files,
 * database + WAL/SHM/journal, face entries, face files) and re-points the active slot to
 * the base when the active profile is deleted; [RescueFiles.resetProfiles] factory-freshes
 * the selected profiles — the base included (its database, settings files, face entries
 * and face files) — without touching the profile list or the active slot; and
 * [RescueFiles.restoreProfiles] brings the backed-up data back. The context is a fresh
 * mock — the operations run in the `:rescue` process where no live database exists, so
 * plain file operations are the whole surface.
 */
class RescueFilesProfileOpsTest {

    @TempDir
    lateinit var tmp: File

    private lateinit var filesDir: File
    private lateinit var sharedPrefsDir: File
    private lateinit var databasesDir: File
    private lateinit var context: Context

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
        every { editor.putLong(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Long>()
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
        sharedPrefsDir = File(tmp, "shared_prefs").apply { mkdirs() }
        databasesDir = File(tmp, "databases").apply { mkdirs() }
        val prefs = mockk<SharedPreferences> {
            every { edit() } answers { editor }
            // Reads go through the same in-memory map the editor writes to.
            every { getString(any(), any()) } answers { store[firstArg<String>()] as String? }
            every { getLong(any(), any()) } answers { (store[firstArg<String>()] as? Long) ?: secondArg() }
        }
        context = mockk()
        every { context.filesDir } returns filesDir
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns prefs
        // deleteSharedPreferences removes the shared_prefs XML of the name (API 24+).
        every { context.deleteSharedPreferences(any()) } answers {
            val name = firstArg<String>()
            val xml = File(sharedPrefsDir, "$name.xml")
            val xmlBak = File(sharedPrefsDir, "$name.xml.bak")
            val existed = xml.exists() || xmlBak.exists()
            if (xml.exists()) xml.delete()
            if (xmlBak.exists()) xmlBak.delete()
            existed
        }
        // The database files live in the fake databases dir, exactly like getDatabasePath
        // would resolve them.
        every { context.deleteDatabase(any()) } answers {
            File(databasesDir, firstArg<String>()).let { if (it.exists()) it.delete() else true }
        }
        every { context.getDatabasePath(any()) } answers {
            File(databasesDir, firstArg<String>())
        }
        // The profile backup/restore reads the settings XMLs from the data dir
        // (the plain shared_prefs dir — the profile suffix lives in the file name).
        // dataDir is a Java field, not a method: mockk cannot stub it, so it is set
        // reflectively on the proxy (the stub class carries the field).
        val appInfo = mockk<ApplicationInfo>()
        ApplicationInfo::class.java.getDeclaredField("dataDir").apply {
            isAccessible = true
            set(appInfo, tmp.absolutePath)
        }
        every { context.applicationInfo } returns appInfo
        // Production contract: the screen initializes the rescue target before any
        // file operation (the first call wins — the rest are no-ops).
        RescueFiles.initialize(context)
    }

    private fun seedProfileFiles(profile: String, db: String, prefs: Boolean = true) {
        if (prefs) {
            File(sharedPrefsDir, "preferences_$profile.xml").writeText("<xml/>")
            File(sharedPrefsDir, "secure_preferences_$profile.xml").writeText("<xml/>")
        }
        File(databasesDir, db).writeText("db")
        File(databasesDir, "$db-wal").writeText("wal")
        File(databasesDir, "$db-shm").writeText("shm")
        File(filesDir, "profiles/$profile/avatar.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
    }

    private fun seedBaseFiles() {
        File(sharedPrefsDir, "preferences.xml").writeText("<xml/>")
        File(sharedPrefsDir, "secure_preferences.xml").writeText("<xml/>")
        File(databasesDir, "data.db").writeText("db")
        File(databasesDir, "data.db-wal").writeText("wal")
        File(databasesDir, "data.db-journal").writeText("journal")
        File(filesDir, "profiles/default/avatar.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // deleteProfiles
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun deleteProfilesPurgesNamesSettingsDatabaseAndFace() {
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\nhome\n")
        store["displayName_work"] = "Boulot"
        store["lastUsed_work"] = 123L
        store["displayName_home"] = "Home"
        seedProfileFiles("work", "data_work.db")
        seedProfileFiles("home", "data_home.db")

        val result = RescueFiles.deleteProfiles(context, listOf("work"))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        // The names file keeps only the surviving profile — the file mirrors the
        // store, so home's renamed display name comes back on its line.
        assertEquals("home\tHome", File(filesDir, "Profiles_names.txt").readText().trim())
        // Every file of the deleted profile is gone
        assertFalse(File(sharedPrefsDir, "preferences_work.xml").exists())
        assertFalse(File(sharedPrefsDir, "secure_preferences_work.xml").exists())
        assertFalse(File(databasesDir, "data_work.db").exists())
        assertFalse(File(databasesDir, "data_work.db-wal").exists())
        assertFalse(File(databasesDir, "data_work.db-shm").exists())
        assertFalse(File(filesDir, "profiles/work").exists())
        // Its face entries are forgotten
        assertNull(store["displayName_work"])
        assertNull(store["lastUsed_work"])
        // The surviving profile is untouched (files, face entries and names line)
        assertTrue(File(sharedPrefsDir, "preferences_home.xml").exists())
        assertTrue(File(databasesDir, "data_home.db").exists())
        assertTrue(File(filesDir, "profiles/home/avatar.jpg").exists())
        assertEquals("Home", store["displayName_home"])
    }

    @Test
    fun deleteProfilesSwitchesTheActiveProfileToTheBaseWhenItIsDeleted() {
        File(filesDir, "Profiles_names.txt").writeText("work\nhome\n")
        store["activeProfile"] = "work"
        seedProfileFiles("work", "data_work.db")

        val result = RescueFiles.deleteProfiles(context, listOf("work"))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        // The next launch must start on a valid profile: the active slot is the base
        assertEquals("default", store["activeProfile"])
        // The other profiles survive
        assertEquals("home", File(filesDir, "Profiles_names.txt").readText().trim())
    }

    @Test
    fun deleteProfilesKeepsTheActiveSlotWhenTheActiveProfileSurvives() {
        File(filesDir, "Profiles_names.txt").writeText("work\nhome\n")
        store["activeProfile"] = "work"
        seedProfileFiles("work", "data_work.db")
        seedProfileFiles("home", "data_home.db")

        val result = RescueFiles.deleteProfiles(context, listOf("home"))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertEquals("work", store["activeProfile"], "the active profile survived: no switch")
        assertEquals("work", File(filesDir, "Profiles_names.txt").readText().trim())
    }

    @Test
    fun deleteProfilesIgnoresTheBaseId() {
        File(filesDir, "Profiles_names.txt").writeText("work\n")
        store["activeProfile"] = "default"
        store["displayName_default"] = "Moi"
        store["lastUsed_default"] = 1L
        seedBaseFiles()
        seedProfileFiles("work", "data_work.db")

        val result = RescueFiles.deleteProfiles(context, listOf("default"))

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull(), "the base is never deleted by the profile deletion")
        // Nothing was touched
        assertTrue(File(databasesDir, "data.db").exists())
        assertTrue(File(sharedPrefsDir, "preferences.xml").exists())
        assertTrue(File(filesDir, "profiles/default/avatar.jpg").exists())
        assertEquals("Moi", store["displayName_default"])
        assertEquals("work", File(filesDir, "Profiles_names.txt").readText().trim())
        assertEquals("default", store["activeProfile"])
    }

    @Test
    fun deleteProfilesIsANoOpForAnEmptySelection() {
        File(filesDir, "Profiles_names.txt").writeText("work\n")
        seedProfileFiles("work", "data_work.db")

        val result = RescueFiles.deleteProfiles(context, emptyList())

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertEquals("work", File(filesDir, "Profiles_names.txt").readText().trim())
        assertTrue(File(databasesDir, "data_work.db").exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // resetProfiles — the base
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun resetProfilesResetsTheBaseLikeAnyOtherProfile() {
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\n")
        store["activeProfile"] = "default"
        store["displayName_default"] = "Moi"
        store["lastUsed_default"] = 42L
        store["displayName_work"] = "Boulot"
        seedBaseFiles()
        seedProfileFiles("work", "data_work.db")

        val result = RescueFiles.resetProfiles(context, listOf("default"))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        // The base files are gone (database + WAL/journal, settings, face)
        assertFalse(File(databasesDir, "data.db").exists())
        assertFalse(File(databasesDir, "data.db-wal").exists())
        assertFalse(File(databasesDir, "data.db-journal").exists())
        assertFalse(File(sharedPrefsDir, "preferences.xml").exists())
        assertFalse(File(sharedPrefsDir, "secure_preferences.xml").exists())
        assertFalse(File(filesDir, "profiles/default").exists())
        // The base face entries are forgotten
        assertNull(store["displayName_default"])
        assertNull(store["lastUsed_default"])
        // The base always exists: the active slot is untouched, and the list
        // (which never lists the base) is intact
        assertEquals("default", store["activeProfile"])
        assertEquals("work\tBoulot", File(filesDir, "Profiles_names.txt").readText().trim())
        // The user profiles survive
        assertTrue(File(databasesDir, "data_work.db").exists())
        assertTrue(File(filesDir, "profiles/work/avatar.jpg").exists())
    }

    @Test
    fun resetProfilesOnTheBaseSucceedsOnAFreshInstallWithoutAnyBaseFiles() {
        File(filesDir, "Profiles_names.txt").writeText("work\n")
        store["activeProfile"] = "work"

        val result = RescueFiles.resetProfiles(context, listOf("default"))

        assertTrue(result.isSuccess, "missing base files are not an error")
        assertEquals(1, result.getOrNull())
        assertEquals("work", store["activeProfile"])
        assertEquals("work", File(filesDir, "Profiles_names.txt").readText().trim())
    }

    // ──────────────────────────────────────────────────────────────────────
    // resetProfiles
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun resetProfilesWipesTheDataButKeepsTheProfileInTheList() {
        File(filesDir, "Profiles_names.txt").writeText("work\nhome\n")
        store["activeProfile"] = "work"
        seedProfileFiles("work", "data_work.db")
        seedProfileFiles("home", "data_home.db")

        val result = RescueFiles.resetProfiles(context, listOf("work"))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        // work's own files are gone
        assertFalse(File(databasesDir, "data_work.db").exists())
        assertFalse(File(sharedPrefsDir, "preferences_work.xml").exists())
        assertFalse(File(sharedPrefsDir, "secure_preferences_work.xml").exists())
        assertFalse(File(filesDir, "profiles/work").exists())
        // …but work is still a switchable profile: its names-file line survives
        assertEquals("work\nhome", File(filesDir, "Profiles_names.txt").readText().trim())
        // home is untouched
        assertTrue(File(databasesDir, "data_home.db").exists())
        assertTrue(File(filesDir, "profiles/home/avatar.jpg").exists())
        // the active slot is untouched (a reset profile still exists)
        assertEquals("work", store["activeProfile"])
    }

    @Test
    fun resetProfilesClearsTheDisplayNamesAndMirrorsItInTheNamesFile() {
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\nhome\tHome\n")
        store["displayName_work"] = "Boulot"
        store["lastUsed_work"] = 123L
        store["displayName_home"] = "Home"
        seedProfileFiles("work", "data_work.db")
        seedProfileFiles("home", "data_home.db")

        val result = RescueFiles.resetProfiles(context, listOf("work"))

        assertTrue(result.isSuccess)
        // work's custom pseudo + last use are forgotten (factory state)
        assertNull(store["displayName_work"])
        assertNull(store["lastUsed_work"])
        // the names file mirrors the store: work's line loses its name, home keeps its
        assertEquals("work\nhome\tHome", File(filesDir, "Profiles_names.txt").readText().trim())
        // home's display name survives
        assertEquals("Home", store["displayName_home"])
    }

    @Test
    fun resetProfilesIsANoOpForAnEmptySelection() {
        File(filesDir, "Profiles_names.txt").writeText("work\n")
        seedProfileFiles("work", "data_work.db")

        val result = RescueFiles.resetProfiles(context, emptyList())

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertTrue(File(databasesDir, "data_work.db").exists())
        assertEquals("work", File(filesDir, "Profiles_names.txt").readText().trim())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Profile reset backups + restore
    // ──────────────────────────────────────────────────────────────────────

    private fun backupDir(profile: String) = File(filesDir, "rescue_backups/profiles/$profile")

    @Test
    fun resetProfilesBacksUpTheDataBeforeWiping() {
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\n")
        store["displayName_work"] = "Boulot"
        store["lastUsed_work"] = 123L
        seedProfileFiles("work", "data_work.db")

        val result = RescueFiles.resetProfiles(context, listOf("work"))

        assertTrue(result.isSuccess)
        // The backup holds the database (original name, side files included)...
        assertEquals("db", File(backupDir("work"), "data_work.db").readText())
        assertEquals("wal", File(backupDir("work"), "data_work.db-wal").readText())
        assertEquals("shm", File(backupDir("work"), "data_work.db-shm").readText())
        // ...the settings XMLs under their stored names...
        assertEquals("<xml/>", File(backupDir("work"), "preferences_work.xml").readText())
        assertEquals("<xml/>", File(backupDir("work"), "secure_preferences_work.xml").readText())
        // ...the face files...
        assertTrue(File(backupDir("work"), "profile/avatar.jpg").exists())
        // ...and the face entries captured before the wipe
        assertEquals("name=Boulot\nlastUsed=123\n", File(backupDir("work"), "face_entries.txt").readText())
        // The live data is gone (the reset still happened)
        assertFalse(File(databasesDir, "data_work.db").exists())
        assertFalse(File(sharedPrefsDir, "preferences_work.xml").exists())
        assertFalse(File(filesDir, "profiles/work").exists())
    }

    @Test
    fun restoreProfilesBringsTheDataBack() {
        File(filesDir, "Profiles_names.txt").writeText("work\tBoulot\nhome\n")
        store["displayName_work"] = "Boulot"
        store["lastUsed_work"] = 123L
        seedProfileFiles("work", "data_work.db")
        seedProfileFiles("home", "data_home.db")

        assertTrue(RescueFiles.resetProfiles(context, listOf("work")).isSuccess)
        val result = RescueFiles.restoreProfiles(context, listOf("work"))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        // The database is back (content + side files)
        assertEquals("db", File(databasesDir, "data_work.db").readText())
        assertEquals("wal", File(databasesDir, "data_work.db-wal").readText())
        assertEquals("shm", File(databasesDir, "data_work.db-shm").readText())
        // The settings XMLs are back
        assertEquals("<xml/>", File(sharedPrefsDir, "preferences_work.xml").readText())
        assertEquals("<xml/>", File(sharedPrefsDir, "secure_preferences_work.xml").readText())
        // The face files are back
        assertTrue(File(filesDir, "profiles/work/avatar.jpg").exists())
        // The face entries are re-applied (pseudo + last use) and mirrored in the names file
        assertEquals("Boulot", store["displayName_work"])
        assertEquals(123L, store["lastUsed_work"])
        assertEquals("work\tBoulot\nhome", File(filesDir, "Profiles_names.txt").readText().trim())
        // The other profile is untouched
        assertTrue(File(databasesDir, "data_home.db").exists())
    }

    @Test
    fun restoreProfilesParksTheFreshDatabaseAsTheNewBackup() {
        File(filesDir, "Profiles_names.txt").writeText("work\n")
        seedProfileFiles("work", "data_work.db")

        assertTrue(RescueFiles.resetProfiles(context, listOf("work")).isSuccess)
        // The profile is used again after the reset: a fresh (empty) database appears.
        File(databasesDir, "data_work.db").writeText("fresh")
        File(databasesDir, "data_work.db-wal").writeText("fresh-wal")

        val result = RescueFiles.restoreProfiles(context, listOf("work"))

        assertTrue(result.isSuccess)
        // The live database is the backed-up one...
        assertEquals("db", File(databasesDir, "data_work.db").readText())
        assertEquals("wal", File(databasesDir, "data_work.db-wal").readText())
        // ...and the fresh one becomes the new backup (the same ping-pong as restoreDatabase)
        assertEquals("fresh", File(backupDir("work"), "data_work.db").readText())
        assertEquals("fresh-wal", File(backupDir("work"), "data_work.db-wal").readText())
    }

    @Test
    fun restoreProfilesSkipsTheIdsWithoutABackup() {
        File(filesDir, "Profiles_names.txt").writeText("work\nhome\n")
        seedProfileFiles("work", "data_work.db")
        seedProfileFiles("home", "data_home.db")
        assertTrue(RescueFiles.resetProfiles(context, listOf("work")).isSuccess)

        // home and the base were never reset (no backup): only work restores
        val result = RescueFiles.restoreProfiles(context, listOf("work", "home", "default"))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        // home's data is untouched
        assertEquals("db", File(databasesDir, "data_home.db").readText())
    }

    @Test
    fun restoreProfilesIsANoOpForAnEmptySelection() {
        File(filesDir, "Profiles_names.txt").writeText("work\n")
        seedProfileFiles("work", "data_work.db")

        val result = RescueFiles.restoreProfiles(context, emptyList())

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertTrue(File(databasesDir, "data_work.db").exists())
    }

    @Test
    fun resetProfilesBacksUpTheBaseBeforeWiping() {
        store["displayName_default"] = "Moi"
        store["lastUsed_default"] = 42L
        seedBaseFiles()

        val result = RescueFiles.resetProfiles(context, listOf("default"))

        assertTrue(result.isSuccess)
        val baseBackup = backupDir("default")
        assertEquals("db", File(baseBackup, "data.db").readText())
        assertEquals("wal", File(baseBackup, "data.db-wal").readText())
        assertEquals("journal", File(baseBackup, "data.db-journal").readText())
        assertEquals("<xml/>", File(baseBackup, "preferences.xml").readText())
        assertEquals("<xml/>", File(baseBackup, "secure_preferences.xml").readText())
        assertTrue(File(baseBackup, "profile/avatar.jpg").exists())
        assertEquals("name=Moi\nlastUsed=42\n", File(baseBackup, "face_entries.txt").readText())
    }

    @Test
    fun restoreProfilesBringsTheBaseBack() {
        store["displayName_default"] = "Moi"
        store["lastUsed_default"] = 42L
        seedBaseFiles()

        assertTrue(RescueFiles.resetProfiles(context, listOf("default")).isSuccess)
        val result = RescueFiles.restoreProfiles(context, listOf("default"))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        // The base data is back (database + side files, settings, face)
        assertEquals("db", File(databasesDir, "data.db").readText())
        assertEquals("wal", File(databasesDir, "data.db-wal").readText())
        assertEquals("journal", File(databasesDir, "data.db-journal").readText())
        assertEquals("<xml/>", File(sharedPrefsDir, "preferences.xml").readText())
        assertEquals("<xml/>", File(sharedPrefsDir, "secure_preferences.xml").readText())
        assertTrue(File(filesDir, "profiles/default/avatar.jpg").exists())
        // The face entries are re-applied
        assertEquals("Moi", store["displayName_default"])
        assertEquals(42L, store["lastUsed_default"])
    }
}
