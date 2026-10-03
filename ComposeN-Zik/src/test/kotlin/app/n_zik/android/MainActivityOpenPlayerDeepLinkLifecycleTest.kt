package app.n_zik.android

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * App OFF / app ON lifecycle contract of the "now playing" notification tap
 * (spec-notification-click-opens-player), tested on the real [MainActivity] under
 * Robolectric (same convention as [MainActivityRewindDeepLinkLifecycleTest]):
 *
 * - app OFF (cold start): [MainActivity.onCreate] consumes the launch intent's token
 *   synchronously — a positive token is held in memory
 *   ([MainActivity.openPlayerColdToken]) and the extra is stripped off the kept intent;
 * - app ON (warm start): [MainActivity.onNewIntent] arms
 *   [MainActivity.openPlayerFromNotificationWarm] and strips the extra — the warm path has
 *   no comparison at all (a warm tap is always a fresh fire of the PendingIntent);
 * - there is NO persistent marker: the token is static for the service's lifetime, so a
 *   stored "last consumed" value would reject a legitimate second cold tap (the activity
 *   is destroyed while the foreground service survives) — a second cold start with the
 *   same token arms the deployment again; staleness (a task-record re-send after a
 *   process death) is instead rejected at deployment time by a live comparison against
 *   the current service's token.
 *
 * robolectric.properties pins a plain Application, so MainApplication is never created:
 * no worker scheduling, cookie fetching or service binding runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityOpenPlayerDeepLinkLifecycleTest {

    private companion object {
        const val TOKEN = 1_700_000_000_000L
    }

    private fun tapIntentWith(token: Long): Intent =
        Intent().apply { putExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN, token) }

    // ---- App OFF: cold start, onCreate consumes the launch intent ----

    @Test
    fun appOff_coldStartWithATapTokenHoldsTheTokenAndStripsTheExtra() {
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, tapIntentWith(TOKEN))
            .create()
            .get()
        assertEquals(TOKEN, activity.openPlayerColdToken)
        assertFalse(activity.openPlayerFromNotificationWarm)
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN))
    }

    @Test
    fun appOff_aSecondColdStartWithTheSameTokenHoldsTheTokenAgain() {
        // The regression the persistent marker caused: the activity is destroyed while the
        // foreground service survives, so a fresh tap delivers the SAME static token — a
        // stored "last consumed" value would have rejected it. There is no store anymore:
        // each activity instance arms from its own launch intent.
        val first = Robolectric
            .buildActivity(MainActivity::class.java, tapIntentWith(TOKEN))
            .create()
            .get()
        assertEquals(TOKEN, first.openPlayerColdToken)
        first.finish()

        val second = Robolectric
            .buildActivity(MainActivity::class.java, tapIntentWith(TOKEN))
            .create()
            .get()
        assertEquals(TOKEN, second.openPlayerColdToken)
        assertFalse(second.intent.hasExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN))
    }

    @Test
    fun appOff_aLaunchWithoutTheTokenArmsNoState() {
        // A plain icon launch (or any notification without the token extra) starts the
        // app without a forced player open.
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, Intent(Intent.ACTION_MAIN))
            .create()
            .get()
        assertNull(activity.openPlayerColdToken)
        assertFalse(activity.openPlayerFromNotificationWarm)
    }

    @Test
    fun appOff_theKeptIntentReDecodesToNullAfterConsumption() {
        // singleTask re-delivers the activity's current intent on every task relaunch: the
        // stripped kept intent must decode to null, or the player re-deploys on every
        // re-foreground.
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, tapIntentWith(TOKEN))
            .create()
            .get()
        assertNull(openPlayerTokenFromIntent(activity.intent))
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN))
    }

    // ---- App ON: warm start, onNewIntent consumes the fresh intent ----

    @Test
    fun appOn_warmStartWithATapTokenArmsTheWarmFlagStripsTheExtraAndTouchesNoColdToken() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(tapIntentWith(TOKEN))
        val activity = controller.get()
        assertTrue(activity.openPlayerFromNotificationWarm)
        assertNull(activity.openPlayerColdToken)
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN))
    }

    @Test
    fun appOn_aWarmTapAfterAColdTapWithTheSameTokenIsStillAccepted() {
        // The marker-era regression: the token is static for the service's lifetime, so a
        // "last consumed" value read on the warm path would have rejected a legitimate
        // second tap. The warm path has no comparison at all — it always arms.
        val controller = Robolectric
            .buildActivity(MainActivity::class.java, tapIntentWith(TOKEN))
            .create()
        assertEquals(TOKEN, controller.get().openPlayerColdToken)
        controller.newIntent(tapIntentWith(TOKEN))
        val activity = controller.get()
        assertTrue(activity.openPlayerFromNotificationWarm)
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN))
    }

    @Test
    fun appOn_aForeignNotificationArmsNoState() {
        // Foreign notification taps (download finished, Android Auto, ...) arrive via
        // onNewIntent with no token extra: the app simply comes to the foreground — no
        // warm deployment armed.
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(Intent().setAction(Intent.ACTION_VIEW))
        val activity = controller.get()
        assertNull(activity.openPlayerColdToken)
        assertFalse(activity.openPlayerFromNotificationWarm)
    }
}
