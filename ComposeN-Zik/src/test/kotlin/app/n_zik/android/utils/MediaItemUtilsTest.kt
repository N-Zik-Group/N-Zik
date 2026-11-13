package app.n_zik.android.utils

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests [titleOrDb] against a real Room database (Robolectric + Room, same
 * style as EventTableTest/LyricsTableTest): clean metadata title is returned
 * as-is, "null"/empty metadata falls back to the cleaned DB title, and a
 * missing DB row falls back to the metadata title.
 *
 * Note: findByIdDirect is a non-suspend DAO call, so it must run off the
 * main thread (Room main-thread guard) — hence withContext(NzikDispatchers.DATA).
 */
/**
 * Test-only application: MainApplication.onCreate() migrates credentials via
 * AndroidKeyStore (MasterKey), which is not available on the Robolectric JVM.
 * MainApplication is final, so Dependencies.application is a mock wired to
 * this app's context — enough for the Room DB under test.
 */
class TestApplication : Application()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class MediaItemUtilsTest {
    @Test
    fun `display title strips the explicit glyph followed by the internal prefix`() {
        assertEquals("Cold (feat. Future)", displayTrackTitle("\uD83C\uDD74 e:Cold (feat. Future)"))
    }


    @Before
    fun initDependencies() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
    }

    private fun item(id: String, metadataTitle: String?) = MediaItem.Builder()
        .setMediaId(id)
        .apply {
            metadataTitle?.let {
                setMediaMetadata(MediaMetadata.Builder().setTitle(it).build())
            }
        }
        .build()

    private suspend fun insertSong(id: String, title: String) {
        // Room suspend DAOs still block the calling thread — keep it off main.
        withContext(NzikDispatchers.DATA) {
            Database.songTable.upsert(
                Song(id = id, title = title, durationText = null, thumbnailUrl = null)
            )
        }
    }

    @Test
    fun `clean metadata title is returned as is`() = runBlocking {
        val title = withContext(NzikDispatchers.DATA) {
            item("lf_id_1", "Hello World").titleOrDb()
        }

        assertEquals("Hello World", title)
    }

    @Test
    fun `prefixed metadata title is cleaned`() = runBlocking {
        val title = withContext(NzikDispatchers.DATA) {
            item("lf_id_2", "modified:Hello World").titleOrDb()
        }

        assertEquals("Hello World", title)
    }

    @Test
    fun `empty metadata title falls back to the cleaned database title`() = runBlocking {
        insertSong("lf_id_3", "modified:DB Song")

        val title = withContext(NzikDispatchers.DATA) {
            item("lf_id_3", null).titleOrDb()
        }

        assertEquals("DB Song", title)
    }

    @Test
    fun `null literal metadata title falls back to the database title`() = runBlocking {
        insertSong("lf_id_4", "DB Title")

        val title = withContext(NzikDispatchers.DATA) {
            item("lf_id_4", "null").titleOrDb()
        }

        assertEquals("DB Title", title)
    }

    @Test
    fun `missing database row falls back to the metadata title`() = runBlocking {
        val title = withContext(NzikDispatchers.DATA) {
            item("lf_id_missing", null).titleOrDb()
        }

        assertEquals("", title)
    }
}
