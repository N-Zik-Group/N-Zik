package app.n_zik.android.legacyoffmain.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.enums.NotificationButtons
import app.n_zik.android.R
import app.n_zik.android.components.discoverButtonIconRes
import app.n_zik.android.components.radioButtonIconRes
import app.n_zik.android.components.shuffleButtonIconRes
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.discoverCommandButtonIconRes
import app.n_zik.android.playback.services.radioCommandButtonIconRes
import app.n_zik.android.playback.services.radioCommandButtonLabelRes
import app.n_zik.android.playback.services.shuffleOkFlashIconRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #866 (gh-866) — after migrating `PlayerServiceModern.updateDefaultNotification()` from the
 * deprecated `setCustomLayout` to `setMediaButtonPreferences`, every icon published in the Android
 * Auto overflow + the media notification is resolved through [NotificationButtons.getStateIcon]
 * inside `buildCustomCommandButtons()`. This test pins that state → drawable mapping
 * (shuffle on/off, repeat off/one/all, like liked/none/disliked, download idle/queued/downloading/
 * completed, static radio/search) and the button → sessionCommand mapping the AA controllers
 * receive from `AutoSessionCallback.onConnect` — proof the icons AA receives are the state icons,
 * not static ones. It also pins the state → label/icon mapping of the radio command button,
 * resolved by the pure `radioCommandButtonLabelRes` / `radioCommandButtonIconRes` functions
 * (gh-866 radio state, beyond the reference apps).
 *
 * Robolectric (same pattern as the automotive package tests): `AutoSessionConstants` builds its
 * [androidx.media3.session.SessionCommand] vals with `Bundle.EMPTY`, which plain JVM stubs reject.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationButtonsStateIconOffMainTest {

    private fun icon(
        button: NotificationButtons,
        likedState: Long? = null,
        // "No download in progress" state — PlayerServiceModern.currentSongStateDownload
        // defaults to / falls back to STATE_STOPPED.
        downloadState: Int = Download.STATE_STOPPED,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        shuffleMode: Boolean = false
    ): Int = button.getStateIcon(button, likedState, downloadState, repeatMode, shuffleMode)

    @Test
    fun `shuffle icon follows shuffle state`() {
        assertEquals(R.drawable.shuffle, icon(NotificationButtons.Shuffle, shuffleMode = false))
        assertEquals(R.drawable.shuffle_filled, icon(NotificationButtons.Shuffle, shuffleMode = true))
    }

    @Test
    fun `repeat icon follows repeat mode`() {
        assertEquals(R.drawable.repeat, icon(NotificationButtons.Repeat, repeatMode = Player.REPEAT_MODE_OFF))
        assertEquals(R.drawable.repeatone, icon(NotificationButtons.Repeat, repeatMode = Player.REPEAT_MODE_ONE))
        assertEquals(R.drawable.infinite, icon(NotificationButtons.Repeat, repeatMode = Player.REPEAT_MODE_ALL))
    }

    @Test
    fun `like icon follows liked state`() {
        assertEquals(R.drawable.heart_outline, icon(NotificationButtons.Favorites, likedState = null))
        assertEquals(R.drawable.heart_dislike, icon(NotificationButtons.Favorites, likedState = -1L))
        assertEquals(R.drawable.heart, icon(NotificationButtons.Favorites, likedState = 1_735_689_600_000L))
    }

    @Test
    fun `download icon follows download state`() {
        assertEquals(R.drawable.download, icon(NotificationButtons.Download, downloadState = Download.STATE_STOPPED))
        assertEquals(R.drawable.download_progress, icon(NotificationButtons.Download, downloadState = Download.STATE_QUEUED))
        assertEquals(R.drawable.download_progress, icon(NotificationButtons.Download, downloadState = Download.STATE_DOWNLOADING))
        assertEquals(R.drawable.downloaded, icon(NotificationButtons.Download, downloadState = Download.STATE_COMPLETED))
    }

    @Test
    fun `radio and search icons are static`() {
        // The radio state icon is static by design in the legacy enum — the active-state
        // override (radio_stop) is applied by PlayerServiceModern.commandButtonIconRes.
        assertEquals(R.drawable.radio, icon(NotificationButtons.Radio))
        assertEquals(R.drawable.search, icon(NotificationButtons.Search))
    }

    @Test
    fun `shuffle command button icon shows confirmation flash then state icon`() {
        // gh-866: while the transient "shuffle registered" flash is active the published icon is
        // shuffle_ok; once it ends the state icon (shuffle / shuffle_filled) is restored.
        assertEquals(R.drawable.shuffle_ok, shuffleOkFlashIconRes(flashActive = true, stateIconRes = R.drawable.shuffle))
        assertEquals(R.drawable.shuffle, shuffleOkFlashIconRes(flashActive = false, stateIconRes = R.drawable.shuffle))
        assertEquals(R.drawable.shuffle_filled, shuffleOkFlashIconRes(flashActive = false, stateIconRes = R.drawable.shuffle_filled))
    }

    @Test
    fun `radio command button label follows radio state`() {
        // gh-866: the radio button's display name flips start_radio → stop_radio.
        assertEquals(R.string.start_radio, radioCommandButtonLabelRes(isRadioActive = false))
        assertEquals(R.string.stop_radio, radioCommandButtonLabelRes(isRadioActive = true))
    }

    @Test
    fun `radio command button icon follows radio state`() {
        // gh-866: while the radio is active the published icon is radio_stop, otherwise the
        // inactive state icon (R.drawable.radio, resolved by the legacy enum).
        assertEquals(R.drawable.radio, radioCommandButtonIconRes(R.drawable.radio, isRadioActive = false))
        assertEquals(R.drawable.radio_stop, radioCommandButtonIconRes(R.drawable.radio, isRadioActive = true))
    }

    @Test
    fun `discover command button icon follows discover state`() {
        // gh-866: while the discover filter is active the published icon is discover_stop,
        // otherwise the inactive state icon (R.drawable.discover, resolved by the legacy enum) —
        // same treatment as the radio command button.
        assertEquals(R.drawable.discover, discoverCommandButtonIconRes(R.drawable.discover, isDiscoverEnabled = false))
        assertEquals(R.drawable.discover_stop, discoverCommandButtonIconRes(R.drawable.discover, isDiscoverEnabled = true))
    }

    @Test
    fun `in-app shuffle button icon shows flash then base icon`() {
        // gh-866: every in-app shuffle button shows shuffle_ok while the transient app-wide
        // flash is active, then the base shuffle icon.
        assertEquals(R.drawable.shuffle_ok, shuffleButtonIconRes(flashActive = true))
        assertEquals(R.drawable.shuffle, shuffleButtonIconRes(flashActive = false))
    }

    @Test
    fun `in-app radio button icon follows radio state`() {
        // gh-866: every in-app radio button shows radio_stop while the radio is active, radio
        // while inactive — the state change is the press feedback (no ok badge: at button size
        // its check read as an "X" = disabled, user feedback 2026-09-30).
        assertEquals(R.drawable.radio, radioButtonIconRes(isRadioActive = false))
        assertEquals(R.drawable.radio_stop, radioButtonIconRes(isRadioActive = true))
    }

    @Test
    fun `in-app discover button icon follows discover state`() {
        // gh-866: every in-app discover button shows discover_stop while discover mode is ON,
        // discover while OFF — the state change is the press feedback (same treatment as radio).
        assertEquals(R.drawable.discover, discoverButtonIconRes(isDiscoverEnabled = false))
        assertEquals(R.drawable.discover_stop, discoverButtonIconRes(isDiscoverEnabled = true))
    }

    @Test
    fun `buttons map to their session commands`() {
        assertEquals(AutoSessionConstants.CommandToggleDownload, NotificationButtons.Download.sessionCommand)
        assertEquals(AutoSessionConstants.CommandToggleLike, NotificationButtons.Favorites.sessionCommand)
        assertEquals(AutoSessionConstants.CommandToggleRepeatMode, NotificationButtons.Repeat.sessionCommand)
        assertEquals(AutoSessionConstants.CommandToggleShuffle, NotificationButtons.Shuffle.sessionCommand)
        assertEquals(AutoSessionConstants.CommandStartRadio, NotificationButtons.Radio.sessionCommand)
        assertEquals(AutoSessionConstants.CommandToggleDiscover, NotificationButtons.Discover.sessionCommand)
        assertEquals(AutoSessionConstants.CommandSearch, NotificationButtons.Search.sessionCommand)
    }

    @Test
    fun `state icons are distinct per state`() {
        // Guards the whole point of the migration: if two states shared one drawable, the AA
        // overflow would look identical regardless of state.
        assertNotEquals(R.drawable.shuffle, R.drawable.shuffle_filled)
        assertNotEquals(R.drawable.repeat, R.drawable.repeatone)
        assertNotEquals(R.drawable.repeat, R.drawable.infinite)
        assertNotEquals(R.drawable.repeatone, R.drawable.infinite)
        assertNotEquals(R.drawable.heart_outline, R.drawable.heart)
        assertNotEquals(R.drawable.heart, R.drawable.heart_dislike)
        assertNotEquals(R.drawable.download, R.drawable.download_progress)
        assertNotEquals(R.drawable.download_progress, R.drawable.downloaded)
        assertNotEquals(R.drawable.radio, R.drawable.radio_stop)
        assertNotEquals(R.drawable.discover, R.drawable.discover_stop)
    }
}
