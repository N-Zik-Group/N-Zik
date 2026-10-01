package app.n_zik.android.core.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The pure resolver of a profile's data file names (spec-profile-data-separation):
 * the base profile keeps EXACTLY the unsuffixed locations whatever its flags (zero
 * migration, no rename); a user profile maps each SHARED item onto the base name and
 * each SEPARATE item onto the suffixed `<item>_<id>` name; the index rule is per cache
 * instance — a separate cache NEVER points at the base index database, and when both
 * caches are separate the single suffixed database carries both indexes.
 */
class ProfileDataDirsTest {

    @Test
    fun `the base profile keeps the unsuffixed names whatever its flags`() {
        val separate = resolveProfileDataDirNames("default", emptySet())
        val shared = resolveProfileDataDirNames("default", ProfileDataItem.ALL.toSet())

        for (names in listOf(separate, shared)) {
            assertEquals("exo_downloads", names.downloadsDir)
            assertEquals("exoplayer", names.mediaCacheDir)
            assertEquals("app_covers", names.coversDir)
            assertEquals("coil", names.imageCacheDir)
            assertEquals("waveforms", names.waveformsDir)
            assertEquals("exoplayer_internal.db", names.downloadsIndexDb)
            assertEquals("exoplayer_internal.db", names.mediaCacheIndexDb)
        }
    }

    @Test
    fun `a profile with nothing shared gets every suffixed name`() {
        val names = resolveProfileDataDirNames("work", emptySet())

        assertEquals("exo_downloads_work", names.downloadsDir)
        assertEquals("exoplayer_work", names.mediaCacheDir)
        assertEquals("app_covers_work", names.coversDir)
        assertEquals("coil_work", names.imageCacheDir)
        assertEquals("waveforms_work", names.waveformsDir)
        // Both cache indexes live in the single suffixed database
        assertEquals("exoplayer_internal_work.db", names.downloadsIndexDb)
        assertEquals(names.downloadsIndexDb, names.mediaCacheIndexDb)
    }

    @Test
    fun `a shared item maps onto the base name and the base index`() {
        val names = resolveProfileDataDirNames("work", setOf(ProfileDataItem.DOWNLOADS))

        // Shared: the base location, the base index
        assertEquals("exo_downloads", names.downloadsDir)
        assertEquals("exoplayer_internal.db", names.downloadsIndexDb)
        // Separate: the suffixed location, the suffixed index
        assertEquals("exoplayer_work", names.mediaCacheDir)
        assertEquals("exoplayer_internal_work.db", names.mediaCacheIndexDb)
        assertEquals("app_covers_work", names.coversDir)
        assertEquals("coil_work", names.imageCacheDir)
        assertEquals("waveforms_work", names.waveformsDir)
    }

    @Test
    fun `a separate cache never points at the base index`() {
        // downloads separate, media cache shared: the base index carries only the
        // shared media cache; the separate downloads get their own suffixed one
        val names = resolveProfileDataDirNames("work", setOf(ProfileDataItem.MEDIA_CACHE))

        assertEquals("exoplayer_internal_work.db", names.downloadsIndexDb)
        assertEquals("exoplayer_internal.db", names.mediaCacheIndexDb)
    }

    @Test
    fun `the images toggle moves covers and the coil cache together`() {
        val separate = resolveProfileDataDirNames("work", emptySet())

        assertEquals("app_covers_work", separate.coversDir)
        assertEquals("coil_work", separate.imageCacheDir)

        val shared = resolveProfileDataDirNames("work", setOf(ProfileDataItem.IMAGES))

        assertEquals("app_covers", shared.coversDir)
        assertEquals("coil", shared.imageCacheDir)
        // the other items stay separate
        assertEquals("exo_downloads_work", shared.downloadsDir)
        assertEquals("waveforms_work", shared.waveformsDir)
    }

    @Test
    fun `sharing everything maps the profile onto the base locations`() {
        val names = resolveProfileDataDirNames("work", ProfileDataItem.ALL.toSet())

        assertEquals("exo_downloads", names.downloadsDir)
        assertEquals("exoplayer", names.mediaCacheDir)
        assertEquals("app_covers", names.coversDir)
        assertEquals("coil", names.imageCacheDir)
        assertEquals("waveforms", names.waveformsDir)
        assertEquals("exoplayer_internal.db", names.downloadsIndexDb)
        assertEquals("exoplayer_internal.db", names.mediaCacheIndexDb)
    }

    @Test
    fun `the directory constants are the current unsuffixed names`() {
        // The base locations must stay bit-for-bit the pre-feature ones: any rename
        // here would silently migrate the base data.
        assertEquals("exo_downloads", ProfileDataDirNames.DOWNLOADS_DIR)
        assertEquals("exoplayer", ProfileDataDirNames.MEDIA_CACHE_DIR)
        assertEquals("app_covers", ProfileDataDirNames.COVERS_DIR)
        assertEquals("coil", ProfileDataDirNames.IMAGE_CACHE_DIR)
        assertEquals("waveforms", ProfileDataDirNames.WAVEFORMS_DIR)
        assertEquals("exoplayer_internal.db", ProfileDataDirNames.INDEX_DB_FILE)
    }

    // ──────────────────────────────────────────────────────────────────────
    // totalDirectorySize (the sizes shown on the profile cards)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `totalDirectorySize is zero for a missing dir`(@TempDir tmp: File) {
        assertEquals(0L, totalDirectorySize(File(tmp, "nope")))
    }

    @Test
    fun `totalDirectorySize sums the nested files`(@TempDir tmp: File) {
        val dir = File(tmp, "data")
        File(dir, "a.bin").apply { parentFile.mkdirs(); writeBytes(ByteArray(5)) }
        File(dir, "nested/b.bin").apply { parentFile.mkdirs(); writeBytes(ByteArray(3)) }
        File(dir, "nested/empty.bin").apply { writeBytes(ByteArray(0)) }

        assertEquals(8L, totalDirectorySize(dir))
    }

    @Test
    fun `totalDirectorySize tolerates a missing subdir inside an existing dir`(@TempDir tmp: File) {
        val dir = File(tmp, "data")
        File(dir, "a.bin").apply { parentFile.mkdirs(); writeBytes(ByteArray(2)) }

        assertEquals(2L, totalDirectorySize(dir))
        assertEquals(0L, totalDirectorySize(File(dir, "never-existed")))
    }
}
