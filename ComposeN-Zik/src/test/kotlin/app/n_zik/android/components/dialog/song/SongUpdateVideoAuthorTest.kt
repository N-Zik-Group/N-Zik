package app.n_zik.android.components.dialog.song

import java.io.IOException

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.PlayerResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.asSong
import app.n_zik.android.core.database.ArtistMappingReconcile

/** Uses runBlocking to await suspend provider calls from synchronous JUnit tests. */
class SongUpdateVideoAuthorTest {
    @org.junit.After
    fun tearDown() = io.mockk.unmockkAll()

    @Test
    fun `missing user video artist resets to the validated channel author`() = runBlocking {
        val original = item()
        var requests = 0
        val result = requireNotNull(withVideoAuthorFallback(VIDEO_ID, original, true) { id ->
            assertEquals(VIDEO_ID, id)
            requests++
            details()
        })
        assertEquals(1, requests)
        assertEquals("tosh", result.asSong.artistsText)
        assertEquals(original.copy(authors = result.authors), result)
        val entries = ArtistMappingReconcile.parseAuthorEntries(result.authors)
        assertEquals(listOf("tosh" to CHANNEL_ID), entries)
        assertTrue(shouldReconcileAuthors(true, entries.map { it.first }, result.authors))
        val stored = Song(VIDEO_ID, "Title", "modified:toshdfdfdff", null, null)
        assertEquals("tosh", applySongUpdate(stored, result.asSong, SongUpdateSelection(authors = true)).artistsText)
    }

    @Test
    fun `unchecked authors missing song and mismatched song never request player`() = runBlocking {
        val original = item()
        val unexpected: suspend (String) -> PlayerResponse.VideoDetails? = { error("Unexpected request") }
        assertSame(original, withVideoAuthorFallback(VIDEO_ID, original, false, fetchDetails = unexpected))
        assertEquals(null, withVideoAuthorFallback(VIDEO_ID, null, true, fetchDetails = unexpected))
        assertSame(original, withVideoAuthorFallback("abcdefghijk", original, true, fetchDetails = unexpected))
    }

    @Test
    fun `complete and partial provider artist credits are never replaced`() = runBlocking {
        for (type in listOf("MUSIC_VIDEO_TYPE_ATV", "MUSIC_VIDEO_TYPE_OMV", "MUSIC_VIDEO_TYPE_UGC")) {
            for (authors in listOf(
                listOf(author("Artist One", CHANNEL_ID), author("Artist Two", "UC1234567890123456789012")),
                listOf(author("Artist One", null)),
            )) {
                val original = item(type).copy(authors = authors)
                assertSame(original, withVideoAuthorFallback(VIDEO_ID, original, true) { error("Unexpected request") })
            }
        }
    }

    @Test
    fun `missing music recording and official video credits use validated player authors`() = runBlocking {
        for (type in listOf("MUSIC_VIDEO_TYPE_ATV", "MUSIC_VIDEO_TYPE_OMV")) {
            val original = item(type)
            val result = withVideoAuthorFallback(VIDEO_ID, original, true) { details().copy(musicVideoType = type) }
            assertEquals("tosh", result?.asSong?.artistsText)
        }
    }

    @Test
    fun `actual recording with endpointless queue author restores the player artist`() = runBlocking {
        val videoId = "ZTf4xFOyiKU"
        val channelId = "UCoEBY9_wiuzqcEYFivpdsAA"
        val original = item("MUSIC_VIDEO_TYPE_ATV", videoId)
        val result = requireNotNull(withVideoAuthorFallback(videoId, original, true) {
            details().copy(
                videoId = videoId,
                author = "かめりあ(EDP)",
                channelId = channelId,
                musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
            )
        })
        assertEquals(listOf("かめりあ(EDP)" to channelId), ArtistMappingReconcile.parseAuthorEntries(result.authors))
        val stored = Song(videoId, "Title", "modified:Custom artist", null, null)
        assertEquals("かめりあ(EDP)", applySongUpdate(stored, result.asSong, SongUpdateSelection(authors = true)).artistsText)
    }

    @Test
    fun `unsupported content types never request a channel fallback`() = runBlocking {
        for (type in listOf("MUSIC_VIDEO_TYPE_PODCAST_EPISODE", "UNKNOWN")) {
            val original = item(type)
            assertSame(original, withVideoAuthorFallback(VIDEO_ID, original, true) { error("Unexpected request") })
        }
    }

