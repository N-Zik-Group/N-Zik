package app.n_zik.android.core.profiles

import android.app.Application
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.writeProfileEntries
import java.io.File
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LegacyImagePublicationTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: ContextWrapper
    private lateinit var originalCover: File
    private lateinit var database: File

    @Before
    fun setUp() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val databases = temporary.newFolder("db")
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
            override fun getDatabasePath(name: String): File = databases.resolve(name)
        }
        context.profilePreferences.edit().clear().commit()
        assertTrue(context.writeProfileEntries(listOf("voyage" to "Voyage")))
        context.setProfileShares(ProfileDataItem.IMAGES, "voyage", true)
        originalCover = files.resolve("app_covers/cover_track.jpg")
        seedImage(originalCover, Color.MAGENTA)
        database = databases.resolve("data_voyage.db")
        SQLiteDatabase.openOrCreateDatabase(database, null).use { db ->
            db.execSQL("CREATE TABLE Song (id TEXT PRIMARY KEY, thumbnailUrl TEXT)")
            val path = originalCover.absolutePath
            assertEquals(originalCover.canonicalFile, File(path).canonicalFile)
            db.execSQL("INSERT INTO Song VALUES ('track', ?)", arrayOf(path))
        }
        val coil = cache.resolve("coil").apply { mkdirs() }
        val key = "https://example.invalid/track".encodeUtf8().sha256().hex()
        coil.resolve("$key.0").writeText("header")
        coil.resolve("$key.1").writeText("cached image")
        coil.resolve("journal").writeText("libcore.io.DiskLruCache\n1\n3\n2\n\nCLEAN $key 6 12\n")
    }

    private fun seedImage(file: File, color: Int) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun referencedCover(): String =
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT thumbnailUrl FROM Song", null).use { rows ->
                check(rows.moveToFirst())
                rows.getString(0)
            }
        }

    @Test
    fun `publishes independent cache and updates only verified cover references`() {
        val original = originalCover.readBytes()
        LegacyImagePublication.resume(context)

        val private = context.filesDir.resolve("app_covers_voyage/cover_track.jpg")
        assertFalse(context.profileShares(ProfileDataItem.IMAGES, "voyage"))
        assertEquals(private.absolutePath, referencedCover())
        assertArrayEquals(original, private.readBytes())
        assertTrue(context.cacheDir.resolve("coil_voyage/journal").isFile)
        assertArrayEquals(original, originalCover.readBytes())
        assertTrue(context.filesDir.resolve("protected-profile-history/images").listFiles().orEmpty().any {
            it.resolve("covers/cover_track.jpg").isFile
        })
    }

    @Test
    fun `cover collision preserves both originals and the previous lookup`() {
        val private = context.filesDir.resolve("app_covers_voyage/cover_track.jpg")
        seedImage(private, Color.BLUE)
        val previous = private.readBytes()
        LegacyImagePublication.resume(context)

        assertTrue(context.profileShares(ProfileDataItem.IMAGES, "voyage"))
        assertEquals(originalCover.absolutePath, referencedCover())
        assertArrayEquals(previous, private.readBytes())
    }

    @Test
    fun `explicit image recovery preserves originals and refuses conflicting covers`() {
        LegacyImagePublication.resume(context)
        assertTrue(context.writeProfileEntries(listOf("voyage" to "Voyage", "other" to "Other")))
        val target = checkNotNull(profileStorageIdentity(context, "other"))
        val archive = ProtectedProfileHistory.archives(context).single { it.kind == ProtectedProfileHistory.Kind.IMAGES }
        val destination = context.filesDir.resolve("app_covers_other/cover_track.jpg")
        seedImage(destination, Color.BLUE)
        val prior = destination.readBytes()
        val original = originalCover.readBytes()

        assertThrows(IllegalStateException::class.java) { ProtectedProfileHistory.recover(context, archive, target) }
        assertArrayEquals(prior, destination.readBytes())
        assertFalse(context.cacheDir.resolve("coil_other").exists())
        assertFalse(ProtectedProfileHistory.hasPending(context))

        assertTrue(destination.delete())
        assertTrue(checkNotNull(destination.parentFile).delete())
        ProtectedProfileHistory.recover(context, archive, target)
        assertArrayEquals(original, destination.readBytes())
        assertArrayEquals(original, originalCover.readBytes())
        assertTrue(context.cacheDir.resolve("coil_other/journal").isFile)
    }

    @Test
    fun `waveform recovery resumes a previously published destination and keeps its archived source`() {
        context.setProfileShares(ProfileDataItem.WAVEFORMS, "voyage", true)
        val original = context.filesDir.resolve("waveforms").apply { mkdirs() }.resolve("track.json")
            .apply { writeText("[0,1]") }
        LegacyWaveformPublication.resume(context)
        val archive = ProtectedProfileHistory.archives(context).single { it.kind == ProtectedProfileHistory.Kind.WAVEFORMS }
        val target = checkNotNull(profileStorageIdentity(context, "voyage"))
        val marker = context.filesDir.resolve(".historical-recovery.pending")
        marker.writeText("WAVEFORMS\n${archive.id}\n${target.id}\n${target.generation}")

        ProtectedProfileHistory.resume(context)

        assertFalse(marker.exists())
        assertEquals("[0,1]", original.readText())
        assertEquals("[0,1]", context.filesDir.resolve("waveforms_voyage/track.json").readText())
        assertEquals("[0,1]", context.filesDir.resolve("protected-profile-history/waveforms/${archive.id}/track.json").readText())
    }
}
