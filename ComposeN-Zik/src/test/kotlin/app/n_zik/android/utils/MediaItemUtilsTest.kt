package app.n_zik.android.utils

import android.app.Application
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.models.Info
import app.n_zik.android.models.Song
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `player replaces title placeholders and honors explicit Room edits`() {
        val placeholder = "Titre inconnu"
        assertEquals("Room title", resolvePlayerTrackName("Unknown Title", "Room title", placeholder, "Unknown Title"))
        assertEquals("Room title", resolvePlayerTrackName(placeholder, "Room title", placeholder, "Unknown Title"))
        assertEquals("My title", resolvePlayerTrackName("Queue title", "modified:My title", placeholder, "Unknown Title"))
        assertEquals("Queue title", resolvePlayerTrackName("Queue title", "Room title", placeholder, "Unknown Title"))
    }

    @Test
    fun `player replaces unknown credit with the resolved artist and preserves explicit Room names`() {
        val placeholder = "Artiste inconnu"
        val links = listOf(Info("UC0000000000000000000000", "Skyla Reeze"))
        assertEquals("Skyla Reeze", resolveTrackArtistName("Unknown Artist", placeholder, links, placeholder))
        assertEquals("Custom credit", resolveTrackArtistName("Old credit", "modified:Custom credit", links, placeholder))
        assertNull(resolveTrackArtistName(placeholder, "null", links + Info("UC1111111111111111111111", null), placeholder))
    }

    @Test
    fun `player uses exact track links over a stale album credit and preserves a fuller byline`() {
        val links = listOf(Info("UCKxZPmezUMRaV0rkbQnrtxA", "Yung Lean"))
        val placeholder = "Unknown Artist"
        assertEquals("Yung Lean", resolveTrackArtistName("Grand Theft Auto VI", "Grand Theft Auto VI", links, placeholder))
        assertEquals("Yung Lean, Another Artist", resolveTrackArtistName("Yung Lean, Another Artist", null, links, placeholder))
        assertEquals("My credit", resolveTrackArtistName("Grand Theft Auto VI", "modified:My credit", links, placeholder))
    }

    @Test
    fun `provider artist links work before the track is stored and honor a saved unlink`() {
        val providerId = "UC0000000000000000000000"
        val extras = Bundle().apply {
            putStringArrayList("artistIds", arrayListOf(providerId))
            putStringArrayList("artistNames", arrayListOf("Provider artist"))
            putStringArrayList(ARTIST_LINKED_NAMES_EXTRA, arrayListOf("Provider artist"))
        }
        val source = MediaItem.Builder().setMediaId("abcdefghijk")
            .setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build())
            .build()
        val unlinked = Info("LOCAL_ARTIST_custom", "Custom artist")

        assertEquals(listOf(Info(providerId, "Provider artist")), source.artistLinksWithStoredFallback(emptyList()) { null })
        assertEquals(listOf(unlinked), source.artistLinksWithStoredFallback(listOf(unlinked)) { providerId })
    }

    @Test
    fun `partial source pairs keep additional exact Room credits and their current names`() {
        val exactId = "UCKxZPmezUMRaV0rkbQnrtxA"
        val other = Info("UC0000000000000000000000", "Another Artist")
        val source = MediaItem.Builder().setMediaId("v6t1s8rDS9s")
            .setMediaMetadata(MediaMetadata.Builder().setExtras(Bundle().apply {
                putStringArrayList("artistNames", arrayListOf("Old artist", "Another Artist"))
                putStringArrayList("artistIds", arrayListOf(exactId))
                putStringArrayList(ARTIST_LINKED_NAMES_EXTRA, arrayListOf("Old artist"))
            }).build())
            .build()
        val stored = listOf(Info(exactId, "Current artist"), other)
        assertEquals(stored, source.artistLinksWithStoredFallback(stored) { null })
        assertEquals(
            "Current artist, Another Artist",
            resolveTrackArtistName("Old artist", "Current artist, Another Artist", stored.take(1), "Unknown Artist"),
        )
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
