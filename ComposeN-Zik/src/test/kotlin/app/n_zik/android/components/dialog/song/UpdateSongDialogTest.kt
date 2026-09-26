package app.n_zik.android.components.dialog.song

import app.it.fast4x.rimusic.models.Song
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure decision tests for [applySongUpdate] and [shouldReconcileAuthors] -
 * the two extracted, testable cores of the song « Update » dialog.
 * No Compose, no database, no network: plain JUnit 5.
 */
class UpdateSongDialogTest {

    private fun author(name: String, browseId: String? = null) =
        Innertube.Info<NavigationEndpoint.Endpoint.Browse>(
            name = name,
            endpoint = browseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
        )

    private fun storedSong() = Song(
        id = "video_1",
        title = "Stored title",
        artistsText = "Stored artist",
        durationText = "3:00",
        thumbnailUrl = "https://stored/thumb",
        likedAt = 123L,
        totalPlayTimeMs = 4567L,
        position = 4,
        isYoutubeSong = true
    ).apply {
        // non-zero on purpose: the playtime-reset test must prove the dialog
        // PRESERVES it, not merely re-emit the model default (playCount is a
        // body property - set after construction)
        playCount = 5
    }

    private fun fetchedSong(
        title: String = "Fetched title",
        artistsText: String? = "Fetched artist",
        thumbnailUrl: String? = "https://fetched/thumb"
    ) = Song(
        id = "video_1",
        title = title,
        artistsText = artistsText,
        durationText = "4:00",
        thumbnailUrl = thumbnailUrl
    )

    // region applySongUpdate

    @Test
    fun titleOnlyUpdateChangesOnlyTheTitle() {
        val updated = applySongUpdate(
            storedSong(),
            fetchedSong(),
            SongUpdateSelection(title = true)
        )

        assertEquals("Fetched title", updated.title)
        assertEquals("Stored artist", updated.artistsText)
        assertEquals("https://stored/thumb", updated.thumbnailUrl)
        assertEquals("3:00", updated.durationText)
        assertEquals(4567L, updated.totalPlayTimeMs)
        assertEquals(123L, updated.likedAt)
    }

    @Test
    fun authorsOnlyUpdateChangesOnlyArtistsText() {
        val updated = applySongUpdate(
            storedSong(),
            fetchedSong(),
            SongUpdateSelection(authors = true)
        )

        assertEquals("Stored title", updated.title)
        assertEquals("Fetched artist", updated.artistsText)
        assertEquals("https://stored/thumb", updated.thumbnailUrl)
    }

    @Test
    fun thumbnailOnlyUpdateChangesOnlyTheThumbnail() {
        val updated = applySongUpdate(
            storedSong(),
            fetchedSong(),
            SongUpdateSelection(thumbnail = true)
        )

        assertEquals("Stored title", updated.title)
        assertEquals("Stored artist", updated.artistsText)
        assertEquals("https://fetched/thumb", updated.thumbnailUrl)
    }

    @Test
    fun modifiedTitleIsPreserved() {
        val stored = storedSong().copy(title = "modified:Custom")

        val updated = applySongUpdate(
            stored,
            fetchedSong(title = "Fetched title"),
            SongUpdateSelection(title = true)
        )

        assertEquals("modified:Custom", updated.title)
    }

    @Test
    fun modifiedArtistsTextIsPreserved() {
        val stored = storedSong().copy(artistsText = "modified:Custom")

        val updated = applySongUpdate(
            stored,
            fetchedSong(artistsText = "Fetched artist"),
            SongUpdateSelection(authors = true)
        )

        assertEquals("modified:Custom", updated.artistsText)
    }

    @Test
    fun modifiedThumbnailIsPreserved() {
        val stored = storedSong().copy(thumbnailUrl = "modified:https://custom/thumb")

        val updated = applySongUpdate(
            stored,
            fetchedSong(thumbnailUrl = "https://fetched/thumb"),
            SongUpdateSelection(thumbnail = true)
        )

        assertEquals("modified:https://custom/thumb", updated.thumbnailUrl)
    }

