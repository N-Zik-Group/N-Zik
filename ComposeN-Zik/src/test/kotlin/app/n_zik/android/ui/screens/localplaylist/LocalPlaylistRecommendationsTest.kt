package app.n_zik.android.ui.screens.localplaylist

import java.io.IOException

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import app.n_zik.android.enums.RecommendationsNumber
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.relatedSongs

class LocalPlaylistRecommendationsTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `recommendations keep first occurrences in fetch order across three seeds and a failed request`() = runTest {
        val remoteTracks = List(101) { index -> Song.makePlaceholder("playlist-$index") }
        val playlist = remoteTracks + List(20) { Song.makePlaceholder("$LOCAL_KEY_PREFIX$it") }
        val playlistIds = remoteTracks.map(Song::id).toSet()
        val seeds = mutableListOf<String>()
        val requestTimes = mutableListOf<Long>()
        val relatedSongsClass = "app.n_zik.innertube.requests.RelatedSongsKt"

        mockkStatic(relatedSongsClass)
        try {
            coEvery { Innertube.relatedSongs(any()) } coAnswers {
                seeds += secondArg<String>()
                requestTimes += testScheduler.currentTime
                when (seeds.size) {
                    1 -> response(
                        candidate(playlist.first().id),
                        candidate("z-first", "Original title"),
                        candidate("b-second"),
                    )
                    2 -> mockResult(Result.failure(IOException("Network unavailable")))
                    3 -> response(
                        candidate("z-first", "Duplicate title"),
                        candidate("a-third"),
                        candidate("fourth"),
                        candidate("fifth"),
                        candidate("beyond-limit"),
                    )
                    else -> error("Unexpected extra recommendation request")
                }
            }

            val recommendations = loadLocalPlaylistRecommendations(playlist, RecommendationsNumber.`5`)

            assertEquals(3, seeds.toSet().size)
            assertTrue(seeds.all { it in playlistIds })
            assertEquals(listOf(0L, 200L, 400L), requestTimes)
            assertEquals(
                listOf("z-first", "b-second", "a-third", "fourth", "fifth"),
                recommendations.keys.map(Song::id),
            )
            assertEquals("Original title", recommendations.keys.first().title)
            assertTrue(recommendations.values.all { it in 0..playlist.size })
        } finally {
            unmockkStatic(relatedSongsClass)
        }
    }

    @Test
    fun `empty and on-device playlists make no recommendation request`() = runTest {
        val relatedSongsClass = "app.n_zik.innertube.requests.RelatedSongsKt"
        mockkStatic(relatedSongsClass)
        try {
            val localTrack = Song.makePlaceholder("${LOCAL_KEY_PREFIX}1")
            assertTrue(loadLocalPlaylistRecommendations(emptyList(), RecommendationsNumber.`5`).isEmpty())
            assertTrue(loadLocalPlaylistRecommendations(listOf(localTrack), RecommendationsNumber.`5`).isEmpty())
            coVerify(exactly = 0) { Innertube.relatedSongs(videoId = any()) }
        } finally {
            unmockkStatic(relatedSongsClass)
        }
    }

    private fun response(vararg candidates: Innertube.SongItem): Result<Innertube.RelatedSongs?>? =
        mockResult(Result.success(Innertube.RelatedSongs(candidates.toList())))

    // MockK unwraps one Result layer for suspend answers, while relatedSongs returns a boxed nullable Result.
    @Suppress("UNCHECKED_CAST")
    private fun mockResult(value: Result<Innertube.RelatedSongs?>): Result<Innertube.RelatedSongs?>? =
        Result.success(value) as Result<Innertube.RelatedSongs?>?

    private fun candidate(id: String, title: String = id) = Innertube.SongItem(
        info = Innertube.Info(name = title, endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)),
        authors = null,
        album = null,
        durationText = null,
        thumbnail = null,
    )
}
