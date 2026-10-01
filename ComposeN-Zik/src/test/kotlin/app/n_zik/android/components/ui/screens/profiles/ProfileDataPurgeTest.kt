package app.n_zik.android.components.ui.screens.profiles

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.profileShareKey
import app.n_zik.android.core.profiles.profileShares
import app.n_zik.android.core.profiles.setProfileShares
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Tests the profile-data purge run on the profiles-page deletion (spec-profile-data-separation):
 * [purgeProfileData] wipes only the deleted profile's SEPARATE data (its suffixed dirs + its
 * suffixed index, side files included), forgets its sharing flags, and leaves the base data —
 * the shared locations — untouched. The context is a fresh mock (the screen's deletion flow is
 * a fire-and-forget dialog callback, so the purge itself is the testable unit — the same
 * pattern as [ProfileAvatarPurgeTest] for the avatar purge).
 */
class ProfileDataPurgeTest {

    @TempDir
    lateinit var tmp: File

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var databasesDir: File
    private lateinit var context: Context

    // The real profile_preferences store, backed by an in-memory map.
    private val store = mutableMapOf<String, Any?>()

    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    init {
        every { editor.putBoolean(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Boolean>()
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
        cacheDir = File(tmp, "cache").apply { mkdirs() }
        databasesDir = File(tmp, "databases").apply { mkdirs() }
        val prefs = mockk<SharedPreferences> {
            every { edit() } answers { editor }
            // Reads go through the same in-memory map the editor writes to.
            every { getBoolean(any(), any()) } answers { (store[firstArg<String>()] as? Boolean) ?: secondArg() }
            every { contains(any()) } answers { store.containsKey(firstArg<String>()) }
        }
        context = mockk()
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        every { context.getDatabasePath(any()) } answers {
            File(databasesDir, firstArg<String>())
        }
        // The sharing flags live in the global profile_preferences store.
        every { context.getSharedPreferences(any(), any()) } returns prefs
    }

    @Test
    fun `deleting a profile purges its separate data and forgets its share flags`() {
        // The profile's SEPARATE data (suffixed) + its suffixed index (side files included).
        File(cacheDir, "exo_downloads_work").apply { mkdirs(); File(this, "dl.bin").writeText("x") }
        File(cacheDir, "exoplayer_work").apply { mkdirs(); File(this, "stream.bin").writeText("x") }
        File(cacheDir, "coil_work").apply { mkdirs(); File(this, "img.bin").writeText("x") }
        File(filesDir, "app_covers_work").apply { mkdirs(); File(this, "cover.jpg").writeText("x") }
        File(filesDir, "waveforms_work").apply { mkdirs(); File(this, "w.json").writeText("x") }
        File(databasesDir, "exoplayer_internal_work.db").writeText("index")
        File(databasesDir, "exoplayer_internal_work.db-wal").writeText("wal")
        // The base (unsuffixed) data — the shared locations.
        File(cacheDir, "exo_downloads").apply { mkdirs(); File(this, "dl.bin").writeText("base") }
        File(cacheDir, "exoplayer").apply { mkdirs(); File(this, "stream.bin").writeText("base") }
        File(filesDir, "app_covers").apply { mkdirs(); File(this, "cover.jpg").writeText("base") }
        File(databasesDir, "exoplayer_internal.db").writeText("base-index")
        // The profile's sharing flags.
        context.setProfileShares(ProfileDataItem.DOWNLOADS, "work", true)
        context.setProfileShares(ProfileDataItem.WAVEFORMS, "work", true)

        purgeProfileData(context, "work")

        // The profile's separate data is gone, suffixed index + side files included.
        assertFalse(File(cacheDir, "exo_downloads_work").exists(), "the separate downloads must be purged")
        assertFalse(File(cacheDir, "exoplayer_work").exists(), "the separate media cache must be purged")
        assertFalse(File(cacheDir, "coil_work").exists(), "the separate image cache must be purged")
        assertFalse(File(filesDir, "app_covers_work").exists(), "the separate covers must be purged")
        assertFalse(File(filesDir, "waveforms_work").exists(), "the separate waveforms must be purged")
        assertFalse(File(databasesDir, "exoplayer_internal_work.db").exists(), "the suffixed index must be purged")
        assertFalse(File(databasesDir, "exoplayer_internal_work.db-wal").exists(), "the suffixed index side files must be purged")
        // The base data (the shared locations) is untouched.
        assertTrue(File(cacheDir, "exo_downloads/dl.bin").exists(), "the base downloads must survive")
        assertTrue(File(cacheDir, "exoplayer/stream.bin").exists(), "the base media cache must survive")
        assertTrue(File(filesDir, "app_covers/cover.jpg").exists(), "the base covers must survive")
        assertTrue(File(databasesDir, "exoplayer_internal.db").exists(), "the base index must survive")
        // The sharing flags are forgotten.
        assertFalse(context.profileShares(ProfileDataItem.DOWNLOADS, "work"))
        assertFalse(context.profileShares(ProfileDataItem.WAVEFORMS, "work"))
        assertFalse(
            context.profilePreferences.contains(profileShareKey(ProfileDataItem.DOWNLOADS, "work")),
            "the share keys are removed from the store"
        )
    }
}
