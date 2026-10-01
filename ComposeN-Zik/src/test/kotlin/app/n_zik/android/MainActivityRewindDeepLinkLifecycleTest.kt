package app.n_zik.android

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.utils.DataStoreUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * App OFF / app ON lifecycle contract of the rewind notification deep links (spec GH-275
 * follow-up), tested on the real [MainActivity] under Robolectric:
 *
 * - app OFF (cold start): [MainActivity.onCreate] consumes the launch intent's targets
 *   synchronously — an unconsumed target is accepted, recorded in its persistent
 *   last-consumed marker, and the extras are stripped off the kept intent;
 * - app ON (warm start): [MainActivity.onNewIntent] shares the exact same consumption
 *   contract;
 * - re-delivered intent (singleTask relaunch, or the task record re-sending the original
 *   launch intent after a process-death restore): an already-consumed target is rejected —
 *   the deck / playlist open once per notification tap, not on every relaunch (the
 *   device-observed leak where the playlist notification re-opened the deck).
 *
 * robolectric.properties pins a plain Application, so MainApplication is never created:
 * no worker scheduling, cookie fetching or service binding runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityRewindDeepLinkLifecycleTest {

    private fun deckIntent(year: Int, month: Int): Intent =
        Intent().apply {
            putExtra(RewindReminderWorker.EXTRA_DECK_YEAR, year)
            putExtra(RewindReminderWorker.EXTRA_DECK_MONTH, month)
        }

    private fun yearlyDeckIntent(year: Int): Intent =
        Intent().apply { putExtra(RewindReminderWorker.EXTRA_DECK_YEAR, year) }

    private fun playlistIntent(playlistId: Long): Intent =
        Intent().apply { putExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID, playlistId) }

    @Before
    fun resetConsumptionMarkers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataStoreUtils.saveInt(context, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0)
        DataStoreUtils.saveLong(context, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 0L)
    }

    // ---- App OFF: cold start, onCreate consumes the launch intent ----

    @Test
    fun appOff_coldStartWithReminderExtrasAcceptsTheDeckTargetRecordsTheMarkerAndStripsTheExtras() {
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, deckIntent(2026, 9))
            .create()
            .get()
        assertEquals(2026 to 9, activity.rewindDeckTarget)
        assertEquals(
            202609,
            DataStoreUtils.getInt(activity, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0)
        )
        assertFalse(activity.intent.hasExtra(RewindReminderWorker.EXTRA_DECK_YEAR))
        assertFalse(activity.intent.hasExtra(RewindReminderWorker.EXTRA_DECK_MONTH))
    }

    @Test
    fun appOff_coldStartWithYearlyReminderExtrasAcceptsTheYearlyTarget() {
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, yearlyDeckIntent(2026))
            .create()
            .get()
        assertEquals(2026 to 0, activity.rewindDeckTarget)
        assertEquals(
            202600,
            DataStoreUtils.getInt(activity, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0)
        )
    }

    @Test
    fun appOff_coldStartWithAnAlreadyConsumedDeckTargetIsRejected() {
        // Simulates the task record re-sending the original launch intent (extras intact)
        // after a process-death restore: the deck was already opened for this target, so
        // the re-delivery must NOT re-open it (the leak the user observed).
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataStoreUtils.saveInt(context, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 202609)
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, deckIntent(2026, 9))
            .create()
            .get()
        assertNull(activity.rewindDeckTarget)
        assertEquals(
            202609,
            DataStoreUtils.getInt(activity, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0)
        )
        // The rejected intent must not keep its extras alive for a later re-delivery either.
        assertFalse(activity.intent.hasExtra(RewindReminderWorker.EXTRA_DECK_YEAR))
    }

    @Test
    fun appOff_coldStartWithASubsequentCycleTargetIsAcceptedDespiteThePreviousCycleMarker() {
        // Last month's task still carries last month's launch intent: this month's tap
        // must still open (the marker rejects only the exact target already consumed).
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataStoreUtils.saveInt(context, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 202608)
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, deckIntent(2026, 9))
            .create()
            .get()
        assertEquals(2026 to 9, activity.rewindDeckTarget)
        assertEquals(
            202609,
            DataStoreUtils.getInt(activity, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0)
        )
    }

    @Test
    fun appOff_iconLaunchWithoutRewindExtrasLeavesNoTargetAndNoMarker() {
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, Intent(Intent.ACTION_MAIN))
            .create()
            .get()
        assertNull(activity.rewindDeckTarget)
        assertNull(activity.rewindPlaylistTarget)
        assertEquals(0, DataStoreUtils.getInt(activity, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0))
        assertEquals(
            0L,
            DataStoreUtils.getLong(activity, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 0L)
        )
    }

    // ---- App ON: warm start, onNewIntent consumes the fresh intent ----

    @Test
    fun appOn_warmStartOnNewIntentAcceptsTheDeckTargetRecordsTheMarkerAndStripsTheExtras() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(deckIntent(2026, 9))
        val activity = controller.get()
        assertEquals(2026 to 9, activity.rewindDeckTarget)
        assertEquals(
            202609,
            DataStoreUtils.getInt(activity, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0)
        )
        assertFalse(activity.intent.hasExtra(RewindReminderWorker.EXTRA_DECK_YEAR))
    }

    @Test
    fun appOn_warmStartWithAnAlreadyConsumedDeckTargetIsRejected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataStoreUtils.saveInt(context, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 202609)
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(deckIntent(2026, 9))
        val activity = controller.get()
        assertNull(activity.rewindDeckTarget)
        assertFalse(activity.intent.hasExtra(RewindReminderWorker.EXTRA_DECK_YEAR))
    }

    // ---- App OFF / ON: playlist deep link ----

    @Test
    fun appOff_coldStartWithPlaylistDeepLinkAcceptsThePlaylistTargetRecordsTheMarkerAndStripsTheExtras() {
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, playlistIntent(42L))
            .create()
            .get()
        assertEquals(42L, activity.rewindPlaylistTarget)
        assertEquals(
            42L,
            DataStoreUtils.getLong(activity, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 0L)
        )
        assertFalse(activity.intent.hasExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID))
    }

    @Test
    fun appOff_coldStartWithAnAlreadyOpenedPlaylistIsRejected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataStoreUtils.saveLong(context, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 42L)
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, playlistIntent(42L))
            .create()
            .get()
        assertNull(activity.rewindPlaylistTarget)
        assertFalse(activity.intent.hasExtra(RewindPlaylists.EXTRA_REWIND_PLAYLIST_ID))
    }

    @Test
    fun appOn_warmStartOnNewIntentAcceptsThePlaylistTarget() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(playlistIntent(42L))
        val activity = controller.get()
        assertEquals(42L, activity.rewindPlaylistTarget)
        assertEquals(
            42L,
            DataStoreUtils.getLong(activity, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 0L)
        )
    }

    // ---- Foreign notifications / player open: no rewind extras must not open anything ----
    //
    // The app in the foreground (player open or not) receives foreign notification taps
    // (download finished, listen-together, Android Auto, ...) via onNewIntent with an intent
    // carrying NO rewind extras: the app must simply come to the foreground — no deck, no
    // playlist, no marker written.

    @Test
    fun appOn_warmStartWithAForeignNotificationDoesNotOpenDeckOrPlaylist() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(Intent().setAction(Intent.ACTION_VIEW))
        val activity = controller.get()
        assertNull(activity.rewindDeckTarget)
        assertNull(activity.rewindPlaylistTarget)
        assertEquals(0, DataStoreUtils.getInt(activity, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0))
        assertEquals(
            0L,
            DataStoreUtils.getLong(activity, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 0L)
        )
    }

    @Test
    fun appOn_aForeignNotificationDoesNotClobberAStillPendingDeckTarget() {
        // The clobber guard (`deck ?: rewindDeckTarget`) at the activity level: a
        // parse-null intent must PRESERVE the in-memory target — e.g. a target held while
        // onboarding is up (the navigation effect has not run yet) must survive a foreign
        // notification tap, or the deck would be lost for the original tap.
        val controller = Robolectric
            .buildActivity(MainActivity::class.java, deckIntent(2026, 9))
            .create()
        assertEquals(2026 to 9, controller.get().rewindDeckTarget)
        controller.newIntent(Intent().setAction(Intent.ACTION_VIEW))
        assertEquals(2026 to 9, controller.get().rewindDeckTarget)
    }
}
