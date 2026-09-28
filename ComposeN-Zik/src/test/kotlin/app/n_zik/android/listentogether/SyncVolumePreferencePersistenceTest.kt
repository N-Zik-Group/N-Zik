package app.n_zik.android.listentogether

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the "Sync volume does not persist" report: the Listen Together
 * host-volume-sync toggle is seeded from the persisted preference on
 * [ListenTogetherManager] construction (default ON = pre-fix behavior, zero regression)
 * and written back by [ListenTogetherManager.setSyncHostVolumeEnabled], so the choice
 * survives an app restart instead of always resetting to ON.
 *
 * Robolectric is required (not a plain JVM unit test) because [DataStoreUtils] reads
 * `Context.getSharedPreferences`, which needs a real Android environment to shadow.
 * The client is a relaxed MockK mock so its constructor (WebSocket, notification
 * channel, session restore) never runs — the test only exercises the manager's
 * preference round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncVolumePreferencePersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newManager(): ListenTogetherManager =
        ListenTogetherManager(mockk(relaxed = true), context)

    @Test
    fun `init seeds syncHostVolumeEnabled from the persisted preference`() {
        DataStoreUtils.saveBoolean(context, ListenTogetherManager.PREF_SYNC_HOST_VOLUME, false)
        assertEquals(false, newManager().syncHostVolumeEnabled.value)
    }

    @Test
    fun `missing preference falls back to the default (sync enabled)`() {
        assertEquals(true, newManager().syncHostVolumeEnabled.value)
    }

    @Test
    fun `setSyncHostVolumeEnabled writes the value back so a fresh manager reads it`() {
        val manager = newManager()
        manager.setSyncHostVolumeEnabled(false)
        assertEquals(
            false,
            DataStoreUtils.getBoolean(context, ListenTogetherManager.PREF_SYNC_HOST_VOLUME, true),
        )
        // Simulated restart: a new manager instance must see the persisted OFF state
        assertEquals(false, newManager().syncHostVolumeEnabled.value)
        manager.setSyncHostVolumeEnabled(true)
        assertEquals(
            true,
            DataStoreUtils.getBoolean(context, ListenTogetherManager.PREF_SYNC_HOST_VOLUME, true),
        )
    }
}
