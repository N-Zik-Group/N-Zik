package app.n_zik.android.playback.utils

import app.n_zik.android.components.ui.collectionTrackRows
import app.n_zik.android.components.ui.screens.player.canSeedQueueRecommendations
import app.n_zik.android.components.ui.screens.player.insertQueueRecommendations
import app.n_zik.android.utils.withPodcastSource

import android.app.Application
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.menu.player.playerMenuMediaItem
import app.n_zik.android.core.database.Converters
import app.n_zik.android.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SmartShuffleRecommendationsTest {
    @Test fun `collection rows interleave recommendations without reordering originals`() {
        val originals = (1..10).map { "source-$it" }
        val recommendations = (1..5).map { Song.makePlaceholder("recommendation-$it") }
        val rows = collectionTrackRows(originals, recommendations)

        assertEquals(originals, rows.mapNotNull { it.original?.value })
        assertEquals(recommendations.toSet(), rows.mapNotNull { it.recommendation }.toSet())
        val firstOriginal = rows.indexOfFirst { it.original != null }
        val lastOriginal = rows.indexOfLast { it.original != null }
        assertTrue(rows.subList(firstOriginal + 1, lastOriginal).any { it.recommendation != null })
        assertEquals(rows, collectionTrackRows(originals, recommendations))
    }

    @Test fun `queue mixes new items in one tail while keeping the original order`() {
        val original = (1..30).map { MediaItem.Builder().setMediaId("song-$it").build() }
        val additions = (1..5).map { MediaItem.Builder().setMediaId("rec-$it").build().asSmartShuffleRecommendation(true) }
        val result = insertQueueRecommendations(original, additions)
        assertEquals(original, result.filterNot { it.isSmartShuffleRecommendation })
        assertEquals(additions.toSet(), result.filter { it.isSmartShuffleRecommendation }.toSet())
    }

    @Test fun `queue ignores podcast and recommendation items as seeds`() {
        val original = MediaItem.Builder().setMediaId("example-id").build()
        assertEquals(true, original.canSeedQueueRecommendations())
        assertEquals(false, original.withPodcastSource("MPSP-example").canSeedQueueRecommendations())
        assertEquals(false, original.asSmartShuffleRecommendation(true).canSeedQueueRecommendations())
    }

    @Test
    fun `smart shuffle glyphs appear before the explicit badge`() {
        val title = "Track"
        assertEquals("🔀✨ 🅴 Track", smartShuffleSessionTitle(title, explicit = true))
        assertEquals("🔀✨ Track", smartShuffleSessionTitle(title, explicit = false))
        assertEquals("🅴 Track", smartShuffleSessionTitle(title, explicit = true).withoutSmartShuffleSessionPrefix())
    }

    @Test
    fun `display positions balance gaps and retain sequential insertion order`() {
        val songs = (0 until 20).toList()
        val recommendations = (20 until 25).toList()
        val positions = smartShuffleInsertionPositions(songs.size, recommendations, Random(42))
        assertEquals(positions.values.sortedDescending(), positions.values.toList())

        val projected = songs.toMutableList()
        positions.forEach { (id, position) -> projected.add(position, id) }
        assertEquals(songs, projected.filter { it in songs })
        assertEquals(recommendations.toSet(), projected.filter { it in recommendations }.toSet())
        val indices = projected.indices.filter { projected[it] in recommendations }
        assertTrue(indices.zipWithNext().all { (previous, next) -> next - previous in 5..6 })
    }

    @Test
    fun `shuffle spreads a dense recommendation set across every available gap`() {
        val songs = listOf(MediaItem.fromUri("a"), MediaItem.fromUri("b"))
        val recommendations = (0 until 8).map { index ->
            MediaItem.fromUri("recommendation-$index").asSmartShuffleRecommendation(true)
        }
        val spread = spreadSmartShuffleRecommendations(recommendations + songs, Random(42))
        assertEquals(songs, spread.filterNot(MediaItem::isSmartShuffleRecommendation))
        assertEquals(recommendations, spread.filter(MediaItem::isSmartShuffleRecommendation))

        val gaps = listOf(-1, spread.indexOf(songs[0]), spread.indexOf(songs[1]), spread.size)
        assertTrue(gaps.zipWithNext().all { (previous, next) -> next - previous in 3..4 })
        assertSame(songs, spreadSmartShuffleRecommendations(songs, Random(42)))
    }

    @Test
    fun `recommendations that have joined the collection become regular rows`() {
        val song = Song(id = "same", title = "Stored title", durationText = null, thumbnailUrl = null)
        val suggested = song.copy(title = "Initial title")
        val recommended = Song(id = "new", title = "Recommendation", durationText = null, thumbnailUrl = null)
        val positions = mapOf(suggested to 0, recommended to 1)
        assertEquals(setOf(recommended.id), smartShuffleRecommendationIds(listOf(song), positions))
    }

    @Test
    fun `recommendation marker survives menu reconstruction and persisted queue round trip`() {
        val original = MediaItem.Builder()
            .setMediaId("abcdefghijk")
            .setUri("abcdefghijk")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Old title")
                    .setExtras(Bundle().apply { putString("albumId", "MPREbExample") })
                    .build()
            )
            .build()

        val marked = original.asSmartShuffleRecommendation(true)
        assertFalse(original.isSmartShuffleRecommendation)
        assertTrue(marked.isSmartShuffleRecommendation)
        assertEquals(original.mediaId, marked.mediaId)
        assertEquals(original.localConfiguration, marked.localConfiguration)
        assertSame(marked, marked.asSmartShuffleRecommendation(true))

        val song = Song(id = original.mediaId, title = "New title", durationText = null, thumbnailUrl = null)
        val menuItem = playerMenuMediaItem(marked, song)
        val restored = Converters.mediaItemFromByteArray(Converters.mediaItemToByteArray(menuItem))
        assertNotNull(restored)
        assertTrue(restored?.isSmartShuffleRecommendation == true)
        assertEquals("MPREbExample", restored?.mediaMetadata?.extras?.getString("albumId"))
        assertEquals("New title", restored?.mediaMetadata?.title)
    }
}