    @Test
    fun fetchedThumbnailIsAppliedWhenStoredValueIsPlain() {
        val updated = applySongUpdate(
            storedSong(),
            fetchedSong(thumbnailUrl = "https://fetched/thumb"),
            SongUpdateSelection(thumbnail = true)
        )

        assertEquals("https://fetched/thumb", updated.thumbnailUrl)
    }

    @Test
    fun nullFetchedThumbnailKeepsStoredValue() {
        // A null fetched field must not clobber the stored value:
        // retainIfModified(stored, null) returns null for a plain stored value.
        val updated = applySongUpdate(
            storedSong(),
            fetchedSong(thumbnailUrl = null),
            SongUpdateSelection(thumbnail = true)
        )

        assertEquals("https://stored/thumb", updated.thumbnailUrl)
    }

    @Test
    fun nullFetchedArtistsTextKeepsStoredValue() {
        val updated = applySongUpdate(
            storedSong(),
            fetchedSong(artistsText = null),
            SongUpdateSelection(authors = true)
        )

        assertEquals("Stored artist", updated.artistsText)
    }

    @Test
    fun playtimeResetZerosTotalPlayTimeOnly() {
        val updated = applySongUpdate(
            storedSong(),
            fetchedSong(),
            SongUpdateSelection(playtime = true)
        )

        assertEquals(0L, updated.totalPlayTimeMs)
        assertEquals("Stored title", updated.title)
        assertEquals("Stored artist", updated.artistsText)
        assertEquals("https://stored/thumb", updated.thumbnailUrl)
        // playCount is a var the dialog never touches: the stored non-zero
        // value must survive the playtime reset
        assertEquals(5, updated.playCount)
    }

    @Test
    fun noCheckedBoxLeavesTheSongUnchanged() {
        val updated = applySongUpdate(
            storedSong(),
            fetchedSong(),
            SongUpdateSelection()
        )

        assertEquals(storedSong(), updated)
    }

    @Test
    fun nullFetchWritesNoFetchedField() {
        val updated = applySongUpdate(
            storedSong(),
            null,
            SongUpdateSelection(title = true, authors = true, thumbnail = true, playtime = true)
        )

        assertEquals("Stored title", updated.title)
        assertEquals("Stored artist", updated.artistsText)
        assertEquals("https://stored/thumb", updated.thumbnailUrl)
        // the playtime reset is local to the dialog, no fetch involved
        assertEquals(0L, updated.totalPlayTimeMs)
    }

    // endregion

    // region shouldReconcileAuthors

    @Test
    fun completeListWithPlainArtistsTextReconciles() {
        val authors = listOf(author("A", "UC_A"), author("B", "UC_B"))

        assertTrue(shouldReconcileAuthors(true, "Stored artist", listOf("A", "B"), authors))
    }

    @Test
    fun modifiedArtistsTextNeverReconciles() {
        val authors = listOf(author("A", "UC_A"))

        assertFalse(shouldReconcileAuthors(true, "modified:Custom", listOf("A"), authors))
    }

    @Test
    fun namesOnlyListNeverReconciles() {
        val authors = listOf(author("A"), author("B"))

        assertFalse(shouldReconcileAuthors(true, "A, B", listOf("A", "B"), authors))
    }

    @Test
    fun nullAuthorsNeverReconciles() {
        assertFalse(shouldReconcileAuthors(true, "A", listOf("A"), null))
    }

    @Test
    fun emptyParsedNamesNeverReconciles() {
        val authors = listOf(author("A", "UC_A"))

        assertFalse(shouldReconcileAuthors(true, "A", emptyList(), authors))
    }

    @Test
    fun untickedAuthorsBoxNeverReconciles() {
        val authors = listOf(author("A", "UC_A"))

        assertFalse(shouldReconcileAuthors(false, "A", listOf("A"), authors))
    }

    // endregion
}
