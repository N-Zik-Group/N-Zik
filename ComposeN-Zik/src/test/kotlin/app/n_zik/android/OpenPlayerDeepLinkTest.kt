package app.n_zik.android

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract of the consumer side of the "now playing" notification's content intent
 * (spec-notification-click-opens-player — the consumer side is unit-tested here, same
 * convention as [app.n_zik.android.components.ui.screens.rewind.RewindDeckDeepLinkTest]):
 * - [openPlayerTokenFromIntent]: the tap token carried by the "now playing"
 *   notification's content intent ([MainActivity.EXTRA_OPEN_PLAYER_TOKEN], the service's
 *   one-shot System.currentTimeMillis() token, static per service instance) must decode to
 *   the token, so the app deploys the full player. Anything else (missing extra, 0,
 *   negative) yields null, so the app starts without a forced player open (NO_EXTRA).
 *   Staleness is NOT rejected here — the task record re-sends the original launch intent
 *   (extras intact) after a process-death restore, and the token is static per service —
 *   it is rejected at deployment time by a live comparison against the current service's
 *   token (see the cold deploy site in MainActivity).
 *
 * The one-shot contract is layered (same design as the rewind deep links):
 * [consumeOpenPlayerExtras] strips the extra off the intent the activity keeps (singleTask
 * relaunch), so a reparse after consumption yields null and the player does not re-deploy
 * on every app re-foreground.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OpenPlayerDeepLinkTest {

    private fun tapIntentWith(token: Long): Intent =
        Intent().apply { putExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN, token) }

    @Test
    fun validTokenDecodesToItself() {
        assertEquals(1_700_000_000_000L, openPlayerTokenFromIntent(tapIntentWith(1_700_000_000_000L)))
    }

    @Test
    fun nullIntentYieldsNull() {
        assertNull(openPlayerTokenFromIntent(null))
    }

    @Test
    fun missingTokenExtraYieldsNull() {
        // A notification posted before the content intent shipped (or a plain app open)
        // carries no extra: the app starts without a forced player open.
        assertNull(openPlayerTokenFromIntent(Intent()))
    }

    @Test
    fun nonPositiveTokensYieldNull() {
        // 0 is the missing-extra default of getLongExtra; a negative value is malformed.
        assertNull(openPlayerTokenFromIntent(tapIntentWith(0L)))
        assertNull(openPlayerTokenFromIntent(tapIntentWith(-1L)))
    }

    // ---- One-shot consumption (consumeOpenPlayerExtras) ----
    //
    // singleTask re-delivers the activity's current intent on every task relaunch, so the
    // token extra must be consumed off the kept intent: a reparse after consumption must
    // yield null, or the player re-deploys on every app re-foreground.

    @Test
    fun tapIntentReparsesNullAfterConsumption() {
        val intent = tapIntentWith(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, openPlayerTokenFromIntent(intent))
        consumeOpenPlayerExtras(intent)
        assertNull(openPlayerTokenFromIntent(intent))
    }

    @Test
    fun consumptionIsIdempotent() {
        val intent = tapIntentWith(1_700_000_000_000L)
        consumeOpenPlayerExtras(intent)
        consumeOpenPlayerExtras(intent)
        assertNull(openPlayerTokenFromIntent(intent))
    }

    @Test
    fun consumingANullIntentDoesNotThrow() {
        consumeOpenPlayerExtras(null)
    }

    @Test
    fun consumptionRemovesOnlyTheTokenExtra() {
        val intent = tapIntentWith(1_700_000_000_000L).apply { putExtra(Intent.EXTRA_TEXT, "artist - song") }
        consumeOpenPlayerExtras(intent)
        assertFalse(intent.hasExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN))
        assertEquals("artist - song", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun parserRejectedIntentStillHasItsExtraConsumed() {
        // The strip is unconditional (not gated on a successful parse): a rejected
        // (non-positive / malformed) token must not leave its extra alive in the kept
        // intent either, or a later re-delivery would keep it around past the parse guard.
        val intent = tapIntentWith(0L)
        assertNull(openPlayerTokenFromIntent(intent))
        consumeOpenPlayerExtras(intent)
        assertFalse(intent.hasExtra(MainActivity.EXTRA_OPEN_PLAYER_TOKEN))
        assertNull(openPlayerTokenFromIntent(intent))
    }
}
