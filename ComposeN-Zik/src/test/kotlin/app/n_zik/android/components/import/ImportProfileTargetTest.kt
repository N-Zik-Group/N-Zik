package app.n_zik.android.components.import

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests the import-target list behind the "Import into which profile?" dialog
 * (the per-profile import flow): the base profile is always present (first, with the
 * app default name), local profiles keep their resolved display names, the active
 * profile is marked, and every imported profile that does not exist locally yet (a
 * single file tag, or several profiles carried by an all-import's state file) is
 * offered as its own "from the file" row — an untagged file or an imported profile
 * that matches a local one never adds a row.
 */
class ImportProfileTargetTest {

    private val defaultName = "N-Zik Fan"
    private lateinit var files: File
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        files = Files.createTempDirectory("nzik-import-target").toFile()
        context = mockk()
        every { context.filesDir } returns files
        // The profile store: the base profile is active by default, "work" has a
        // display name.
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns
            stringPrefsOf("displayName_work" to "Work")
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    private fun stringPrefsOf(vararg pairs: Pair<String, String?>): SharedPreferences {
        val store = pairs.toMap()
        return mockk {
            every { getString(any(), any()) } answers { store[firstArg<String>()] ?: secondArg() }
        }
    }

    private fun profilesOf(raw: String) {
        File(files, PROFILE_NAMES_FILE_NAME).writeText(raw)
    }

    @Test
    fun theBaseProfileIsFirstAndLocalProfilesAreListedWithResolvedNames() {
        profilesOf("work\nhome\n")

        val options = buildProfileTargetOptions(context, emptyList(), defaultName)

        assertEquals(listOf("default", "work", "home"), options.map { it.id })
        assertEquals(defaultName, options.first().displayName)
        assertEquals("Work", options[1].displayName)
        // "home" was never renamed: its ID is shown
        assertEquals("home", options[2].displayName)
        assertTrue(options.none { it.fromFileTag })
        // The base profile is the active one here (active = "default")
        assertEquals(true, options.first().isActive)
        assertTrue(options.drop(1).none { it.isActive })
    }

    @Test
    fun theActiveProfileIsMarked() {
        profilesOf("work\n")
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns
            stringPrefsOf(
                "activeProfile" to "work",
                "displayName_work" to "Work",
            )

        val options = buildProfileTargetOptions(context, emptyList(), defaultName)

        assertEquals(true, options[1].isActive)
        assertEquals(false, options.first().isActive)
    }

    @Test
    fun anUnknownFileTagIsAddedAsItsOwnRow() {
        profilesOf("work\n")

        val options = buildProfileTargetOptions(context, listOf("guest" to "guest"), defaultName)

        val guest = options.last()
        assertEquals("guest", guest.id)
        assertTrue(guest.fromFileTag)
        assertFalse(guest.isActive)
        assertEquals(3, options.size)
    }

    @Test
    fun aKnownFileTagIsNotDuplicated() {
        profilesOf("work\n")

        val options = buildProfileTargetOptions(context, listOf("work" to "work"), defaultName)

        assertEquals(2, options.size)
        assertTrue(options.none { it.fromFileTag })
    }

    @Test
    fun anAllImportOffersEveryProfileItsStateFileCarries() {
        profilesOf("work\n")

        // The state file lists "work" (already local) plus "guest" and "home"
        // (not on this device yet) — all of them must be offerable as targets.
        val options = buildProfileTargetOptions(
            context,
            listOf("work" to "Work", "guest" to "Guest", "home" to ""),
            defaultName
        )

        // Local rows first (base + work), then the non-local imported ones
        assertEquals(listOf("default", "work", "guest", "home"), options.map { it.id })
        // "work" stays its local row (resolved name, not a from-file row)
        assertEquals("Work", options[1].displayName)
        assertFalse(options[1].fromFileTag)
        // The imported-only rows show their stored name ("home" has none -> its ID)
        assertTrue(options[2].fromFileTag)
        assertEquals("Guest", options[2].displayName)
        assertTrue(options[3].fromFileTag)
        assertEquals("home", options[3].displayName)
        assertTrue(options.drop(1).none { it.isActive })
    }

    @Test
    fun aRenamedBaseCarriedByTheStateFileIsAdoptedOnTheBaseRow() {
        profilesOf("work\n")

        // An all-import reads the state file, whose base-only name line is carried as
        // ("default", <renamed name>) — the base row must show that name, because that
        // is exactly what the base will be named once the import applies.
        val options = buildProfileTargetOptions(
            context,
            listOf("default" to "Moi", "work" to "Work"),
            defaultName
        )

        // The base is local: it keeps its first, local row — no from-file row for it.
        assertEquals(listOf("default", "work"), options.map { it.id })
        assertEquals("Moi", options.first().displayName)
        assertFalse(options.first().fromFileTag)
        assertTrue(options.none { it.fromFileTag })
        assertEquals(true, options.first().isActive)
    }

    @Test
    fun aRawDefaultTagIsNotMistakenForADisplayName() {
        profilesOf("work\n")

        // A single/both import offers the database tag as `id to id` — the raw
        // "default" tag must not be interpreted as a display name and must not
        // rename the local base row.
        val options = buildProfileTargetOptions(context, listOf("default" to "default"), defaultName)

        assertEquals(defaultName, options.first().displayName)
        assertEquals(2, options.size)
        assertTrue(options.none { it.fromFileTag })
    }
}