    @Test
    fun `unknown queue type needs a supported video type from player`() = runBlocking {
        val original = item(null)
        for (type in listOf("MUSIC_VIDEO_TYPE_ATV", "MUSIC_VIDEO_TYPE_OMV", "MUSIC_VIDEO_TYPE_UGC")) {
            assertEquals("tosh", withVideoAuthorFallback(VIDEO_ID, original, true) { details().copy(musicVideoType = type) }?.asSong?.artistsText)
        }
        assertSame(original, withVideoAuthorFallback(VIDEO_ID, original, true) { details().copy(musicVideoType = null) })
    }

    @Test
    fun `invalid or conflicting player metadata preserves the existing custom author`() = runBlocking {
        val original = item()
        val invalid = listOf(
            null,
            details().copy(videoId = "abcdefghijk"),
            details().copy(videoId = null),
            details().copy(author = " "),
            details().copy(author = null),
            details().copy(channelId = null),
            details().copy(channelId = "not-a-channel"),
            details().copy(musicVideoType = "MUSIC_VIDEO_TYPE_ATV"),
            details().copy(musicVideoType = "MUSIC_VIDEO_TYPE_OMV"),
            details().copy(musicVideoType = "UNKNOWN"),
        )
        val stored = Song(VIDEO_ID, "Title", "modified:toshdfdfdff", null, null)
        for (response in invalid) {
            val result = withVideoAuthorFallback(VIDEO_ID, original, true) { response }
            assertSame(original, result)
            assertTrue(result?.asSong?.artistsText.isNullOrBlank())
            assertEquals(stored.artistsText, applySongUpdate(stored, result?.asSong, SongUpdateSelection(authors = true)).artistsText)
            assertFalse(shouldReconcileAuthors(true, emptyList(), result?.authors))
        }
    }

    @Test
    fun `network failure preserves the missing provider data`() = runBlocking {
        val original = item()
        assertSame(original, withVideoAuthorFallback(VIDEO_ID, original, true) { throw IOException("Offline") })
    }

    @Test
    fun `cancellation propagates unchanged`() = runBlocking {
        val cancelled = CancellationException("Cancelled update")
        try {
            withVideoAuthorFallback(VIDEO_ID, item(), true) { throw cancelled }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }

    @Test
    fun `fallback network failure is reported and wrapped cancellation propagates`() = runBlocking {
        val issues = mutableListOf<SongUpdateIssue>()
        val original = item()
        assertSame(original, withVideoAuthorFallback(VIDEO_ID, original, true, onFailure = { issues += it }) {
            throw IOException("Offline")
        })
        assertEquals(listOf(SongUpdateIssue.NETWORK), issues)
        val cancellation = CancellationException("Cancelled update")
        try {
            withVideoAuthorFallback(VIDEO_ID, original, true, onFailure = { issues += it }) {
                throw IOException(cancellation)
            }
            fail("Wrapped cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(listOf(SongUpdateIssue.NETWORK), issues)
    }

    @Test
    fun `fallback HTTP failure is unavailable metadata rather than missing artists alone`() = runBlocking {
        val response = io.mockk.mockk<io.ktor.client.statement.HttpResponse>(relaxed = true)
        io.mockk.every { response.status } returns io.ktor.http.HttpStatusCode.NotFound
        val issues = mutableListOf<SongUpdateIssue>()
        val original = item()
        assertSame(original, withVideoAuthorFallback(VIDEO_ID, original, true, onFailure = { issues += it }) {
            throw io.ktor.client.plugins.ClientRequestException(response, "Not Found")
        })
        assertEquals(listOf(SongUpdateIssue.METADATA_UNAVAILABLE), issues)
    }

    private fun item(type: String? = "MUSIC_VIDEO_TYPE_UGC", videoId: String = VIDEO_ID) = Innertube.SongItem(
        info = Innertube.Info(
            name = "Death Corridor",
            endpoint = NavigationEndpoint.Endpoint.Watch(
                videoId = videoId,
                watchEndpointMusicSupportedConfigs = NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs(
                    NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig(type),
                ),
            ),
        ),
        authors = emptyList(),
        album = null,
        durationText = "2:00",
        thumbnail = null,
    )

    private fun author(name: String, id: String?) = Innertube.Info(
        name = name,
        endpoint = NavigationEndpoint.Endpoint.Browse(browseId = id),
    )

    private fun details() = PlayerResponse.VideoDetails(
        videoId = VIDEO_ID, title = "Death Corridor", author = "tosh", channelId = CHANNEL_ID,
        authorAvatar = null, authorSubCount = null, lengthSeconds = "120", musicVideoType = "MUSIC_VIDEO_TYPE_UGC",
        viewCount = null, thumbnail = null, description = null,
    )

    private companion object {
        const val VIDEO_ID = "QDu-h7sO1YA"
        const val CHANNEL_ID = "UCpf42a3Bz4M9AdRDBXukneQ"
    }
}
