package app.n_zik.android.core.matching

import android.app.Application
import android.app.Notification
import androidx.core.content.ContextCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.models.Song

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SongMatchNotificationTest {
    private val owner = ProfileStorageIdentity("default", "legacy")

    @Test
    fun `separate runs have distinct immutable cancel actions`() {
        val controller = Robolectric.buildService(SongMatchService::class.java).create()
        try {
            val service = controller.get()
            val old = service.cancelAction("old")
            val current = service.cancelAction("current")

            assertNotEquals(old, current)
            assertTrue(old.isImmutable)
            assertTrue(current.isImmutable)
            assertEquals("old", shadowOf(old).savedIntent.getStringExtra(SongMatchService.EXTRA_REQUEST_ID))
            assertEquals("current", shadowOf(current).savedIntent.getStringExtra(SongMatchService.EXTRA_REQUEST_ID))
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `terminal notification reports the outcome and only offers retry when there are candidates`() {
        val controller = Robolectric.buildService(SongMatchService::class.java).create()
        try {
            val service = controller.get()
            val strings = ContextCompat.getContextForLanguage(service).resources
            val initial = run("result").initialState
            val success = initial.copy(status = SongMatchStatus.Finished, matched = 2, merged = 1)
            val partial = initial.copy(
                status = SongMatchStatus.Finished,
                matched = 1,
                failed = 1,
                failedIds = setOf("retry"),
            )
            val cancelled = partial.copy(status = SongMatchStatus.Cancelled, unprocessed = 1)
            val failed = cancelled.copy(status = SongMatchStatus.Failed)
            val expected = listOf(
                success to strings.getString(R.string.song_matching_notification_completed, 2, 1),
                partial to strings.getString(R.string.song_matching_notification_partial, 1, 1, 0, 0),
                cancelled to strings.getString(R.string.song_matching_notification_cancelled, 1, 1, 1, 0),
                failed to strings.getString(R.string.song_matching_notification_failed, 1, 1, 1, 0),
            )
            for ((state, summary) in expected) {
                val notification = service.resultNotification(state, owner)
                assertEquals(summary, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
                val openApp = notification.contentIntent
                assertTrue(openApp.isImmutable)
                assertEquals(MainActivity::class.java.name, shadowOf(openApp).savedIntent.component?.className)
                if (state.failedIds.isEmpty()) {
                    assertTrue(notification.actions.isNullOrEmpty())
                } else {
                    val action = notification.actions.single().actionIntent
                    val intent = shadowOf(action).savedIntent
                    assertTrue(action.isImmutable)
                    assertEquals("retry", intent.data?.authority)
                    assertEquals(state.requestId, intent.getStringExtra(SongMatchService.EXTRA_REQUEST_ID))
                    assertEquals(owner, service.retryOwner(intent))
                }
            }
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `retry keeps the original result until promotion and rejects other requests and profile generations`() {
        val sessions = SongMatchSessions { it == owner }
        val original = checkNotNull(sessions.reserve(run("original")))
        sessions.accept(original)
        sessions.promoted(original)
        val failed = original.initialState.copy(status = SongMatchStatus.Finished, failedIds = setOf("retry"))
        sessions.finish(original, failed) { assertEquals(failed, it) }
        sessions.dismiss(original.id)

        assertNull(sessions.retryResult("different-request", owner))
        assertNull(sessions.retryResult(original.id, owner.copy(generation = "stale")))
        assertEquals(failed, sessions.retryResult(original.id, owner))
        val songs = listOf(Song.makePlaceholder("already-matched"), Song.makePlaceholder("retry"))
        val retry = checkNotNull(sessions.reserveRetry(failed, owner, songs))
        assertEquals(listOf("retry"), retry.request.songs.map(Song::id))
        assertEquals(original.request.playlistId, retry.request.playlistId)
        assertNull(sessions.retryResult(original.id, owner))

        sessions.rejectPromotion(retry)
        assertEquals(failed, sessions.retryResult(original.id, owner))
        val promotedRetry = checkNotNull(sessions.reserveRetry(failed, owner, songs))
        sessions.promoted(promotedRetry)
        sessions.finish(promotedRetry, promotedRetry.initialState.copy(status = SongMatchStatus.Finished)) {}
        assertNull(sessions.retryResult(original.id, owner))
    }

    private fun run(id: String) = SongMatchRun(
        id,
        owner,
        SongMatchRequest("playlist:1", listOf(Song.makePlaceholder("retry")), playlistId = 1L),
    )
}
