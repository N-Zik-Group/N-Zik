package app.n_zik.android.components.menu.playlist

import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistPickerResultsTest {
    @Test
    fun `a completed empty read resolves successfully`() = runTest {
        val result = playlistPickerResults(flowOf(emptyList())).first()

        assertEquals(emptyList<PlaylistPreview>(), result.getOrThrow())
    }

    @Test
    fun `a read failure resolves separately from an empty library`() = runTest {
        val failure = IllegalStateException("unavailable")
        val result = playlistPickerResults(flow { throw failure }).first()

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
        val result = runCatching { playlistPickerResults(flow { throw cancellation }).first() }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
