package app.n_zik.android.components.menu.playlist

import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.ui.components.themed.isPinnedPlaylistPickerVisible
import app.n_zik.android.ui.components.themed.isPlaylistPickerTarget
import app.n_zik.android.ui.components.themed.playlistPickerSections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistPickerResultsTest {
    @Test
    fun `a completed empty read resolves successfully`() = runTest {
        val result = playlistPickerResults(flowOf(emptyList<PlaylistPreview>())).first()

        assertEquals(emptyList<PlaylistPreview>(), result.getOrThrow())
    }

    @Test
    fun `pending device scan keeps the picker loading and preserves library errors`() {
        val previews = PlaylistPickerPreviews(emptyList())
        val success = Result.success(previews)
        val failure = IllegalStateException("unavailable")

        assertNull(PlaylistPickerRead(success, emptySet(), deviceScanPending = true) {}.result)
        assertEquals(previews, PlaylistPickerRead(success, emptySet(), deviceScanPending = false) {}.result?.getOrThrow())
        assertSame(
            failure,
            PlaylistPickerRead(Result.failure(failure), emptySet(), deviceScanPending = true) {}.result?.exceptionOrNull(),
        )
    }

    @Test
    fun `a read failure resolves separately from an empty library`() = runTest {
        val failure = IllegalStateException("unavailable")
        val result = playlistPickerResults(flow<List<PlaylistPreview>> { throw failure }).first()

        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `later snapshots replace the earlier playlist list`() = runTest {
        val preview = PlaylistPreview(Playlist(name = "Library playlist"), 2)
        val results = playlistPickerResults(flowOf(emptyList(), listOf(preview))).toList()

        assertEquals(listOf(emptyList<PlaylistPreview>(), listOf(preview)), results.map { it.getOrThrow() })
    }

    @Test
    fun `cancelling the read does not emit a failure status`() = runTest {
        val cancellation = CancellationException("picker closed")
        val result = runCatching { playlistPickerResults(flow<List<PlaylistPreview>> { throw cancellation }).first() }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `a pinned locked playlist stays visible online and offline`() {
        val locked = Playlist(
            name = "${PINNED_PREFIX}Collection",
            browseId = "VLPLcollection",
            isEditable = false,
            isYoutubePlaylist = true,
        )

        assertTrue(isPinnedPlaylistPickerVisible(locked, online = true))
        assertTrue(isPinnedPlaylistPickerVisible(locked, online = false))
        assertFalse(isPlaylistPickerTarget(locked, localDestinationsOnly = false))
        assertFalse(isPinnedPlaylistPickerVisible(locked.copy(isEditable = true), online = false))
        assertFalse(isPinnedPlaylistPickerVisible(locked.copy(name = "Collection"), online = true))
    }

    @Test
    fun `saved podcasts appear with YouTube collections and stay locked`() {
        val podcast = PlaylistPreview(Playlist(
            name = "Saved podcast",
            browseId = "MPSPPLexample",
            isEditable = false,
            isYoutubePlaylist = false,
        ), 31)
        val local = PlaylistPreview(Playlist(name = "Local"), 5)

        val sections = playlistPickerSections(listOf(podcast, local), online = false)

        assertEquals(listOf(podcast), sections.youtube)
        assertEquals(listOf(local), sections.local)
        assertFalse(isPlaylistPickerTarget(podcast.playlist, localDestinationsOnly = false))
    }

    @Test
    fun `device membership cannot reintroduce an offline YouTube destination`() {
        val local = PlaylistPreview(Playlist(id = 1, name = "Device collection"), 1)
        val linked = PlaylistPreview(Playlist(
            id = 2,
            name = "YouTube collection",
            browseId = "VLPLcollection",
            isYoutubePlaylist = true,
        ), 1)
        val previews = listOf(local, linked)
        val ids = setOf(local.playlist.id, linked.playlist.id)

        val online = playlistPickerSections(
            previews, online = true, devicePlaylistIds = ids, availableDevicePlaylistIds = ids,
        )
        assertEquals(listOf(local), online.device)
        assertEquals(listOf(linked), online.youtube)

        val offline = playlistPickerSections(
            previews, online = false, devicePlaylistIds = ids, availableDevicePlaylistIds = ids,
        )
        assertEquals(listOf(local), offline.all)
        assertEquals(listOf(local), offline.device)
    }

    @Test
    fun `the device tab follows the current scan and All retains its library rows`() {
        val mixed = PlaylistPreview(Playlist(id = 1, name = "Mixed tracks"), 2)
        val unavailable = PlaylistPreview(Playlist(id = 2, name = "Missing local file"), 1)
        val ordinary = PlaylistPreview(Playlist(id = 3, name = "Online tracks"), 1)
        val library = listOf(unavailable, mixed, ordinary)

        val sections = playlistPickerSections(
            library,
            online = true,
            devicePlaylistIds = setOf(unavailable.playlist.id),
            availableDevicePlaylistIds = setOf(mixed.playlist.id),
        )

        assertEquals(listOf(mixed), sections.device)
        assertEquals(library, sections.all)
    }

    @Test
    fun `picker search matches the displayed name without storage prefixes`() {
        val playlist = Playlist(name = "${PINNED_PREFIX}Late Night")

        assertTrue(playlist.matchesPickerQuery("LATE NIGHT"))
        assertTrue(playlist.matchesPickerQuery(""))
        assertFalse(playlist.matchesPickerQuery("pinned"))
    }
}
